package us.ihmc.humanoidBehaviors.ui.tools;

import controller_msgs.msg.dds.*;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.ControllerAPIDefinition;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.MessageTopicNameGenerator;
import us.ihmc.communication.controllerAPI.RobotLowLevelMessenger;
import us.ihmc.humanoidRobotics.communication.packets.HumanoidMessageTools;
import us.ihmc.humanoidRobotics.communication.packets.atlas.AtlasLowLevelControlMode;
import us.ihmc.ros2.Ros2Node;

public class AtlasDirectRobotInterface implements RobotLowLevelMessenger
{
   private final IHMCROS2Publisher<AtlasLowLevelControlModeMessage> lowLevelModePublisher;
   private final IHMCROS2Publisher<BDIBehaviorCommandPacket> bdiBehaviorPublisher;
   private final IHMCROS2Publisher<AtlasDesiredPumpPSIPacket> desiredPumpPSIPublisher;
   private final IHMCROS2Publisher<AbortWalkingMessage> abortWalkingPublisher;
   private final IHMCROS2Publisher<PauseWalkingMessage> pauseWalkingPublisher;

   public AtlasDirectRobotInterface(Ros2Node ros2Node, DRCRobotModel robotModel)
   {
      MessageTopicNameGenerator subscriberTopicNameGenerator = ControllerAPIDefinition.getSubscriberTopicNameGenerator(robotModel.getSimpleRobotName());
      lowLevelModePublisher = ROS2Tools.createPublisher(ros2Node, AtlasLowLevelControlModeMessage.class, subscriberTopicNameGenerator);
      bdiBehaviorPublisher = ROS2Tools.createPublisher(ros2Node, BDIBehaviorCommandPacket.class, subscriberTopicNameGenerator);
      desiredPumpPSIPublisher = ROS2Tools.createPublisher(ros2Node, AtlasDesiredPumpPSIPacket.class, subscriberTopicNameGenerator);
      abortWalkingPublisher = ROS2Tools.createPublisher(ros2Node, AbortWalkingMessage.class, subscriberTopicNameGenerator);
      pauseWalkingPublisher = ROS2Tools.createPublisher(ros2Node, PauseWalkingMessage.class, subscriberTopicNameGenerator);
   }

   @Override
   public void sendAbortWalkingRequest()
   {
      abortWalkingPublisher.publish(new AbortWalkingMessage());
   }

   @Override
   public void sendPauseWalkingRequest()
   {
      PauseWalkingMessage message = new PauseWalkingMessage();
      message.setPause(true);
      pauseWalkingPublisher.publish(message);
   }

   @Override
   public void sendContinueWalkingRequest()
   {
      PauseWalkingMessage message = new PauseWalkingMessage();
      message.setPause(false);
      pauseWalkingPublisher.publish(message);
   }

   @Override
   public void sendFreezeRequest()
   {
      AtlasLowLevelControlModeMessage message = new AtlasLowLevelControlModeMessage();
      message.setRequestedAtlasLowLevelControlMode(AtlasLowLevelControlMode.FREEZE.toByte());
      lowLevelModePublisher.publish(message);
   }

   @Override
   public void sendStandRequest()
   {
      AtlasLowLevelControlModeMessage message = new AtlasLowLevelControlModeMessage();
      message.setRequestedAtlasLowLevelControlMode(AtlasLowLevelControlMode.STAND_PREP.toByte());
      lowLevelModePublisher.publish(message);
   }

   @Override
   public void sendShutdownRequest()
   {
      BDIBehaviorCommandPacket message = HumanoidMessageTools.createBDIBehaviorCommandPacket(true);
      bdiBehaviorPublisher.publish(message);
   }

   @Override
   public void setHydraulicPumpPSI(int psi)
   {
      AtlasDesiredPumpPSIPacket message = new AtlasDesiredPumpPSIPacket();
      message.setDesiredPumpPsi(psi);
      desiredPumpPSIPublisher.publish(message);
   }
}
