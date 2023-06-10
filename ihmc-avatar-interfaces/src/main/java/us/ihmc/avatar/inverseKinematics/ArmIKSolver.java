package us.ihmc.avatar.inverseKinematics;

import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.networkProcessor.kinematicsToolboxModule.KinematicsToolboxOptimizationSettings;
import us.ihmc.commonWalkingControlModules.configurations.JointPrivilegedConfigurationParameters;
import us.ihmc.commonWalkingControlModules.controllerCore.FeedbackControllerTemplate;
import us.ihmc.commonWalkingControlModules.controllerCore.WholeBodyControlCoreToolbox;
import us.ihmc.commonWalkingControlModules.controllerCore.WholeBodyControllerCore;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.JointTorqueSoftLimitWeightCalculator;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.log.LogTools;
import us.ihmc.mecano.multiBodySystem.RigidBody;
import us.ihmc.mecano.multiBodySystem.SixDoFJoint;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.mecano.multiBodySystem.iterators.SubtreeStreams;
import us.ihmc.mecano.tools.MultiBodySystemTools;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.MultiBodySystemMissingTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputList;
import us.ihmc.yoVariables.registry.YoRegistry;

import java.util.ArrayList;
import java.util.List;

public class ArmIKSolver
{
   /** The gains of the solver depend on this dt, it shouldn't change based on the actual thread scheduled period. */
   public static final double CONTROL_DT = 0.001;
   public static final double GRAVITY = 9.81;

   private final YoRegistry registry = new YoRegistry(getClass().getSimpleName());
   // TODO: Mess with these settings
   private final KinematicsToolboxOptimizationSettings optimizationSettings = new KinematicsToolboxOptimizationSettings();

   public ArmIKSolver(RobotSide side,
                      DRCRobotModel robotModel,
                      FullHumanoidRobotModel syncedRobot,
                      FullHumanoidRobotModel desiredRobot,
                      ReferenceFrame handControlDesiredFrame)
   {
      RigidBodyBasics syncedChest = syncedRobot.getChest();
      OneDoFJointBasics syncedFirstArmJoint = syncedRobot.getArmJoint(side, robotModel.getJointMap().getArmJointNames()[0]);

      RigidBody detachedArmOnlyRobot = MultiBodySystemMissingTools.getDetachedCopyOfSubtree(syncedChest, syncedFirstArmJoint);
      SixDoFJoint rootSixDoFJoint = (SixDoFJoint) detachedArmOnlyRobot.getChildrenJoints().get(0);

      // Remove fingers
      RigidBodyBasics hand = MultiBodySystemTools.findRigidBody(detachedArmOnlyRobot, robotModel.getJointMap().getHandName(side));
      hand.getChildrenJoints().clear();

      RigidBodyBasics chest = MultiBodySystemTools.findRigidBody(detachedArmOnlyRobot, robotModel.getJointMap().getChestName());

      JointBasics[] controlledJoints = MultiBodySystemTools.createJointPath(detachedArmOnlyRobot, hand);


      ReferenceFrame centerOfMassFrame = chest.getBodyFixedFrame(); // TODO: FIXME
      YoGraphicsListRegistry yoGraphicsListRegistry = null; // opt out
      WholeBodyControlCoreToolbox toolbox = new WholeBodyControlCoreToolbox(CONTROL_DT,
                                                                            GRAVITY,
                                                                            rootSixDoFJoint,
                                                                            controlledJoints,
                                                                            centerOfMassFrame,
                                                                            optimizationSettings,
                                                                            yoGraphicsListRegistry,
                                                                            registry);
      toolbox.setJointPrivilegedConfigurationParameters(new JointPrivilegedConfigurationParameters());
      JointTorqueSoftLimitWeightCalculator jointTorqueMinimizationWeightCalculator = new JointTorqueSoftLimitWeightCalculator(toolbox.getJointIndexHandler());
      jointTorqueMinimizationWeightCalculator.setParameters(0.0, 0.001, 0.10);
      toolbox.setupForInverseKinematicsSolver(jointTorqueMinimizationWeightCalculator);

      List<RigidBodyBasics> controllableRigidBodies = new ArrayList<>();
      controllableRigidBodies.add(chest);
      controllableRigidBodies.add(hand);

      OneDoFJointBasics[] oneDoFJoints = (OneDoFJointBasics[]) MultiBodySystemTools.createJointPath(chest, hand);

      FeedbackControllerTemplate controllerCoreTemplate = new FeedbackControllerTemplate();
      controllerCoreTemplate.setAllowDynamicControllerConstruction(true);
      controllerCoreTemplate.enableCenterOfMassFeedbackController();

      int numberOfControllersPerBody = 1;
      controllableRigidBodies.stream().forEach(rigidBody -> controllerCoreTemplate.enableSpatialFeedbackController(rigidBody, numberOfControllersPerBody));

      SubtreeStreams.fromChildren(OneDoFJointBasics.class, chest).forEach(controllerCoreTemplate::enableOneDoFJointFeedbackController);

      JointDesiredOutputList lowLevelControllerOutput = new JointDesiredOutputList(oneDoFJoints);
      WholeBodyControllerCore wholeBodyControllerCore =  new WholeBodyControllerCore(toolbox, controllerCoreTemplate, lowLevelControllerOutput, registry);

      LogTools.info("Created WBCC!");
   }
}
