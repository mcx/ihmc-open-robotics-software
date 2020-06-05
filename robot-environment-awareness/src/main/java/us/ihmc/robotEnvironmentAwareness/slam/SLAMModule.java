package us.ihmc.robotEnvironmentAwareness.slam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import javafx.scene.paint.Color;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.jOctoMap.normalEstimation.NormalEstimationParameters;
import us.ihmc.jOctoMap.ocTree.NormalOcTree;
import us.ihmc.messager.Messager;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.pubsub.subscriber.Subscriber;
import us.ihmc.robotEnvironmentAwareness.communication.KryoMessager;
import us.ihmc.robotEnvironmentAwareness.communication.REACommunicationProperties;
import us.ihmc.robotEnvironmentAwareness.communication.REAModuleAPI;
import us.ihmc.robotEnvironmentAwareness.communication.SLAMModuleAPI;
import us.ihmc.robotEnvironmentAwareness.communication.converters.BoundingBoxMessageConverter;
import us.ihmc.robotEnvironmentAwareness.communication.converters.OcTreeMessageConverter;
import us.ihmc.robotEnvironmentAwareness.communication.converters.PointCloudCompression;
import us.ihmc.robotEnvironmentAwareness.communication.packets.NormalOcTreeMessage;
import us.ihmc.robotEnvironmentAwareness.io.FilePropertyHelper;
import us.ihmc.robotEnvironmentAwareness.tools.ExecutorServiceTools;
import us.ihmc.robotEnvironmentAwareness.tools.ExecutorServiceTools.ExceptionHandling;
import us.ihmc.robotEnvironmentAwareness.ui.graphicsBuilders.StereoVisionPointCloudViewer;
import us.ihmc.robotEnvironmentAwareness.updaters.OcTreeConsumer;
import us.ihmc.ros2.Ros2Node;

public class SLAMModule
{
   protected final Messager reaMessager;

   private static final double DEFAULT_OCTREE_RESOLUTION = 0.02;

   private static final Color LATEST_ORIGINAL_POINT_CLOUD_COLOR = Color.BEIGE;
   private static final Color SOURCE_POINT_CLOUD_COLOR = Color.BLACK;
   private static final Color LATEST_POINT_CLOUD_COLOR = Color.LIME;

   protected final AtomicReference<Boolean> enable;

   private final AtomicReference<StereoVisionPointCloudMessage> newPointCloud = new AtomicReference<>(null);
<<<<<<< HEAD
   protected final LinkedList<StereoVisionPointCloudMessage> pointCloudQueue = new LinkedList<>();

<<<<<<< HEAD
   private final AtomicReference<RandomICPSLAMParameters> slamParameters;
   private final AtomicReference<NormalEstimationParameters> normalEstimationParameters;
   private final AtomicReference<Boolean> enableNormalEstimation;
   private final AtomicReference<Boolean> clearNormals;
=======
   //private final RandomICPSLAM slam = new RandomICPSLAM(DEFAULT_OCTREE_RESOLUTION);
=======
   private final LinkedList<StereoVisionPointCloudMessage> pointCloudQueue = new LinkedList<StereoVisionPointCloudMessage>();
   private final LinkedList<Boolean> stationaryFlagQueue = new LinkedList<Boolean>();
   private final LinkedList<Boolean> reasonableVelocityFlagQueue = new LinkedList<Boolean>();

>>>>>>> 50756f09dce... Fixed number of thread.
   private final SurfaceElementICPSLAM slam = new SurfaceElementICPSLAM(DEFAULT_OCTREE_RESOLUTION);
>>>>>>> 9c5dd62fd96... Before test on Atlas.

   protected final RandomICPSLAM slam = new RandomICPSLAM(DEFAULT_OCTREE_RESOLUTION);

   private ScheduledExecutorService executorService = ExecutorServiceTools.newScheduledThreadPool(2, getClass(), ExceptionHandling.CATCH_AND_REPORT);
   private static final int THREAD_PERIOD_MILLISECONDS = 1;
   private ScheduledFuture<?> scheduledMain;
   private ScheduledFuture<?> scheduledSLAM;

