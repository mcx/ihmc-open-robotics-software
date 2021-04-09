package us.ihmc.avatar.sensors.realsense;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RemoteSyncedRobotModel;
import us.ihmc.avatar.networkProcessor.stereoPointCloudPublisher.PointCloudData;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.log.LogTools;
import us.ihmc.robotEnvironmentAwareness.communication.converters.PointCloudMessageTools;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2QosProfile;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.tools.Timer;
import us.ihmc.tools.UnitConversions;
import us.ihmc.tools.thread.MissingThreadTools;
import us.ihmc.tools.thread.ResettableExceptionHandlingExecutorService;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.subscriber.AbstractRosTopicSubscriber;

import java.util.ArrayList;

public class RealsensePointCloudROS1Bridge extends AbstractRosTopicSubscriber<sensor_msgs.PointCloud2>
{
   private static final int MAX_POINTS = 5000;
   private static final double MIN_PUBLISH_PERIOD = UnitConversions.hertzToSeconds(10.0);

   private final IHMCROS2Publisher<StereoVisionPointCloudMessage> publisher;
   private final RemoteSyncedRobotModel syncedRobot;
   private final ReferenceFrame sensorBaseFrame;
   private final FramePose3D tempSensorFramePose = new FramePose3D();
   private final RigidBodyTransformReadOnly sensorTransform;
   private final RigidBodyTransform transformToWorld = new RigidBodyTransform();
   private final Timer throttleTimer = new Timer();
   private final ResettableExceptionHandlingExecutorService executor = MissingThreadTools.newSingleThreadExecutor(getClass().getSimpleName(), true, 1);

   public RealsensePointCloudROS1Bridge(DRCRobotModel robotModel,
                                        RosMainNode ros1Node,
                                        ROS2Node ros2Node,
                                        RigidBodyTransformReadOnly sensorTransform,
                                        String ros1InputTopic, ROS2Topic<StereoVisionPointCloudMessage> ros2OutputTopic)
   {
      super(sensor_msgs.PointCloud2._TYPE);

      this.sensorTransform = sensorTransform;

      syncedRobot = new RemoteSyncedRobotModel(robotModel, ros2Node);
      sensorBaseFrame = robotModel.getSensorInformation().getSteppingCameraFrame(syncedRobot.getReferenceFrames());

      LogTools.info("Subscribing ROS 1: {}", ros1InputTopic);
      ros1Node.attachSubscriber(ros1InputTopic, this);

      LogTools.info("Publishing ROS 2: {}", ros2OutputTopic.getName());
      publisher = ROS2Tools.createPublisher(ros2Node, ros2OutputTopic, ROS2QosProfile.DEFAULT());
   }

   @Override
   public void onNewMessage(sensor_msgs.PointCloud2 ros1PointCloud)
   {
      executor.clearQueueAndExecute(() -> waitThenAct(ros1PointCloud));
   }

   private void waitThenAct(sensor_msgs.PointCloud2 ros1PointCloud)
   {
      throttleTimer.sleepUntilExpiration(MIN_PUBLISH_PERIOD);
      throttleTimer.reset();

      compute(ros1PointCloud);
   }

   private void compute(sensor_msgs.PointCloud2 ros1PointCloud)
   {
      try
      {
         boolean hasColors = true;
         PointCloudData pointCloudData = new PointCloudData(ros1PointCloud, MAX_POINTS, hasColors);

         pointCloudData.flipToZUp();

         syncedRobot.update();
         sensorBaseFrame.getTransformToDesiredFrame(transformToWorld, ReferenceFrame.getWorldFrame());
         transformToWorld.multiply(sensorTransform);
         tempSensorFramePose.set(transformToWorld);
         pointCloudData.applyTransform(transformToWorld);

         ArrayList<Point3D> pointCloud = new ArrayList<>();
         for (int i = 0; i < pointCloudData.getNumberOfPoints(); i++)
         {
            pointCloud.add(new Point3D(pointCloudData.getPointCloud()[i]));
         }

         StereoVisionPointCloudMessage message = PointCloudMessageTools.toStereoVisionPointCloudMessage(pointCloud, tempSensorFramePose);
//         LogTools.info("Publishing point cloud of size {}", message.getNumberOfPoints());
         publisher.publish(message);
      }
      catch (Exception e)
      {
         LogTools.error(e.getMessage());
         e.printStackTrace();
      }
   }
}