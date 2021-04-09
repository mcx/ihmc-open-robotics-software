package us.ihmc.avatar.sensors.realsense;

import controller_msgs.msg.dds.RobotConfigurationData;
import map_sense.RawGPUPlanarRegionList;
import org.apache.commons.lang3.mutable.MutableDouble;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.ros.RobotROSClockCalculator;
import us.ihmc.commons.Conversions;
import us.ihmc.communication.IHMCROS2Callback;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.euclid.exceptions.NotARotationMatrixException;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.log.LogTools;
import us.ihmc.robotEnvironmentAwareness.updaters.GPUPlanarRegionUpdater;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.ros2.ROS2NodeInterface;
import us.ihmc.sensorProcessing.communication.producers.RobotConfigurationDataBuffer;
import us.ihmc.tools.thread.MissingThreadTools;
import us.ihmc.tools.thread.ResettableExceptionHandlingExecutorService;
import us.ihmc.utilities.ros.RosNodeInterface;
import us.ihmc.utilities.ros.subscriber.AbstractRosTopicSubscriber;

import java.util.function.Consumer;

public class DelayFixedPlanarRegionsSubscription
{
   public static final double INITIAL_DELAY_OFFSET = 0.07; // TODO: Put in a stored property set

   private final ResettableExceptionHandlingExecutorService executorService;
   private final GPUPlanarRegionUpdater gpuPlanarRegionUpdater = new GPUPlanarRegionUpdater();
   private final ReferenceFrame sensorFrame;
   private final RobotConfigurationDataBuffer robotConfigurationDataBuffer;
   private final HumanoidReferenceFrames referenceFrames;
   private final ROS2NodeInterface ros2Node;
   private final DRCRobotModel robotModel;
   private final String topic;
   private final Consumer<PlanarRegionsList> callback;
   private final MutableDouble delayOffset = new MutableDouble(INITIAL_DELAY_OFFSET);
   private final FullHumanoidRobotModel fullRobotModel;
   private final RobotROSClockCalculator rosClockCalculator;
   private IHMCROS2Callback<?> robotConfigurationDataSubscriber;

   private boolean enabled = false;
   private AbstractRosTopicSubscriber<RawGPUPlanarRegionList> subscriber;
   private double delay = 0.0;
   private RosNodeInterface ros1Node;

   public DelayFixedPlanarRegionsSubscription(ROS2NodeInterface ros2Node,
                                              DRCRobotModel robotModel,
                                              String topic,
                                              Consumer<PlanarRegionsList> callback)
   {
      this.ros2Node = ros2Node;
      this.robotModel = robotModel;
      this.topic = topic;
      this.callback = callback;

      rosClockCalculator = robotModel.getROSClockCalculator();
      ROS2Tools.createCallbackSubscription2(ros2Node,
                                            ROS2Tools.getRobotConfigurationDataTopic(robotModel.getSimpleRobotName()),
                                            rosClockCalculator::receivedRobotConfigurationData);

      ROS2Tools.createCallbackSubscription2(ros2Node, ROS2Tools.MAPSENSE_REGIONS_DELAY_OFFSET, message -> delayOffset.setValue(message.getData()));

      boolean daemon = true;
      int queueSize = 1;
      executorService = MissingThreadTools.newSingleThreadExecutor("ROS1PlanarRegionsSubscriber", daemon, queueSize);

      gpuPlanarRegionUpdater.attachROS2Tuner(ros2Node);

      fullRobotModel = robotModel.createFullRobotModel();
      robotConfigurationDataBuffer = new RobotConfigurationDataBuffer();

      referenceFrames = new HumanoidReferenceFrames(fullRobotModel);

      ReferenceFrame baseFrame = robotModel.getSensorInformation().getSteppingCameraFrame(referenceFrames);

      RigidBodyTransformReadOnly transform = robotModel.getSensorInformation().getSteppingCameraTransform();
      sensorFrame = ReferenceFrameTools.constructFrameWithUnchangingTransformToParent("l515", baseFrame, transform);
   }

