package us.ihmc.avatar.colorVision;

import controller_msgs.msg.dds.BigVideoPacket;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.spinnaker.Spinnaker_C.spinImage;
import org.bytedeco.spinnaker.global.Spinnaker_C;
import std_msgs.msg.dds.Float64;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.ros2.ROS2Helper;
import us.ihmc.log.LogTools;
import us.ihmc.perception.BytedecoImage;
import us.ihmc.perception.spinnaker.SpinnakerBlackfly;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.ROS2QosProfile;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.RealtimeROS2Node;
import us.ihmc.tools.time.FrequencyCalculator;

public class DualBlackflyCamera
{
   private String serialNumber;
   private SpinnakerBlackfly blackfly;
   private final spinImage spinImage = new spinImage();
   private BytePointer spinImageDataPointer;
   private RobotSide side;
   private ROS2Helper ros2Helper;
   private RealtimeROS2Node realtimeROS2Node;
   private IHMCRealtimeROS2Publisher<BigVideoPacket> ros2VideoPublisher;
   private int numberOfBytesInFrame;
   private int imageWidth;
   private int imageHeight;
   private final FrequencyCalculator imagePublishRateCalculator = new FrequencyCalculator();
   private BytedecoImage blackflySourceImage;
   private BytePointer jpegImageBytePointer;
   private Mat yuv420Image;
   private final BigVideoPacket videoPacket = new BigVideoPacket();
   private IntPointer compressionParameters;

   public DualBlackflyCamera(String serialNumber)
   {
      this.serialNumber = serialNumber;
   }

   public void create(SpinnakerBlackfly blackfly, RobotSide side, ROS2Helper ros2Helper, RealtimeROS2Node realtimeROS2Node)
   {
      this.blackfly = blackfly;
      this.side = side;
      this.ros2Helper = ros2Helper;
      this.realtimeROS2Node = realtimeROS2Node;

      blackfly.setAcquisitionMode(Spinnaker_C.spinAcquisitionModeEnums.AcquisitionMode_Continuous);
      blackfly.setPixelFormat(Spinnaker_C.spinPixelFormatEnums.PixelFormat_RGB8);
      blackfly.startAcquiringImages();
   }

   public void update()
   {
      if (blackfly.getNextImage(spinImage))
      {
         if (ros2VideoPublisher == null)
         {
            imageWidth = blackfly.getWidth(spinImage);
            imageHeight = blackfly.getHeight(spinImage);
            LogTools.info("Blackfly {} resolution detected: {}x{}", serialNumber, imageWidth, imageHeight);
            numberOfBytesInFrame = imageWidth * imageHeight * 4;
            spinImageDataPointer = new BytePointer((long) numberOfBytesInFrame);

            blackflySourceImage = new BytedecoImage(imageWidth, imageHeight, opencv_core.CV_8UC3);
            yuv420Image = new Mat();
            jpegImageBytePointer = new BytePointer();
            compressionParameters = new IntPointer(opencv_imgcodecs.IMWRITE_JPEG_QUALITY, 75);

            ROS2Topic<BigVideoPacket> videoTopic = ROS2Tools.BLACKFLY_VIDEO.get(side);
            LogTools.info("Publishing ROS 2 color video: {}", videoTopic);
            ros2VideoPublisher = ROS2Tools.createPublisher(realtimeROS2Node, videoTopic, ROS2QosProfile.BEST_EFFORT());
         }

         if (ros2VideoPublisher != null)
         {
            long acquisitionTime = System.nanoTime();
            blackfly.setBytedecoPointerToSpinImageData(spinImage, spinImageDataPointer);
            blackflySourceImage.rewind();
            blackflySourceImage.changeAddress(spinImageDataPointer.address());

            opencv_imgproc.cvtColor(blackflySourceImage.getBytedecoOpenCVMat(), yuv420Image, opencv_imgproc.COLOR_BGR2YUV_I420);
            opencv_imgcodecs.imencode(".jpg", yuv420Image, jpegImageBytePointer, compressionParameters);

            byte[] heapByteArrayData = new byte[jpegImageBytePointer.asBuffer().remaining()];
            jpegImageBytePointer.asBuffer().get(heapByteArrayData);
            videoPacket.getData().resetQuick();
            videoPacket.getData().add(heapByteArrayData);
            videoPacket.setAcquisitionTimeNanos(acquisitionTime);
            ros2VideoPublisher.publish(videoPacket);

            imagePublishRateCalculator.ping();
         }
      }

      Float64 float64Message = new Float64();
      float64Message.setData(imagePublishRateCalculator.getFrequency());
      ros2Helper.publish(DualBlackflyComms.PUBLISH_RATE.get(side), float64Message);
   }

   public void destroy()
   {
      Spinnaker_C.spinImageRelease(spinImage);
   }

   public String getSerialNumber()
   {
      return serialNumber;
   }

   public IHMCRealtimeROS2Publisher<BigVideoPacket> getRos2VideoPublisher()
   {
      return ros2VideoPublisher;
   }
}