   protected final Ros2Node ros2Node = ROS2Tools.createRos2Node(PubSubImplementation.FAST_RTPS, ROS2Tools.REA_NODE_NAME);

   private final List<OcTreeConsumer> ocTreeConsumers = new ArrayList<>();

   public SLAMModule(Messager messager)
   {
      this(messager, null);
   }

   public SLAMModule(Messager messager, File configurationFile)
   {
      this.reaMessager = messager;

      // TODO: Check name space and fix. Suspected atlas sensor suite and publisher.
      ROS2Tools.createCallbackSubscription(ros2Node, StereoVisionPointCloudMessage.class, "/ihmc/stereo_vision_point_cloud", this::handlePointCloud);
      ROS2Tools.createCallbackSubscription(ros2Node, StereoVisionPointCloudMessage.class, "/ihmc/stereo_vision_point_cloud_D435", this::handlePointCloud);

      reaMessager.submitMessage(SLAMModuleAPI.UISensorPoseHistoryFrames, 1000);

      enable = reaMessager.createInput(SLAMModuleAPI.SLAMEnable, true);

      slamParameters = reaMessager.createInput(SLAMModuleAPI.SLAMParameters, new RandomICPSLAMParameters());

      enableNormalEstimation = reaMessager.createInput(SLAMModuleAPI.NormalEstimationEnable, true);
      clearNormals = reaMessager.createInput(SLAMModuleAPI.NormalEstimationClear, false);
      normalEstimationParameters = reaMessager.createInput(SLAMModuleAPI.NormalEstimationParameters);

      reaMessager.registerTopicListener(SLAMModuleAPI.SLAMClear, (content) -> clearSLAM());

      reaMessager.registerTopicListener(SLAMModuleAPI.RequestEntireModuleState, update -> sendCurrentState());

      NormalEstimationParameters normalEstimationParameters = new NormalEstimationParameters();
      normalEstimationParameters.setNumberOfIterations(7);
      reaMessager.submitMessage(SLAMModuleAPI.NormalEstimationParameters, normalEstimationParameters);

      if (configurationFile != null)
      {
         FilePropertyHelper filePropertyHelper = new FilePropertyHelper(configurationFile);
         loadConfiguration(filePropertyHelper);

         reaMessager.registerTopicListener(SLAMModuleAPI.SaveConfiguration, content -> saveConfiguration(filePropertyHelper));
      }

      sendCurrentState();
   }

   public void attachOcTreeConsumer(OcTreeConsumer ocTreeConsumer)
   {
      this.ocTreeConsumers.add(ocTreeConsumer);
   }