   public void subscribe(RosNodeInterface ros1Node)
   {
      this.ros1Node = ros1Node;
      rosClockCalculator.subscribeToROS1Topics(ros1Node);
      subscriber = MapsenseTools.createROS1Callback(topic, ros1Node, this::acceptRawGPUPlanarRegionsList);
   }

   public void unsubscribe(RosNodeInterface ros1Node)
   {
      ros1Node.removeSubscriber(subscriber);
      rosClockCalculator.unsubscribeFromROS1Topics(ros1Node);
   }

   private void acceptRobotConfigurationData(RobotConfigurationData robotConfigurationData)
   {
      // LogTools.info("Recieved robot configuration data w/ timestamp: {}", data.getMonotonicTime());
      robotConfigurationDataBuffer.update(robotConfigurationData);
   }

   private void acceptRawGPUPlanarRegionsList(RawGPUPlanarRegionList rawGPUPlanarRegionList)
   {
      if (enabled)
      {
         executorService.clearQueueAndExecute(() ->
         {
            long timestamp = rawGPUPlanarRegionList.getHeader().getStamp().totalNsecs();
            //      LogTools.info("rawGPU timestamp: {}", timestamp);
            double seconds = delayOffset.getValue();
            //      LogTools.info("Latest delay: {}", seconds);
            timestamp -= Conversions.secondsToNanoseconds(seconds);


            if (!rosClockCalculator.offsetIsDetermined())
            {
               delay = Double.NaN;
               return;
            }

            long controllerTime = rosClockCalculator.computeRobotMonotonicTime(timestamp);
            if (controllerTime == -1L)
            {
               delay = Double.NaN;
               return;
            }

            long newestTimestamp = robotConfigurationDataBuffer.getNewestTimestamp();
            if (newestTimestamp == -1L)
            {
               delay = Double.NaN;
               return;
            }

            boolean waitIfNecessary = false; // dangerous if true! need a timeout
            long selectedTimestamp = robotConfigurationDataBuffer.updateFullRobotModel(waitIfNecessary, controllerTime, fullRobotModel, null);
            if (selectedTimestamp != -1L)
            {
               long currentTimeInWall = ros1Node.getCurrentTime().totalNsecs();
               long selectedTimeInWall = selectedTimestamp - rosClockCalculator.getCurrentTimestampOffset();
               delay = Conversions.nanosecondsToSeconds(currentTimeInWall - selectedTimeInWall);

               try
               {
                  referenceFrames.updateFrames();
               }
               catch (NotARotationMatrixException e)
               {
                  LogTools.error(e.getMessage());
               }

               PlanarRegionsList planarRegionsList = gpuPlanarRegionUpdater.generatePlanarRegions(rawGPUPlanarRegionList);
               try
               {
                  planarRegionsList.applyTransform(MapsenseTools.getTransformFromCameraToWorld());
                  planarRegionsList.applyTransform(sensorFrame.getTransformToWorldFrame());
               }
               catch (NotARotationMatrixException e)
               {
                  LogTools.error(e.getMessage());
               }
               callback.accept(planarRegionsList);
            }
         });
      }
   }

   public void destroy()
   {
      executorService.destroy();
   }

   public void setEnabled(boolean enabled)
   {
      if (this.enabled != enabled)
      {
         if (enabled)
         {
            robotConfigurationDataSubscriber = ROS2Tools.createCallbackSubscription2(ros2Node,
                                                                                     ROS2Tools.getRobotConfigurationDataTopic(robotModel.getSimpleRobotName()),
                                                                                     this::acceptRobotConfigurationData);
         }
         else
         {
            executorService.interruptAndReset();
            robotConfigurationDataSubscriber.destroy();
            robotConfigurationDataSubscriber = null;
         }
      }

      this.enabled = enabled;
   }

   public double getDelay()
   {
      return delay;
   }
}