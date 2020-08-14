package us.ihmc.atlas.behaviors;

import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.avatar.factory.AvatarSimulation;
import us.ihmc.avatar.factory.AvatarSimulationFactory;
import us.ihmc.avatar.initialSetup.DRCGuiInitialSetup;
import us.ihmc.avatar.initialSetup.DRCSCSInitialSetup;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.ContactableBodiesFactory;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.factories.HighLevelHumanoidControllerFactory;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.humanoidRobotics.communication.packets.dataobjects.HighLevelControllerName;
import us.ihmc.log.LogTools;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.simulationConstructionSetTools.util.environments.CommonAvatarEnvironmentInterface;
import us.ihmc.simulationConstructionSetTools.util.environments.FlatGroundEnvironment;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.util.simulationTesting.SimulationTestingParameters;
import us.ihmc.tools.gui.AWTTools;
import us.ihmc.wholeBodyController.RobotContactPointParameters;

import static us.ihmc.humanoidRobotics.communication.packets.dataobjects.HighLevelControllerName.DO_NOTHING_BEHAVIOR;
import static us.ihmc.humanoidRobotics.communication.packets.dataobjects.HighLevelControllerName.WALKING;

public class AtlasDynamicsSimulation
{
   private final RealtimeRos2Node realtimeRos2Node;
   private final SimulationConstructionSet simulationConstructionSet;
   private final AvatarSimulation avatarSimulation;

   public static AtlasDynamicsSimulation createForManualTest(DRCRobotModel robotModel,
                                                             CommonAvatarEnvironmentInterface environment,
                                                             int recordTicksPerControllerTick,
                                                             int dataBufferSize)
   {
      return create(robotModel, environment, PubSubImplementation.FAST_RTPS, recordTicksPerControllerTick, dataBufferSize, false);
   }

   public static AtlasDynamicsSimulation createForAutomatedTest(DRCRobotModel robotModel, CommonAvatarEnvironmentInterface environment)
   {
      return create(robotModel, environment, PubSubImplementation.INTRAPROCESS, 1, 1024, false);
   }