   public void start() throws IOException
   {
      if (scheduledMain == null)
      {
         scheduledMain = executorService.scheduleAtFixedRate(this::updateMain, 0, THREAD_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
      }

      if (scheduledSLAM == null)
      {
         scheduledSLAM = executorService.scheduleAtFixedRate(this::updateSLAM, 0, THREAD_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
      }
   }

   public void stop() throws Exception
   {
      reaMessager.closeMessager();

      if (scheduledMain != null)
      {
         scheduledMain.cancel(true);
         scheduledMain = null;
      }

      if (scheduledSLAM != null)
      {
         scheduledSLAM.cancel(true);
         scheduledSLAM = null;
      }

      if (executorService != null)
      {
         executorService.shutdownNow();
         executorService = null;
      }
   }

   private boolean isMainThreadInterrupted()
   {
      return Thread.interrupted() || scheduledMain == null || scheduledMain.isCancelled();
   }

   private boolean isSLAMThreadInterrupted()
   {
      return Thread.interrupted() || scheduledSLAM == null || scheduledSLAM.isCancelled();
   }

   public void sendCurrentState()
   {
      reaMessager.submitMessage(SLAMModuleAPI.SLAMEnable, enable.get());
      reaMessager.submitMessage(SLAMModuleAPI.SLAMParameters, slamParameters.get());

      reaMessager.submitMessage(SLAMModuleAPI.NormalEstimationEnable, enableNormalEstimation.get());
      reaMessager.submitMessage(SLAMModuleAPI.NormalEstimationParameters, normalEstimationParameters.get());
   }

   public void updateSLAM()
   {
      if (updateSLAMInternal())
      {
         publishResults();
      }
   }

   private boolean updateSLAMInternal()
   {
      if (isSLAMThreadInterrupted())
         return false;

      if (pointCloudQueue.size() == 0)
         return false;

      updateSLAMParameters();
      StereoVisionPointCloudMessage pointCloudToCompute = pointCloudQueue.getFirst();

      boolean success;
      if (slam.isEmpty())
      {
         slam.addKeyFrame(pointCloudToCompute);
         success = true;
      }
      else
      {
         success = addFrame(pointCloudToCompute);
      }

      slam.setNormalEstimationParameters(normalEstimationParameters.get());
      if (clearNormals.getAndSet(false))
         slam.clearNormals();
      if (enableNormalEstimation.get())
         slam.updateOcTree();
      dequeue();

      return success;
   }

   protected boolean addFrame(StereoVisionPointCloudMessage pointCloudToCompute)
   {
      return slam.addFrame(pointCloudToCompute);
   }

   protected void queue(StereoVisionPointCloudMessage pointCloud)
   {
      pointCloudQueue.add(pointCloud);
   }

   protected void dequeue()
   {
      pointCloudQueue.removeFirst();
<<<<<<< HEAD
   }

   protected void publishResults()
   {
      String stringToReport = "";
      reaMessager.submitMessage(SLAMModuleAPI.QueuedBuffers, pointCloudQueue.size() + " [" + slam.getSensorPoses().size() + "]");
      stringToReport = stringToReport + " " + slam.getSensorPoses().size() + " " + slam.getComputationTimeForLatestFrame() + " (sec) ";
=======
      stationaryFlagQueue.removeFirst();
      reasonableVelocityFlagQueue.removeFirst();
      reaMessager.submitMessage(SLAMModuleAPI.QueuedBuffers, pointCloudQueue.size() + " [" + slam.getMapSize() + "]");
      stringToReport = stringToReport + success + " " + slam.getMapSize() + " " + slam.getComputationTimeForLatestFrame() + " (sec) ";
>>>>>>> c12a8c7feaf... Tested surfel icp.
      reaMessager.submitMessage(SLAMModuleAPI.SLAMStatus, stringToReport);

      NormalOcTree octreeMap = slam.getOctree();
      NormalOcTreeMessage octreeMessage = OcTreeMessageConverter.convertToMessage(octreeMap);

      reaMessager.submitMessage(SLAMModuleAPI.SLAMOctreeMapState, octreeMessage);

      SLAMFrame latestFrame = slam.getLatestFrame();
      Point3DReadOnly[] originalPointCloud = latestFrame.getOriginalPointCloud();
      Point3DReadOnly[] correctedPointCloud = latestFrame.getPointCloud();
      Point3DReadOnly[] sourcePointsToWorld = slam.getSourcePointsToWorldLatestFrame();
      if (originalPointCloud == null || sourcePointsToWorld == null || correctedPointCloud == null)
         return;
      StereoVisionPointCloudMessage latestStereoMessage = createLatestFrameStereoVisionPointCloudMessage(originalPointCloud,
                                                                                                         sourcePointsToWorld,
                                                                                                         correctedPointCloud);
      RigidBodyTransformReadOnly sensorPose = latestFrame.getSensorPose();
      latestStereoMessage.getSensorPosition().set(sensorPose.getTranslation());
      latestStereoMessage.getSensorOrientation().set(sensorPose.getRotation());
      reaMessager.submitMessage(SLAMModuleAPI.IhmcSLAMFrameState, latestStereoMessage);
      reaMessager.submitMessage(SLAMModuleAPI.LatestFrameConfidenceFactor, latestFrame.getConfidenceFactor());

      for (OcTreeConsumer ocTreeConsumer : ocTreeConsumers)
      {
<<<<<<< HEAD
         ocTreeConsumer.reportOcTree(octreeMap, slam.getLatestFrame().getSensorPose().getTranslation());
=======
         NormalOcTree octreeMap = slam.getOctree();
         NormalOcTreeMessage octreeMessage = OcTreeMessageConverter.convertToMessage(octreeMap);
         reaMessager.submitMessage(SLAMModuleAPI.SLAMOctreeMapState, octreeMessage);

         slam.updatePlanarRegionsMap();
         PlanarRegionsList planarRegionsMap = slam.getPlanarRegionsMap();
         PlanarRegionsListMessage planarRegionsListMessage = PlanarRegionMessageConverter.convertToPlanarRegionsListMessage(planarRegionsMap);
         reaMessager.submitMessage(planarRegionsStateTopicToSubmit, planarRegionsListMessage);
         planarRegionPublisher.publish(planarRegionsListMessage);

         SLAMFrame latestFrame = slam.getLatestFrame();
         Point3DReadOnly[] originalPointCloud = latestFrame.getOriginalPointCloud();
         Point3DReadOnly[] correctedPointCloud = latestFrame.getPointCloud();
         //Point3DReadOnly[] sourcePointsToWorld = slam.getSourcePointsToWorldLatestFrame();
         Point3DReadOnly[] sourcePointsToWorld = null;
         if (originalPointCloud == null || sourcePointsToWorld == null || correctedPointCloud == null)
            return;
         StereoVisionPointCloudMessage latestStereoMessage = createLatestFrameStereoVisionPointCloudMessage(originalPointCloud,
                                                                                                            sourcePointsToWorld,
                                                                                                            correctedPointCloud);
         RigidBodyTransformReadOnly sensorPose = latestFrame.getSensorPose();
         latestStereoMessage.getSensorPosition().set(sensorPose.getTranslation());
         latestStereoMessage.getSensorOrientation().set(sensorPose.getRotation());
         reaMessager.submitMessage(SLAMModuleAPI.IhmcSLAMFrameState, latestStereoMessage);
         reaMessager.submitMessage(SLAMModuleAPI.LatestFrameConfidenceFactor, latestFrame.getConfidenceFactor());

         if (estimatedPelvisPublisher != null)
         {
            StampedPosePacket posePacket = new StampedPosePacket();
            posePacket.setTimestamp(latestRobotTimeStamp.get());
            int maximumBufferOfQueue = 10;
            if (pointCloudQueue.size() >= maximumBufferOfQueue)
            {
               posePacket.setConfidenceFactor(0.0);
            }
            else
            {
               posePacket.setConfidenceFactor(latestFrame.getConfidenceFactor());
            }
            RigidBodyTransform estimatedPelvisPose = new RigidBodyTransform(sensorPoseToPelvisTransformer);
            estimatedPelvisPose.preMultiply(sensorPose);
            posePacket.getPose().set(estimatedPelvisPose);
            reaMessager.submitMessage(SLAMModuleAPI.CustomizedFrameState, posePacket);
            estimatedPelvisPublisher.publish(posePacket);
         }
>>>>>>> 9c5dd62fd96... Before test on Atlas.
      }
   }

   private StereoVisionPointCloudMessage createLatestFrameStereoVisionPointCloudMessage(Point3DReadOnly[] originalPointCloud,
                                                                                        Point3DReadOnly[] sourcePointsToWorld,
                                                                                        Point3DReadOnly[] correctedPointCloud)
   {
      int numberOfPointsToPack = originalPointCloud.length + sourcePointsToWorld.length + correctedPointCloud.length;

      Point3D[] pointCloudBuffer = new Point3D[numberOfPointsToPack];
      int[] colorBuffer = new int[numberOfPointsToPack];
      for (int i = 0; i < originalPointCloud.length; i++)
      {
         pointCloudBuffer[i] = new Point3D(originalPointCloud[i]);
         colorBuffer[i] = StereoVisionPointCloudViewer.colorToInt(LATEST_ORIGINAL_POINT_CLOUD_COLOR);
      }
      for (int i = originalPointCloud.length; i < originalPointCloud.length + sourcePointsToWorld.length; i++)
      {
         pointCloudBuffer[i] = new Point3D(sourcePointsToWorld[i - originalPointCloud.length]);
         colorBuffer[i] = StereoVisionPointCloudViewer.colorToInt(SOURCE_POINT_CLOUD_COLOR);
      }
      for (int i = originalPointCloud.length + sourcePointsToWorld.length; i < numberOfPointsToPack; i++)
      {
         pointCloudBuffer[i] = new Point3D(correctedPointCloud[i - originalPointCloud.length - sourcePointsToWorld.length]);
         colorBuffer[i] = StereoVisionPointCloudViewer.colorToInt(LATEST_POINT_CLOUD_COLOR);
      }
      return PointCloudCompression.compressPointCloud(19870612L, pointCloudBuffer, colorBuffer, numberOfPointsToPack, 0.001, null);
   }

   public void updateMain()
   {
      if (isMainThreadInterrupted())
         return;

      if (enable.get())
      {
         StereoVisionPointCloudMessage pointCloud = newPointCloud.getAndSet(null);
         if (pointCloud == null)
            return;

         queue(pointCloud);
      }
   }

   private void updateSLAMParameters()
   {
<<<<<<< HEAD
      RandomICPSLAMParameters parameters = slamParameters.get();
      slam.updateParameters(parameters);
=======
      RandomICPSLAMParameters parameters = ihmcSLAMParameters.get();
      //slam.updateParameters(parameters);
>>>>>>> 9c5dd62fd96... Before test on Atlas.
   }

   public void clearSLAM()
   {
      newPointCloud.set(null);
      pointCloudQueue.clear();
      slam.clear();
   }

   public void loadConfiguration(FilePropertyHelper filePropertyHelper)
   {
      Boolean enableFile = filePropertyHelper.loadBooleanProperty(SLAMModuleAPI.SLAMEnable.getName());
      if (enableFile != null)
         enable.set(enableFile);
      Boolean enableNormalEstimationFile = filePropertyHelper.loadBooleanProperty(SLAMModuleAPI.NormalEstimationEnable.getName());
      if (enableNormalEstimationFile != null)
         enableNormalEstimation.set(enableNormalEstimationFile);
      String slamParametersFile = filePropertyHelper.loadProperty(SLAMModuleAPI.SLAMParameters.getName());
      if (slamParametersFile != null)
         slamParameters.set(RandomICPSLAMParameters.parse(slamParametersFile));
      String normalEstimationParametersFile = filePropertyHelper.loadProperty(SLAMModuleAPI.NormalEstimationParameters.getName());
      if (normalEstimationParametersFile != null)
         normalEstimationParameters.set(NormalEstimationParameters.parse(normalEstimationParametersFile));
   }

   public void saveConfiguration(FilePropertyHelper filePropertyHelper)
   {
      filePropertyHelper.saveProperty(SLAMModuleAPI.SLAMEnable.getName(), enable.get());
      filePropertyHelper.saveProperty(SLAMModuleAPI.NormalEstimationEnable.getName(), enableNormalEstimation.get());

      filePropertyHelper.saveProperty(SLAMModuleAPI.SLAMParameters.getName(), slamParameters.get().toString());
      filePropertyHelper.saveProperty(SLAMModuleAPI.NormalEstimationParameters.getName(), normalEstimationParameters.get().toString());
   }

   private void handlePointCloud(Subscriber<StereoVisionPointCloudMessage> subscriber)
   {
      StereoVisionPointCloudMessage message = subscriber.takeNextData();
      newPointCloud.set(message);
      reaMessager.submitMessage(SLAMModuleAPI.DepthPointCloudState, new StereoVisionPointCloudMessage(message));
   }

   public static SLAMModule createIntraprocessModule() throws Exception
   {
      KryoMessager messager = KryoMessager.createIntraprocess(SLAMModuleAPI.API,
                                                              NetworkPorts.SLAM_MODULE_UI_PORT,
                                                              REACommunicationProperties.getPrivateNetClassList());
      messager.setAllowSelfSubmit(true);
      messager.startMessager();

      return new SLAMModule(messager);
   }
}
