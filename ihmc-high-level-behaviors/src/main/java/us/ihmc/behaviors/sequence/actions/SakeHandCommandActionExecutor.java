package us.ihmc.behaviors.sequence.actions;

import behavior_msgs.msg.dds.ActionExecutionStatusMessage;
import controller_msgs.msg.dds.SakeHandDesiredCommandMessage;
import us.ihmc.avatar.ros2.ROS2ControllerHelper;
import us.ihmc.avatar.sakeGripper.SakeHandCommandOption;
import us.ihmc.behaviors.sequence.BehaviorActionExecutor;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.tools.Timer;

public class SakeHandCommandActionExecutor extends BehaviorActionExecutor<SakeHandCommandActionState, SakeHandCommandActionDefinition>
{
   /** TODO: Make this variable. */
   private static final double WAIT_TIME = 0.5;

   private final SakeHandCommandActionState state;
   private final ROS2ControllerHelper ros2ControllerHelper;
   private final Timer executionTimer = new Timer();
   private final ActionExecutionStatusMessage executionStatusMessage = new ActionExecutionStatusMessage();

   public SakeHandCommandActionExecutor(long id, ROS2ControllerHelper ros2ControllerHelper)
   {
      this.ros2ControllerHelper = ros2ControllerHelper;

      state = new SakeHandCommandActionState(id);
   }

   @Override
   public void update()
   {
      super.update();
   }

   @Override
   public void triggerActionExecution()
   {
      SakeHandDesiredCommandMessage message = new SakeHandDesiredCommandMessage();
      message.setRobotSide(getDefinition().getSide().toByte());
      message.setDesiredHandConfiguration((byte) SakeHandCommandOption.values[getDefinition().getHandConfigurationIndex()].getCommandNumber());
      message.setPostionRatio(getDefinition().getGoalPosition());
      message.setTorqueRatio(getDefinition().getGoalTorque());

      ros2ControllerHelper.publish(ROS2Tools::getHandSakeCommandTopic, message);

      executionTimer.reset();
   }

   @Override
   public void updateCurrentlyExecuting()
   {
      state.setIsExecuting(executionTimer.isRunning(WAIT_TIME));

      executionStatusMessage.setActionIndex(state.getActionIndex());
      executionStatusMessage.setNominalExecutionDuration(WAIT_TIME);
      executionStatusMessage.setElapsedExecutionTime(executionTimer.getElapsedTime());
   }

   @Override
   public ActionExecutionStatusMessage getExecutionStatusMessage()
   {
      return executionStatusMessage;
   }

   @Override
   public SakeHandCommandActionState getState()
   {
      return state;
   }
}