   public static AtlasDynamicsSimulation create(DRCRobotModel robotModel,
                                                CommonAvatarEnvironmentInterface environment,
                                                PubSubImplementation pubSubImplementation,
                                                int recordTicksPerControllerTick,
                                                int dataBufferSize,
                                                boolean logToFile)
   {
      SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromSystemProperties();
      DRCGuiInitialSetup guiInitialSetup = new DRCGuiInitialSetup(false, false, simulationTestingParameters);

      DRCSCSInitialSetup scsInitialSetup = new DRCSCSInitialSetup(environment, robotModel.getSimulateDT());
      scsInitialSetup.setInitializeEstimatorToActual(true);
      scsInitialSetup.setTimePerRecordTick(robotModel.getControllerDT() * recordTicksPerControllerTick);
      scsInitialSetup.setUsePerfectSensors(true);
      scsInitialSetup.setSimulationDataBufferSize(dataBufferSize);

      RobotContactPointParameters<RobotSide> contactPointParameters = robotModel.getContactPointParameters();
      ContactableBodiesFactory<RobotSide> contactableBodiesFactory = new ContactableBodiesFactory<>();
      contactableBodiesFactory.setFootContactPoints(contactPointParameters.getFootContactPoints());
      contactableBodiesFactory
            .setToeContactParameters(contactPointParameters.getControllerToeContactPoints(), contactPointParameters.getControllerToeContactLines());
      for (int i = 0; i < contactPointParameters.getAdditionalContactNames().size(); i++)
      {
         contactableBodiesFactory.addAdditionalContactPoint(contactPointParameters.getAdditionalContactRigidBodyNames().get(i),
                                                            contactPointParameters.getAdditionalContactNames().get(i),
                                                            contactPointParameters.getAdditionalContactTransforms().get(i));
      }

      RealtimeRos2Node realtimeRos2Node = ROS2Tools.createRealtimeRos2Node(pubSubImplementation, "humanoid_simulation_controller");

      HighLevelHumanoidControllerFactory controllerFactory = new HighLevelHumanoidControllerFactory(contactableBodiesFactory,
                                                                                                    robotModel.getSensorInformation().getFeetForceSensorNames(),
                                                                                                    robotModel.getSensorInformation()
                                                                                                              .getFeetContactSensorNames(),
                                                                                                    robotModel.getSensorInformation()
                                                                                                              .getWristForceSensorNames(),
                                                                                                    robotModel.getHighLevelControllerParameters(),
                                                                                                    robotModel.getWalkingControllerParameters(),
                                                                                                    robotModel.getCapturePointPlannerParameters());
      controllerFactory.useDefaultDoNothingControlState();
      controllerFactory.useDefaultWalkingControlState();
      controllerFactory.addRequestableTransition(DO_NOTHING_BEHAVIOR, WALKING);
      controllerFactory.addRequestableTransition(WALKING, DO_NOTHING_BEHAVIOR);
      controllerFactory.addControllerFailureTransition(DO_NOTHING_BEHAVIOR, DO_NOTHING_BEHAVIOR);
      controllerFactory.addControllerFailureTransition(WALKING, DO_NOTHING_BEHAVIOR);
      controllerFactory.setInitialState(HighLevelControllerName.WALKING);
      controllerFactory.createControllerNetworkSubscriber(robotModel.getSimpleRobotName(), realtimeRos2Node);

      AvatarSimulationFactory avatarSimulationFactory = new AvatarSimulationFactory();
      avatarSimulationFactory.setRobotModel(robotModel);
      avatarSimulationFactory.setShapeCollisionSettings(robotModel.getShapeCollisionSettings());
      avatarSimulationFactory.setHighLevelHumanoidControllerFactory(controllerFactory);
      avatarSimulationFactory.setCommonAvatarEnvironment(environment);
      avatarSimulationFactory.setRobotInitialSetup(robotModel.getDefaultRobotInitialSetup(0.0, 0.0));
      avatarSimulationFactory.setSCSInitialSetup(scsInitialSetup);
      avatarSimulationFactory.setGuiInitialSetup(guiInitialSetup);
      avatarSimulationFactory.setRealtimeRos2Node(realtimeRos2Node);
      avatarSimulationFactory.setCreateYoVariableServer(false);
      avatarSimulationFactory.setLogToFile(logToFile);

      AvatarSimulation avatarSimulation = avatarSimulationFactory.createAvatarSimulation();
      SimulationConstructionSet scs = avatarSimulation.getSimulationConstructionSet();
      if (scs.getGUI() != null )
         scs.getGUI().getFrame().setSize(AWTTools.getDimensionOfSmallestScreenScaled(2.0 / 3.0));

      avatarSimulation.start();
      realtimeRos2Node.spin();  // TODO Should probably happen in start()

      // TODO set up some useful graphs

      scs.setupGraph("root.atlas.t");
      scs.setupGraph("root.atlas.DRCSimulation.DRCControllerThread.DRCMomentumBasedController.HumanoidHighLevelControllerManager.highLevelControllerNameCurrentState");

      return new AtlasDynamicsSimulation(realtimeRos2Node, avatarSimulation);
   }

   private AtlasDynamicsSimulation(RealtimeRos2Node realtimeRos2Node, AvatarSimulation avatarSimulation)
   {
      this.realtimeRos2Node = realtimeRos2Node;
      this.avatarSimulation = avatarSimulation;
      this.simulationConstructionSet = avatarSimulation.getSimulationConstructionSet();
   }

   public void destroy()
   {
      LogTools.info("Shutting down");
      avatarSimulation.destroy();
      realtimeRos2Node.destroy();
   }

   public RealtimeRos2Node getRealtimeRos2Node()
   {
      return realtimeRos2Node;
   }

   public SimulationConstructionSet getSimulationConstructionSet()
   {
      return simulationConstructionSet;
   }

   public AvatarSimulation getAvatarSimulation()
   {
      return avatarSimulation;
   }

   public void simulate()
   {
      simulationConstructionSet.simulate();
   }

   public static void main(String[] args)
   {
      int recordTicksPerControllerTick = 1;
      int dataBufferSize = 1024;
      createForManualTest(new AtlasRobotModel(AtlasBehaviorModule.ATLAS_VERSION, RobotTarget.SCS, false),
                          new FlatGroundEnvironment(),
                          recordTicksPerControllerTick,
                          dataBufferSize).simulate();
   }
}