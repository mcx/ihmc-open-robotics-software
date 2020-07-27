package us.ihmc.robotEnvironmentAwareness.slam;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import javafx.scene.paint.Color;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.jOctoMap.normalEstimation.NormalEstimationParameters;
import us.ihmc.jOctoMap.ocTree.NormalOcTree;
import us.ihmc.log.LogTools;
import us.ihmc.messager.Messager;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.pubsub.subscriber.Subscriber;
import us.ihmc.robotEnvironmentAwareness.communication.SLAMModuleAPI;
import us.ihmc.robotEnvironmentAwareness.communication.SegmentationModuleAPI;
import us.ihmc.robotEnvironmentAwareness.communication.converters.BoundingBoxMessageConverter;
import us.ihmc.robotEnvironmentAwareness.communication.converters.OcTreeMessageConverter;
import us.ihmc.robotEnvironmentAwareness.communication.converters.PointCloudCompression;
import us.ihmc.robotEnvironmentAwareness.communication.packets.BoundingBoxParametersMessage;
import us.ihmc.robotEnvironmentAwareness.communication.packets.NormalOcTreeMessage;
import us.ihmc.robotEnvironmentAwareness.io.FilePropertyHelper;
import us.ihmc.robotEnvironmentAwareness.perceptionSuite.PerceptionModule;
import us.ihmc.robotEnvironmentAwareness.ros.REASourceType;
import us.ihmc.robotEnvironmentAwareness.tools.ExecutorServiceTools;
import us.ihmc.robotEnvironmentAwareness.tools.ExecutorServiceTools.ExceptionHandling;
import us.ihmc.robotEnvironmentAwareness.ui.graphicsBuilders.StereoVisionPointCloudViewer;
import us.ihmc.robotEnvironmentAwareness.updaters.OcTreeConsumer;
import us.ihmc.ros2.Ros2Node;

public class SLAMModule implements PerceptionModule
{
   protected final Messager reaMessager;

   private static final double DEFAULT_OCTREE_RESOLUTION = 0.02;

   private static final Color LATEST_ORIGINAL_POINT_CLOUD_COLOR = Color.BEIGE;
   private static final Color SOURCE_POINT_CLOUD_COLOR = Color.RED;
   private static final Color LATEST_POINT_CLOUD_COLOR = Color.LIME;

   protected final AtomicReference<Boolean> enable;

   private final AtomicReference<StereoVisionPointCloudMessage> newPointCloud = new AtomicReference<>(null);
   protected final LinkedList<StereoVisionPointCloudMessage> pointCloudQueue = new LinkedList<>();

   protected final AtomicReference<SurfaceElementICPSLAMParameters> slamParameters;
   private final AtomicReference<NormalEstimationParameters> normalEstimationParameters;
   private final AtomicReference<Boolean> enableNormalEstimation;
   private final AtomicReference<Boolean> clearNormals;

   private final AtomicReference<Boolean> isOcTreeBoundingBoxRequested;
   private final AtomicReference<BoundingBoxParametersMessage> atomicBoundingBoxParameters;
   private final AtomicReference<Boolean> useBoundingBox;
   
   protected final SurfaceElementICPSLAM slam = new SurfaceElementICPSLAM(DEFAULT_OCTREE_RESOLUTION);

   private static final int SLAM_PERIOD_MILLISECONDS = 20;
   private static final int QUEUE_PERIOD_MILLISECONDS = 5;
   private static final int MAIN_PERIOD_MILLISECONDS = 200;

   private ScheduledExecutorService executorService = ExecutorServiceTools.newScheduledThreadPool(3, getClass(), ExceptionHandling.CATCH_AND_REPORT);
   private ScheduledFuture<?> scheduledQueue;
   private ScheduledFuture<?> scheduledMain;
   private ScheduledFuture<?> scheduledSLAM;

   private Stopwatch stopwatch = new Stopwatch();

   private final boolean manageRosNode;
   protected final Ros2Node ros2Node;

   private final List<OcTreeConsumer> ocTreeConsumers = new ArrayList<>();

   private final SLAMHistory history = new SLAMHistory();
   private final AtomicReference<String> slamDataExportPath;

   public SLAMModule(Messager messager)
   {
      this(messager, null);
   }

   public SLAMModule(Ros2Node ros2Node, Messager messager)
   {
      this(ros2Node, messager, null);
   }

