package us.ihmc.behaviors.sequence.actions;

import behavior_msgs.msg.dds.ActionExecutionStatusMessage;
import controller_msgs.msg.dds.SakeHandDesiredCommandMessage;
import us.ihmc.avatar.ros2.ROS2ControllerHelper;
import us.ihmc.avatar.sakeGripper.SakeHandCommandOption;
import us.ihmc.behaviors.sequence.BehaviorActionExecutor;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.tools.Timer;

public class SakeHandCommandActionExecutor implements BehaviorActionExecutor
{
   /** TODO: Make this variable. */
   private static final double WAIT_TIME = 0.5;

   private final SakeHandCommandActionState state = new SakeHandCommandActionState();
   private final SakeHandCommandActionDefinition definition = state.getDefinition();
   private final ROS2ControllerHelper ros2ControllerHelper;
   private int actionIndex;
   private final Timer executionTimer = new Timer();
   private boolean isExecuting;
   private final ActionExecutionStatusMessage executionStatusMessage = new ActionExecutionStatusMessage();

   public SakeHandCommandActionExecutor(ROS2ControllerHelper ros2ControllerHelper)
   {
      this.ros2ControllerHelper = ros2ControllerHelper;
   }

   @Override
   public void update(int actionIndex, int nextExecutionIndex, boolean concurrentActionIsNextForExecution)
   {
      definition.update();

      this.actionIndex = actionIndex;
   }

   @Override
   public void triggerActionExecution()
   {
      SakeHandDesiredCommandMessage message = new SakeHandDesiredCommandMessage();
      message.setRobotSide(definition.getSide().toByte());
      message.setDesiredHandConfiguration((byte) SakeHandCommandOption.values[definition.getHandConfigurationIndex()].getCommandNumber());
      message.setPostionRatio(definition.getGoalPosition());
      message.setTorqueRatio(definition.getGoalTorque());

      ros2ControllerHelper.publish(ROS2Tools::getHandSakeCommandTopic, message);

      executionTimer.reset();
   }

   @Override
   public void updateCurrentlyExecuting()
   {
      isExecuting = executionTimer.isRunning(WAIT_TIME);

      executionStatusMessage.setActionIndex(actionIndex);
      executionStatusMessage.setNominalExecutionDuration(WAIT_TIME);
      executionStatusMessage.setElapsedExecutionTime(executionTimer.getElapsedTime());
   }

   @Override
   public ActionExecutionStatusMessage getExecutionStatusMessage()
   {
      return executionStatusMessage;
   }

   @Override
   public boolean isExecuting()
   {
      return isExecuting;
   }

   @Override
   public SakeHandCommandActionState getState()
   {
      return state;
   }

   public SakeHandCommandActionDefinition getDefinition()
   {
      return definition;
   }
}
