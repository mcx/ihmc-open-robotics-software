package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl;

import java.util.EnumMap;

import us.ihmc.commonWalkingControlModules.configurations.HighLevelControllerParameters;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.HighLevelControlManagerFactory;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.HighLevelControllerStateFactory;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.WholeBodyControllerCoreFactory;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.highLevelStates.pushRecoveryController.PushRecoveryControllerParameters;
import us.ihmc.commonWalkingControlModules.momentumBasedController.HighLevelHumanoidControllerToolbox;
import us.ihmc.communication.controllerAPI.CommandInputManager;
import us.ihmc.communication.controllerAPI.StatusMessageOutputManager;
import us.ihmc.humanoidRobotics.communication.packets.dataobjects.HighLevelControllerName;
import us.ihmc.robotics.sensors.ForceSensorDataHolderReadOnly;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputListBasics;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputListReadOnly;
import us.ihmc.yoVariables.variable.YoEnum;

public class HighLevelControllerFactoryHelper
{
   private EnumMap<HighLevelControllerName, HighLevelControllerStateFactory> controllerFactories;
   private HighLevelHumanoidControllerToolbox controllerToolbox;
   private HighLevelControlManagerFactory managerFactory;
   private WholeBodyControllerCoreFactory controllerCoreFactory;
   private HighLevelControllerParameters highLevelControllerParameters;
   private WalkingControllerParameters walkingControllerParameters;
   private PushRecoveryControllerParameters pushRecoveryControllerParameters;
   private CommandInputManager commandInputManager;
   private StatusMessageOutputManager statusMessageOutputManager;
   private JointDesiredOutputListBasics lowLevelControllerOutput;
   private YoEnum<HighLevelControllerName> requestedHighLevelControllerState;
   private ForceSensorDataHolderReadOnly forceSensorDataHolder;

   public void setLowLevelControllerOutput(JointDesiredOutputListBasics lowLevelControllerOutput)
   {
      this.lowLevelControllerOutput = lowLevelControllerOutput;
   }

   public void setControllerFactories(EnumMap<HighLevelControllerName, HighLevelControllerStateFactory> controllerFactories)
   {
      this.controllerFactories = controllerFactories;
   }

   public void setHighLevelHumanoidControllerToolbox(HighLevelHumanoidControllerToolbox controllerToolbox)
   {
      this.controllerToolbox = controllerToolbox;
   }

   public void setParameters(HighLevelControllerParameters highLevelControllerParameters,
                             WalkingControllerParameters walkingControllerParameters,
                             PushRecoveryControllerParameters pushRecoveryControllerParameters)
   {
      this.highLevelControllerParameters = highLevelControllerParameters;
      this.walkingControllerParameters = walkingControllerParameters;
      this.pushRecoveryControllerParameters = pushRecoveryControllerParameters;
}

   public void setHighLevelControlManagerFactory(HighLevelControlManagerFactory managerFactory)
   {
      this.managerFactory = managerFactory;
   }

   public void setWholeBodyControllerCoreFactory(WholeBodyControllerCoreFactory controllerCoreFactory)
   {
      this.controllerCoreFactory = controllerCoreFactory;
   }

   public void setCommandInputManager(CommandInputManager commandInputManager)
   {
      this.commandInputManager = commandInputManager;
   }

   public void setStatusMessageOutputManager(StatusMessageOutputManager statusMessageOutputManager)
   {
      this.statusMessageOutputManager = statusMessageOutputManager;
   }

   public void setRequestedHighLevelControllerState(YoEnum<HighLevelControllerName> requestedHighLevelControllerState)
   {
      this.requestedHighLevelControllerState = requestedHighLevelControllerState;
   }

   public void setForceSensorDataHolder(ForceSensorDataHolderReadOnly forceSensorDataHolder)
   {
      this.forceSensorDataHolder = forceSensorDataHolder;
   }

   public EnumMap<HighLevelControllerName, HighLevelControllerStateFactory> getControllerFactories()
   {
      return controllerFactories;
   }

   public HighLevelHumanoidControllerToolbox getHighLevelHumanoidControllerToolbox()
   {
      return controllerToolbox;
   }

   public HighLevelControlManagerFactory getManagerFactory()
   {
      return managerFactory;
   }

   public WholeBodyControllerCoreFactory getWholeBodyControllerCoreFactory()
   {
      return controllerCoreFactory;
   }

   public HighLevelControllerParameters getHighLevelControllerParameters()
   {
      return highLevelControllerParameters;
   }

   public WalkingControllerParameters getWalkingControllerParameters()
   {
      return walkingControllerParameters;
   }

   public PushRecoveryControllerParameters getPushRecoveryControllerParameters()
   {
      return pushRecoveryControllerParameters;
   }

   public CommandInputManager getCommandInputManager()
   {
      return commandInputManager;
   }

   public StatusMessageOutputManager getStatusMessageOutputManager()
   {
      return statusMessageOutputManager;
   }

   public JointDesiredOutputListReadOnly getLowLevelControllerOutput()
   {
      return lowLevelControllerOutput;
   }

   public YoEnum<HighLevelControllerName> getRequestedHighLevelControllerState()
   {
      return requestedHighLevelControllerState;
   }

   public ForceSensorDataHolderReadOnly getForceSensorDataHolder()
   {
      return forceSensorDataHolder;
   }
}