   public SLAMModule(Messager messager, File configurationFile)
   {
      this(ROS2Tools.createRos2Node(PubSubImplementation.FAST_RTPS, ROS2Tools.REA_NODE_NAME), messager, configurationFile, true);
   }

   public SLAMModule(Ros2Node ros2Node, Messager messager, File configurationFile)
   {
      this(ros2Node, messager, configurationFile, false);
   }

   public SLAMModule(Ros2Node ros2Node, Messager messager, File configurationFile, boolean manageRosNode)
   {
      this.ros2Node = ros2Node;
      this.reaMessager = messager;
      this.manageRosNode = manageRosNode;

      // TODO: Check name space and fix. Suspected atlas sensor suite and publisher.
      ROS2Tools.createCallbackSubscription(ros2Node,
                                           StereoVisionPointCloudMessage.class,
                                           REASourceType.STEREO_POINT_CLOUD.getTopicName(),
                                           this::handlePointCloud);
      ROS2Tools.createCallbackSubscription(ros2Node,
                                           StereoVisionPointCloudMessage.class,
                                           REASourceType.DEPTH_POINT_CLOUD.getTopicName(),
                                           this::handlePointCloud);

      reaMessager.submitMessage(SLAMModuleAPI.UISensorPoseHistoryFrames, 1000);

      enable = reaMessager.createInput(SLAMModuleAPI.SLAMEnable, true);

      slamParameters = reaMessager.createInput(SLAMModuleAPI.SLAMParameters, new SurfaceElementICPSLAMParameters());

      enableNormalEstimation = reaMessager.createInput(SLAMModuleAPI.NormalEstimationEnable, true);
      clearNormals = reaMessager.createInput(SLAMModuleAPI.NormalEstimationClear, false);

      reaMessager.registerTopicListener(SLAMModuleAPI.SLAMClear, (content) -> clearSLAM());
      reaMessager.registerTopicListener(SLAMModuleAPI.RequestEntireModuleState, update -> sendCurrentState());

      NormalEstimationParameters normalEstimationParametersLocal = new NormalEstimationParameters();
      normalEstimationParametersLocal.setNumberOfIterations(10);
      normalEstimationParameters = reaMessager.createInput(SLAMModuleAPI.NormalEstimationParameters, normalEstimationParametersLocal);

      isOcTreeBoundingBoxRequested = reaMessager.createInput(SLAMModuleAPI.RequestBoundingBox, false);

      useBoundingBox = reaMessager.createInput(SLAMModuleAPI.OcTreeBoundingBoxEnable, true);
      atomicBoundingBoxParameters = reaMessager.createInput(SLAMModuleAPI.OcTreeBoundingBoxParameters,
                                                            BoundingBoxMessageConverter.createBoundingBoxParametersMessage(-1.0f,
                                                                                                                           -1.5f,
                                                                                                                           -1.5f,
                                                                                                                           2.0f,
                                                                                                                           1.5f,
                                                                                                                           0.5f));

      reaMessager.submitMessage(SLAMModuleAPI.NormalEstimationParameters, normalEstimationParametersLocal);

      if (configurationFile != null)
      {
         FilePropertyHelper filePropertyHelper = new FilePropertyHelper(configurationFile);
         loadConfiguration(filePropertyHelper);

         reaMessager.registerTopicListener(SLAMModuleAPI.SaveConfiguration, content -> saveConfiguration(filePropertyHelper));
      }
      slamDataExportPath = reaMessager.createInput(SLAMModuleAPI.UISLAMDataExportDirectory);
      reaMessager.registerTopicListener(SLAMModuleAPI.UISLAMDataExportRequest, content -> exportSLAMHistory());

      sendCurrentState();
   }

   private void loadConfigurationFile(FilePropertyHelper filePropertyHelper)
   {
      String slamParametersFile = filePropertyHelper.loadProperty(SLAMModuleAPI.SLAMParameters.getName());
      if (slamParametersFile != null)
         slamParameters.set(SurfaceElementICPSLAMParameters.parse(slamParametersFile));
      Boolean useBoundingBoxFile = filePropertyHelper.loadBooleanProperty(SLAMModuleAPI.OcTreeBoundingBoxEnable.getName());
      if (useBoundingBoxFile != null)
         useBoundingBox.set(useBoundingBoxFile);
      String boundingBoxParametersFile = filePropertyHelper.loadProperty(SLAMModuleAPI.OcTreeBoundingBoxParameters.getName());
      if (boundingBoxParametersFile != null)
         atomicBoundingBoxParameters.set(BoundingBoxMessageConverter.parse(boundingBoxParametersFile));
   }

