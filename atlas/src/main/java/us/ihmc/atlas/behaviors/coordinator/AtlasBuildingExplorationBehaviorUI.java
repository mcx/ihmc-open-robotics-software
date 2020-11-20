package us.ihmc.atlas.behaviors.coordinator;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.atlas.AtlasRobotVersion;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.exception.ExceptionTools;
import us.ihmc.communication.CommunicationMode;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.humanoidBehaviors.BehaviorModule;
import us.ihmc.humanoidBehaviors.BehaviorRegistry;
import us.ihmc.humanoidBehaviors.IHMCHumanoidBehaviorManager;
import us.ihmc.humanoidBehaviors.RemoteBehaviorInterface;
import us.ihmc.humanoidBehaviors.demo.BuildingExplorationBehaviorCoordinator;
import us.ihmc.humanoidBehaviors.demo.BuildingExplorationStateName;
import us.ihmc.humanoidBehaviors.lookAndStep.LookAndStepBehavior;
import us.ihmc.humanoidBehaviors.stairs.TraverseStairsBehavior;
import us.ihmc.humanoidBehaviors.tools.BehaviorHelper;
import us.ihmc.humanoidBehaviors.ui.behaviors.coordinator.BuildingExplorationBehaviorUI;
import us.ihmc.javaFXToolkit.scenes.View3DFactory;
import us.ihmc.javaFXToolkit.shapes.JavaFXCoordinateSystem;
import us.ihmc.javafx.applicationCreator.JavaFXApplicationCreator;
import us.ihmc.log.LogTools;
import us.ihmc.messager.Messager;
import us.ihmc.multicastLogDataProtocol.modelLoaders.LogModelProvider;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.sensorProcessing.parameters.HumanoidRobotSensorInformation;

import java.util.concurrent.atomic.AtomicReference;

import static us.ihmc.humanoidBehaviors.demo.BuildingExplorationBehaviorAPI.*;
import static us.ihmc.humanoidBehaviors.demo.BuildingExplorationBehaviorAPI.ConfirmDoor;

public class AtlasBuildingExplorationBehaviorUI
{
   public static void start()
   {
      start(new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS, RobotTarget.REAL_ROBOT, false),
            CommunicationMode.INTERPROCESS,
            BehaviorRegistry.of(LookAndStepBehavior.DEFINITION, TraverseStairsBehavior.DEFINITION, BuildingExplorationBehaviorCoordinator.DEFINITION),
            CommunicationMode.INTRAPROCESS);
   }

   public static void start(DRCRobotModel robotModel,
                            CommunicationMode ros2CommunicationMode,
                            BehaviorRegistry behaviorRegistry,
                            CommunicationMode behaviorMessagerCommunicationMode)
   {
      HumanoidRobotSensorInformation sensorInformation = robotModel.getSensorInformation();
      LogModelProvider logModelProvider = robotModel.getLogModelProvider();
      ExceptionTools.handle(() ->
      {
         new IHMCHumanoidBehaviorManager(robotModel.getSimpleRobotName(),
                                         robotModel.getFootstepPlannerParameters(),
                                         robotModel,
                                         robotModel,
                                         logModelProvider,
                                         false,
                                         sensorInformation);

      }, DefaultExceptionHandler.RUNTIME_EXCEPTION);

      BehaviorModule behaviorModule = new BehaviorModule(behaviorRegistry, robotModel, ros2CommunicationMode, behaviorMessagerCommunicationMode);
      ROS2Node ros2Node = ROS2Tools.createROS2Node(ros2CommunicationMode.getPubSubImplementation(), "building_exploration");
      Messager behaviorMessager = behaviorMessagerCommunicationMode == CommunicationMode.INTRAPROCESS
            ? behaviorModule.getMessager() : RemoteBehaviorInterface.createForUI(behaviorRegistry, "localhost");

      JavaFXApplicationCreator.buildJavaFXApplication(primaryStage ->
      {
         ExceptionTools.handle(() ->
         {
            BehaviorHelper helper = new BehaviorHelper(robotModel, behaviorMessager, ros2Node);

            BuildingExplorationBehaviorCoordinator behaviorCoordinator = new BuildingExplorationBehaviorCoordinator(helper);

            AtomicReference<Pose3D> goal = behaviorMessager.createInput(Goal);

            behaviorMessager.registerTopicListener(RequestedState, behaviorCoordinator::requestState);
            AtomicReference<BuildingExplorationStateName> requestedState = behaviorMessager.createInput(RequestedState);

            behaviorMessager.registerTopicListener(Start, s ->
            {
               LogTools.debug("Start requested in UI... starting behavior coordinator");
               behaviorCoordinator.setBombPose(goal.get());
               behaviorCoordinator.requestState(requestedState.get());
               behaviorCoordinator.start();
            });
            behaviorMessager.registerTopicListener(Stop, s -> behaviorCoordinator.stop());
            behaviorCoordinator.setStateChangedCallback(newState -> behaviorMessager.submitMessage(CurrentState, newState));
            behaviorCoordinator.setDebrisDetectedCallback(() -> behaviorMessager.submitMessage(DebrisDetected, true));
            behaviorCoordinator.setStairsDetectedCallback(() -> behaviorMessager.submitMessage(StairsDetected, true));
            behaviorCoordinator.setDoorDetectedCallback(() -> behaviorMessager.submitMessage(DoorDetected, true));
            behaviorMessager.registerTopicListener(IgnoreDebris, ignore -> behaviorCoordinator.ignoreDebris());
            behaviorMessager.registerTopicListener(ConfirmDoor, confirm -> behaviorCoordinator.proceedWithDoorBehavior());

            primaryStage.setTitle(AtlasBuildingExplorationBehaviorUI.class.getSimpleName());
            BorderPane mainPane = new BorderPane();

            View3DFactory view3dFactory = View3DFactory.createSubscene();
            view3dFactory.addCameraController(true);
            view3dFactory.addDefaultLighting();

            Pane subScene = view3dFactory.getSubSceneWrappedInsidePane();

            JavaFXCoordinateSystem worldCoordinateSystem = new JavaFXCoordinateSystem(0.3);
            worldCoordinateSystem.setMouseTransparent(true);
            view3dFactory.addNodeToView(worldCoordinateSystem);

            BuildingExplorationBehaviorUI ui = new BuildingExplorationBehaviorUI(view3dFactory.getSubScene(), null, robotModel, ros2Node, behaviorMessager);

            view3dFactory.addNodeToView(ui.get3DGroup());

            mainPane.setCenter(subScene);
            mainPane.setBottom(ui.getPane());

            Scene mainScene = new Scene(mainPane);
            primaryStage.setScene(mainScene);
            primaryStage.setWidth(1400);
            primaryStage.setHeight(950);
            primaryStage.setOnCloseRequest(event -> ui.destroy());

            primaryStage.show();

         }, DefaultExceptionHandler.RUNTIME_EXCEPTION);
      });
   }

   public static void main(String[] args)
   {
      AtlasBuildingExplorationBehaviorUI.start();
   }
}
