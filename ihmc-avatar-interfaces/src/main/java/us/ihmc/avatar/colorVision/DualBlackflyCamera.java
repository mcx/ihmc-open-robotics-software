package us.ihmc.avatar.colorVision;

import boofcv.struct.calib.CameraPinholeBrown;
import controller_msgs.msg.dds.BigVideoPacket;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.spinnaker.Spinnaker_C.spinImage;
import org.bytedeco.spinnaker.global.Spinnaker_C;
import std_msgs.msg.dds.Float64;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.ros2.ROS2Helper;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.log.LogTools;
import us.ihmc.perception.BytedecoImage;
import us.ihmc.perception.OpenCVArUcoMarkerDetection;
import us.ihmc.perception.spinnaker.SpinnakerBlackfly;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.ROS2QosProfile;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.RealtimeROS2Node;
import us.ihmc.tools.time.FrequencyCalculator;

import java.time.Instant;

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
   private Mat cameraMatrix;
   private Mat distortionCoefficients;
   private CameraPinholeBrown cameraPinholeBrown;
   private Mat undistortedImageMat;
   private BytedecoImage undistortedImage;
   private BytePointer jpegImageBytePointer;
   private Mat yuv420Image;
   private final BigVideoPacket videoPacket = new BigVideoPacket();
   private IntPointer compressionParameters;
   private final Stopwatch getNextImageDuration = new Stopwatch();
   private final Stopwatch convertColorDuration = new Stopwatch();
   private final Stopwatch encodingDuration = new Stopwatch();
   private final Stopwatch copyDuration = new Stopwatch();
   private OpenCVArUcoMarkerDetection arUcoMarkerDetection;

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
      Instant now = Instant.now();
      getNextImageDuration.start();
      if (blackfly.getNextImage(spinImage))
      {
         getNextImageDuration.suspend();

         if (ros2VideoPublisher == null)
         {
            imageWidth = blackfly.getWidth(spinImage);
            imageHeight = blackfly.getHeight(spinImage);
            LogTools.info("Blackfly {} resolution detected: {}x{}", serialNumber, imageWidth, imageHeight);
            numberOfBytesInFrame = imageWidth * imageHeight * 4;
            spinImageDataPointer = new BytePointer((long) numberOfBytesInFrame);

            blackflySourceImage = new BytedecoImage(imageWidth, imageHeight, opencv_core.CV_8UC3);

            // From OpenCV calibrateCamera with Blackfly serial number 17372478 with FE185C086HA-1 fisheye lens
            // Procedure conducted by Bhavyansh Mishra on 12/14/2021
            cameraPinholeBrown = new CameraPinholeBrown();
            cameraPinholeBrown.setFx(499.3716197917922);
            cameraPinholeBrown.setFy(506.42956667285574);
            cameraPinholeBrown.setCx(1043.8826790137316);
            cameraPinholeBrown.setCy(572.2558510618412);

            cameraMatrix = new Mat(cameraPinholeBrown.getFx(), 0.0, cameraPinholeBrown.getCx(),
                                   0.0, cameraPinholeBrown.getFy(), cameraPinholeBrown.getCy(),
                                   0.0, 0.0, 1.0);
            cameraMatrix.reshape(1, 3);
            distortionCoefficients = new Mat(-0.1304880574839372, 0.0343337720836711, 0, 0, 0.002347490605947351,
                                             0.163868408051474, -0.02493286434834704, 0.01394671162254435);
            distortionCoefficients.reshape(1, 8);
            undistortedImageMat = new Mat();

            yuv420Image = new Mat();

            jpegImageBytePointer = new BytePointer();
            compressionParameters = new IntPointer(opencv_imgcodecs.IMWRITE_JPEG_QUALITY, 75);

            ROS2Topic<BigVideoPacket> videoTopic = ROS2Tools.BLACKFLY_VIDEO.get(side);
            LogTools.info("Publishing ROS 2 color video: {}", videoTopic);
            ros2VideoPublisher = ROS2Tools.createPublisher(realtimeROS2Node, videoTopic, ROS2QosProfile.BEST_EFFORT());
         }
         else // We don't want to publish until the node is spinning which will be next time
         {
            long acquisitionTime = System.nanoTime();
            blackfly.setBytedecoPointerToSpinImageData(spinImage, spinImageDataPointer);
            blackflySourceImage.rewind();
            blackflySourceImage.changeAddress(spinImageDataPointer.address());

            convertColorDuration.start();
            opencv_imgproc.cvtColor(blackflySourceImage.getBytedecoOpenCVMat(), yuv420Image, opencv_imgproc.COLOR_RGB2YUV_I420);
            convertColorDuration.suspend();

            encodingDuration.start();
            opencv_imgcodecs.imencode(".jpg", yuv420Image, jpegImageBytePointer, compressionParameters);
            encodingDuration.suspend();

            copyDuration.start();
            byte[] heapByteArrayData = new byte[jpegImageBytePointer.asBuffer().remaining()];
            jpegImageBytePointer.asBuffer().get(heapByteArrayData);
            videoPacket.getData().resetQuick();
            videoPacket.getData().add(heapByteArrayData);
            copyDuration.suspend();
            videoPacket.setAcquisitionTimeSecondsSinceEpoch(now.getEpochSecond());
            videoPacket.setAcquisitionTimeAdditionalNanos(now.getNano());
            ros2VideoPublisher.publish(videoPacket);

            imagePublishRateCalculator.ping();

            if (side == RobotSide.RIGHT)
            {
               opencv_calib3d.undistort(blackflySourceImage.getBytedecoOpenCVMat(), undistortedImageMat, cameraMatrix, distortionCoefficients);

               if (undistortedImage == null)
               {
                  undistortedImage = new BytedecoImage(undistortedImageMat);

                  arUcoMarkerDetection = new OpenCVArUcoMarkerDetection();
                  arUcoMarkerDetection.create(undistortedImage, cameraPinholeBrown, ReferenceFrame.getWorldFrame());
               }

            }
         }
      }
      Spinnaker_C.spinImageRelease(spinImage);

      sendStatisticMessage(getNextImageDuration, DualBlackflyComms.GET_IMAGE_DURATION);
      sendStatisticMessage(convertColorDuration, DualBlackflyComms.CONVERT_COLOR_DURATION);
      sendStatisticMessage(encodingDuration, DualBlackflyComms.ENCODING_DURATION);
      sendStatisticMessage(copyDuration, DualBlackflyComms.COPY_DURATION);
      sendStatisticMessage(imagePublishRateCalculator.getFrequency(), DualBlackflyComms.PUBLISH_RATE.get(side));
   }

   private void sendStatisticMessage(Stopwatch stopwatch, ROS2Topic<Float64> topic)
   {
      double elapsed = stopwatch.totalElapsed();
      if (!Double.isNaN(elapsed))
      {
         sendStatisticMessage(elapsed, topic);
      }
   }

   private void sendStatisticMessage(double value, ROS2Topic<Float64> topic)
   {
      Float64 float64Message = new Float64();
      float64Message.setData(value);
      ros2Helper.publish(topic, float64Message);
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
