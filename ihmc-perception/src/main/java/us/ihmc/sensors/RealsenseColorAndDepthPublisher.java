package us.ihmc.sensors;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import perception_msgs.msg.dds.ImageMessage;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.nio.FileTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.IHMCROS2Callback;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.property.ROS2StoredPropertySetGroup;
import us.ihmc.communication.ros2.ROS2Helper;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.log.LogTools;
import us.ihmc.perception.BytedecoOpenCVTools;
import us.ihmc.perception.BytedecoTools;
import us.ihmc.perception.MutableBytePointer;
import us.ihmc.perception.comms.PerceptionComms;
import us.ihmc.perception.logging.PerceptionDataLogger;
import us.ihmc.perception.logging.PerceptionLoggerConstants;
import us.ihmc.perception.parameters.PerceptionConfigurationParameters;
import us.ihmc.perception.realsense.BytedecoRealsense;
import us.ihmc.perception.realsense.RealSenseHardwareManager;
import us.ihmc.perception.tools.PerceptionMessageTools;
import us.ihmc.pubsub.DomainFactory;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.tools.IHMCCommonPaths;
import us.ihmc.tools.UnitConversions;
import us.ihmc.tools.thread.Activator;
import us.ihmc.tools.thread.Throttler;

import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.bytedeco.opencv.global.opencv_highgui.imshow;
import static org.bytedeco.opencv.global.opencv_highgui.waitKey;

/**
 * Publishes color and depth from Realsense D435
 * ----+ L515 Device Configuration: Serial Number: F0245563, Depth Height 768, Depth Width: 1024, Depth FPS: 15, Color Height 720, Color Width: 1280, Color FPS:
 * 15
 * ----+ D435: Serial Number: 752112070330, Depth Width: 848, Depth Height: 480, Depth FPS: 30, Color Width: 848, Color Height: 480, Color FPS: 30
 *
 * Use this to retrieve files from ihmc-mini-2
 * rsync -aP ihmc-mini-2:/home/ihmc/.ihmc/logs/perception/20230226_172530_PerceptionLog.hdf5 /home/robotlab/.ihmc/logs/perception/
 *
 */
public class RealsenseColorAndDepthPublisher
{
   private final Activator nativesLoadedActivator;
   private final ROS2Helper ros2Helper;
   private final Supplier<ReferenceFrame> sensorFrameUpdater;
   private final FramePose3D cameraPose = new FramePose3D();

   private final Point3D cameraPosition = new Point3D();
   private final Quaternion cameraQuaternion = new Quaternion();

   private final ROS2Topic<ImageMessage> colorTopic;
   private final ROS2Topic<ImageMessage> depthTopic;
   private final ImageMessage colorImageMessage = new ImageMessage();
   private final ImageMessage depthImageMessage = new ImageMessage();
   private final Mat yuvColorImage = new Mat();
   private final Throttler throttler = new Throttler();

   private PerceptionConfigurationParameters parameters = new PerceptionConfigurationParameters();
   private ROS2StoredPropertySetGroup ros2PropertySetGroup;
   private final PerceptionDataLogger perceptionDataLogger = new PerceptionDataLogger();
   private boolean loggerInitialized = false;
   private boolean previousLoggerEnabledState = false;

   private RealSenseHardwareManager realSenseHardwareManager;
   private BytedecoRealsense sensor;
   private Mat depth16UC1Image;
   private Mat color8UC3Image;

   private volatile boolean running = true;
   private final double outputPeriod;

   private BytePointer compressedColorPointer;
   private BytePointer compressedDepthPointer;

   private final Pose3D mocapPose = new Pose3D();

   private final String serialNumber;
   private int depthHeight;
   private int depthWidth;
   private final int colorHeight;
   private final int colorWidth;
   private final int colorFPS;
   private final int depthFPS;
   private long depthSequenceNumber = 0;
   private long colorSequenceNumber = 0;