   private void saveConfigurationFIle(FilePropertyHelper filePropertyHelper)
   {
      filePropertyHelper.saveProperty(SLAMModuleAPI.SLAMParameters.getName(), slamParameters.get().toString());
      filePropertyHelper.saveProperty(SLAMModuleAPI.OcTreeBoundingBoxEnable.getName(), useBoundingBox.get());
      filePropertyHelper.saveProperty(SLAMModuleAPI.OcTreeBoundingBoxParameters.getName(), atomicBoundingBoxParameters.get().toString());
   }

   private void exportSLAMHistory()
   {
      history.export(Paths.get(slamDataExportPath.get()));
   }

   public void attachOcTreeConsumer(OcTreeConsumer ocTreeConsumer)
   {
      this.ocTreeConsumers.add(ocTreeConsumer);
   }

   public void removeOcTreeConsumer(OcTreeConsumer ocTreeConsumer)
   {
      this.ocTreeConsumers.remove(ocTreeConsumer);
   }

   @Override
   public void start()
   {
      LogTools.info("Starting SLAM Module");
      if (scheduledMain == null)
         scheduledMain = executorService.scheduleAtFixedRate(this::updateMain, 0, MAIN_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
      if (scheduledQueue == null)
         scheduledQueue = executorService.scheduleAtFixedRate(this::updateQueue, 0, QUEUE_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
      if (scheduledSLAM == null)
         scheduledSLAM = executorService.scheduleAtFixedRate(this::updateSLAM, 0, SLAM_PERIOD_MILLISECONDS, TimeUnit.MILLISECONDS);
   }

   @Override
   public void stop()
   {
      LogTools.info("SLAM Module is going down");

      try
      {
         reaMessager.closeMessager();
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }

      if (scheduledQueue != null)
      {
         scheduledQueue.cancel(true);
         scheduledQueue = null;
      }

      if (scheduledMain != null)
      {
         scheduledMain.cancel(true);
         scheduledMain = null;
      }

      if (manageRosNode)
         ros2Node.destroy();

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

   private boolean isQueueThreadInterrupted()
   {
      return Thread.interrupted() || scheduledQueue == null || scheduledQueue.isCancelled();
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

      reaMessager.submitMessage(SLAMModuleAPI.OcTreeBoundingBoxEnable, useBoundingBox.get());
      reaMessager.submitMessage(SLAMModuleAPI.OcTreeBoundingBoxParameters, atomicBoundingBoxParameters.get());
   }

   public void updateMain()
   {
      if (isMainThreadInterrupted())
         return;

      slam.setNormalEstimationParameters(normalEstimationParameters.get());
      if (clearNormals.getAndSet(false))
         slam.clearNormals();
      if (enableNormalEstimation.get())
         slam.updateSurfaceNormals();
   }

   private void updateSLAM()
   {
      stopwatch.start();
      if (updateSLAMInternal())
      {
         publishResults();
      }

      if (isOcTreeBoundingBoxRequested.getAndSet(false))
         reaMessager.submitMessage(SLAMModuleAPI.OcTreeBoundingBoxState,
                                   BoundingBoxMessageConverter.convertToMessage(slam.getOctree().getBoundingBox()));
   }

   private boolean updateSLAMInternal()
   {
      if (isSLAMThreadInterrupted())
         return false;

      if (pointCloudQueue.size() == 0)
         return false;

      updateSLAMParameters();
      StereoVisionPointCloudMessage pointCloudToCompute = pointCloudQueue.getFirst();
      slam.handleBoundingBox(pointCloudToCompute.getSensorPosition(), pointCloudToCompute.getSensorOrientation(), atomicBoundingBoxParameters.get(), useBoundingBox.get());

      boolean success;
      if (slam.isEmpty())
      {
         LogTools.debug("addKeyFrame queueSize: {} pointCloudSize: {} timestamp: {}",
                       pointCloudQueue.size(),
                       pointCloudToCompute.getNumberOfPoints(),
                       pointCloudToCompute.getTimestamp());
         slam.addKeyFrame(pointCloudToCompute, slamParameters.get().getInsertMissInOcTree());
         success = true;
      }
      else
      {
         LogTools.debug("addFrame queueSize: {} pointCloudSize: {}, timestamp: {}",
                       pointCloudQueue.size(),
                       pointCloudToCompute.getNumberOfPoints(),
                       pointCloudToCompute.getTimestamp());
         success = addFrame(pointCloudToCompute);
         LogTools.debug("success: {} getComputationTimeForLatestFrame: {}", success, slam.getComputationTimeForLatestFrame());
      }

         
      dequeue();

      return success;
   }

   protected boolean addFrame(StereoVisionPointCloudMessage pointCloudToCompute)
   {
      return slam.addFrame(pointCloudToCompute, slamParameters.get().getInsertMissInOcTree());
   }

   protected void queue(StereoVisionPointCloudMessage pointCloud)
   {
      pointCloudQueue.add(pointCloud);
      while (pointCloudQueue.size() > slamParameters.get().getMaximumQueueSize())
         pointCloudQueue.removeFirst();
   }

   protected void dequeue()
   {
      pointCloudQueue.removeFirst();
   }

   protected void publishResults()
   {
      String stringToReport = "";
      reaMessager.submitMessage(SLAMModuleAPI.QueuedBuffers, pointCloudQueue.size() + " [" + slam.getMapSize() + "]");
      stringToReport = stringToReport + " " + slam.getMapSize() + " " + slam.getComputationTimeForLatestFrame() + " (sec) ";
      reaMessager.submitMessage(SLAMModuleAPI.SLAMStatus, stringToReport);

      NormalOcTree octreeMap = slam.getOctree();
      SLAMFrame latestFrame = slam.getLatestFrame();

      reaMessager.submitMessage(SLAMModuleAPI.SLAMOctreeMapState, OcTreeMessageConverter.convertToMessage(slam.getOctree()));

      LogTools.debug("Took: {} ocTree size: {}", stopwatch.totalElapsed(), octreeMap.size());
      Pose3D pose = new Pose3D(slam.getLatestFrame().getSensorPose());
      for (OcTreeConsumer ocTreeConsumer : ocTreeConsumers)
      {
         ocTreeConsumer.reportOcTree(octreeMap, pose);
      }
      Point3DReadOnly[] originalPointCloud = latestFrame.getUncorrectedPointCloudInWorld();
      Point3DReadOnly[] correctedPointCloud = latestFrame.getPointCloud();
      Point3DReadOnly[] sourcePointsToWorld = slam.getSourcePoints();
      if (originalPointCloud == null || sourcePointsToWorld == null || correctedPointCloud == null)
         return;
      SLAMFrameState frameState = new SLAMFrameState();
      frameState.setUncorrectedPointCloudInWorld(originalPointCloud);
      frameState.setCorrectedPointCloudInWorld(correctedPointCloud);
      frameState.setCorrespondingPointsInWorld(sourcePointsToWorld);
      RigidBodyTransformReadOnly sensorPose = latestFrame.getSensorPose();
      frameState.getSensorPosition().set(sensorPose.getTranslation());
      frameState.getSensorOrientation().set(sensorPose.getRotation());
      reaMessager.submitMessage(SLAMModuleAPI.IhmcSLAMFrameState, frameState);
      reaMessager.submitMessage(SLAMModuleAPI.LatestFrameConfidenceFactor, latestFrame.getConfidenceFactor());
      history.addLatestFrameHistory(latestFrame);
      history.addDriftCorrectionHistory(slam.getDriftCorrectionResult());
   }


   public void updateQueue()
   {
      if (isQueueThreadInterrupted())
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
      slam.updateParameters(slamParameters.get());
   }

   public void clearSLAM()
   {
      newPointCloud.set(null);
      pointCloudQueue.clear();
      slam.clear();
      history.clearHistory();
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
         slamParameters.set(SurfaceElementICPSLAMParameters.parse(slamParametersFile));
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
      LogTools.trace("Received point cloud. numberOfPoints: {} timestamp: {}", message.getNumberOfPoints(), message.getTimestamp());
      newPointCloud.set(message);
      reaMessager.submitMessage(SLAMModuleAPI.DepthPointCloudState, new StereoVisionPointCloudMessage(message));
   }

   public static SLAMModule createIntraprocessModule(Ros2Node ros2Node, Messager messager)
   {
      return new SLAMModule(ros2Node, messager);
   }

   public static SLAMModule createIntraprocessModule(Messager messager)
   {
      return new SLAMModule(messager);
   }

}