   public RealsenseColorAndDepthPublisher(String serialNumber,
                                          int depthWidth,
                                          int depthHeight,
                                          int depthFPS,
                                          int colorWidth,
                                          int colorHeight,
                                          int colorFPS,
                                          ROS2Topic<ImageMessage> depthTopic,
                                          ROS2Topic<ImageMessage> colorTopic,
                                          Supplier<ReferenceFrame> sensorFrameUpdater)
   {
      this.serialNumber = serialNumber;
      this.depthWidth = depthWidth;
      this.depthHeight = depthHeight;
      this.colorWidth = colorWidth;
      this.colorHeight = colorHeight;
      this.depthFPS = depthFPS;
      this.colorFPS = colorFPS;
      this.colorTopic = colorTopic;
      this.depthTopic = depthTopic;
      this.sensorFrameUpdater = sensorFrameUpdater;

      outputPeriod = UnitConversions.hertzToSeconds(15.0);

      nativesLoadedActivator = BytedecoTools.loadOpenCVNativesOnAThread();

      ROS2Node ros2Node = ROS2Tools.createROS2Node(DomainFactory.PubSubImplementation.FAST_RTPS, "realsense_color_depth_node");
      ros2Helper = new ROS2Helper(ros2Node);

      new IHMCROS2Callback<>(ros2Node, ROS2Tools.MOCAP_RIGID_BODY, (message) -> {
            message.get(mocapPose);
      });

      Runtime.getRuntime().addShutdownHook(new Thread(() ->
      {
         ThreadTools.sleepSeconds(0.5);
         destroy();
      }, getClass().getSimpleName() + "Shutdown"));

      while (running)
      {
         update();
         throttler.waitAndRun(outputPeriod); // do the waiting after we send to remove unecessary latency
      }
   }

   private void update()
   {
      if (nativesLoadedActivator.poll())
      {
         if (nativesLoadedActivator.isNewlyActivated())
         {
            realSenseHardwareManager = new RealSenseHardwareManager();
            sensor = realSenseHardwareManager.createBytedecoRealsenseDevice(this.serialNumber, this.depthWidth, this.depthHeight, this.depthFPS);

            if (sensor.getDevice() == null)
            {
               running = false;
               throw new RuntimeException("Device not found. Set -D<model>.serial.number=00000000000");
            }
            sensor.enableColor(this.colorWidth, this.colorHeight, this.colorFPS);
            sensor.initialize();

            depthWidth = sensor.getDepthWidth();
            depthHeight = sensor.getDepthHeight();

            LogTools.info(String.format("Color: [fx:%.4f, fy:%.4f, cx:%.4f, cy:%.4f, h:%d, w:%d]",
                                        sensor.getColorFocalLengthPixelsX(),
                                        sensor.getColorFocalLengthPixelsY(),
                                        sensor.getColorPrincipalOffsetXPixels(),
                                        sensor.getColorPrincipalOffsetYPixels(),
                                        colorHeight,
                                        colorWidth));

            LogTools.info(String.format("Depth: [fx:%.4f, fy:%.4f, cx:%.4f, cy:%.4f, h:%d, w:%d]",
                                        sensor.getDepthFocalLengthPixelsX(),
                                        sensor.getDepthFocalLengthPixelsY(),
                                        sensor.getDepthPrincipalOffsetXPixels(),
                                        sensor.getDepthPrincipalOffsetYPixels(),
                                        depthHeight,
                                        depthWidth));

            ros2PropertySetGroup = new ROS2StoredPropertySetGroup(ros2Helper);
            ros2PropertySetGroup.registerStoredPropertySet(PerceptionComms.PERCEPTION_CONFIGURATION_PARAMETERS, parameters);
         }

         if (sensor.readFrameData())
         {
            sensor.updateDataBytePointers();

            Instant now = Instant.now();

            MutableBytePointer depthFrameData = sensor.getDepthFrameData();
            depth16UC1Image = new Mat(depthHeight, depthWidth, opencv_core.CV_16UC1, depthFrameData);
            PerceptionMessageTools.setDepthIntrinsicsFromRealsense(sensor, depthImageMessage.getIntrinsicParameters());

            MutableBytePointer colorFrameData = sensor.getColorFrameData();
            color8UC3Image = new Mat(this.colorHeight, this.colorWidth, opencv_core.CV_8UC3, colorFrameData);
            PerceptionMessageTools.setColorIntrinsicsFromRealsense(sensor, colorImageMessage.getIntrinsicParameters());

            // Important not to store as a field, as update() needs to be called each frame
            ReferenceFrame cameraFrame = sensorFrameUpdater.get();
            cameraPose.setToZero(cameraFrame);
            cameraPose.changeFrame(ReferenceFrame.getWorldFrame());

            cameraPosition.set(cameraPose.getPosition());
            cameraQuaternion.set(cameraPose.getOrientation());

            compressedDepthPointer = new BytePointer();
            BytedecoOpenCVTools.compressImagePNG(depth16UC1Image, compressedDepthPointer);
            PerceptionMessageTools.publishCompressedDepthImage(compressedDepthPointer, depthTopic, depthImageMessage, ros2Helper, cameraPose, now, depthSequenceNumber++,
                                                               sensor.getDepthHeight(), sensor.getDepthWidth());


            PerceptionMessageTools.publishJPGCompressedColorImage(color8UC3Image, yuvColorImage, colorTopic, colorImageMessage, ros2Helper,  cameraPose,
                                                                  now, colorSequenceNumber++,  colorHeight, colorWidth);

            if(parameters.getLoggingEnabled())
            {
               if(!loggerInitialized)
               {
                  SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
                  String logFileName = dateFormat.format(new Date()) + "_" + "PerceptionLog.hdf5";
                  FileTools.ensureDirectoryExists(Paths.get(IHMCCommonPaths.PERCEPTION_LOGS_DIRECTORY_NAME), DefaultExceptionHandler.MESSAGE_AND_STACKTRACE);

                  perceptionDataLogger.openLogFile(IHMCCommonPaths.PERCEPTION_LOGS_DIRECTORY.resolve(logFileName).toString());
                  perceptionDataLogger.addChannel(PerceptionLoggerConstants.L515_DEPTH_NAME);
                  perceptionDataLogger.setChannelEnabled(PerceptionLoggerConstants.L515_DEPTH_NAME, true);

                  loggerInitialized = true;
               }

               long timestamp = Conversions.secondsToNanoseconds(now.getEpochSecond()) + now.getNano();

               perceptionDataLogger.storeLongArray(PerceptionLoggerConstants.L515_SENSOR_TIME, timestamp);
               perceptionDataLogger.storeBytesFromPointer(PerceptionLoggerConstants.L515_DEPTH_NAME, compressedDepthPointer);

               perceptionDataLogger.storeFloatArray(PerceptionLoggerConstants.L515_SENSOR_POSITION, cameraPosition);
               perceptionDataLogger.storeFloatArray(PerceptionLoggerConstants.L515_SENSOR_ORIENTATION, cameraPosition);

               perceptionDataLogger.storeFloatArray(PerceptionLoggerConstants.MOCAP_RIGID_BODY_POSITION, mocapPose.getPosition());
               perceptionDataLogger.storeFloatArray(PerceptionLoggerConstants.MOCAP_RIGID_BODY_ORIENTATION, mocapPose.getOrientation());

               previousLoggerEnabledState = true;
            }
            else {
               if(previousLoggerEnabledState)
               {
                  perceptionDataLogger.closeLogFile();
                  previousLoggerEnabledState = false;
                  loggerInitialized = false;
               }
            }

            ros2PropertySetGroup.update();

         }
      }
   }

   private void destroy()
   {
      running = false;
      sensor.deleteDevice();
      realSenseHardwareManager.deleteContext();
      perceptionDataLogger.closeLogFile();
   }

   public static void main(String[] args)
   {
      /*
         Color: [fx:901.3026, fy:901.8400, cx:635.2337, cy:350.9427, h:720, w:1280]
         Depth: [fx:730.7891, fy:731.0859, cx:528.6094, cy:408.1602, h:768, w:1024]
      */

      // L515: [F1121365, F0245563], D455: [215122254074]
      String l515SerialNumber = System.getProperty("l515.serial.number", "F1121365");
      new RealsenseColorAndDepthPublisher(l515SerialNumber,
                                          1024,
                                          768,
                                          30,
                                          1280,
                                          720,
                                          30,
                                          ROS2Tools.L515_DEPTH_IMAGE,
                                          ROS2Tools.L515_COLOR_IMAGE,
                                          ReferenceFrame::getWorldFrame);
   }
}