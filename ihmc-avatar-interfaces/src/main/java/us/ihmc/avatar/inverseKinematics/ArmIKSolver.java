package us.ihmc.avatar.inverseKinematics;

import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.networkProcessor.kinematicsToolboxModule.KinematicsSolutionQualityCalculator;
import us.ihmc.avatar.networkProcessor.kinematicsToolboxModule.KinematicsToolboxOptimizationSettings;
import us.ihmc.commonWalkingControlModules.configurations.JointPrivilegedConfigurationParameters;
import us.ihmc.commonWalkingControlModules.controllerCore.*;
import us.ihmc.commonWalkingControlModules.controllerCore.command.ControllerCoreCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.ControllerCoreOutput;
import us.ihmc.commonWalkingControlModules.controllerCore.command.feedbackController.SpatialFeedbackControlCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.InverseKinematicsOptimizationSettingsCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.PrivilegedConfigurationCommand;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.JointTorqueSoftLimitWeightCalculator;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.log.LogTools;
import us.ihmc.mecano.frames.CenterOfMassReferenceFrame;
import us.ihmc.mecano.multiBodySystem.SixDoFJoint;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.mecano.spatial.SpatialVector;
import us.ihmc.mecano.spatial.interfaces.SpatialVectorReadOnly;
import us.ihmc.mecano.tools.MultiBodySystemTools;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotModels.FullRobotModelUtils;
import us.ihmc.robotics.MultiBodySystemMissingTools;
import us.ihmc.robotics.controllers.pidGains.implementations.DefaultPIDSE3Gains;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.SelectionMatrix6D;
import us.ihmc.robotics.weightMatrices.WeightMatrix6D;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputList;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputListReadOnly;
import us.ihmc.sensorProcessing.outputData.JointDesiredOutputReadOnly;
import us.ihmc.wholeBodyController.HandTransformTools;
import us.ihmc.yoVariables.registry.YoRegistry;

/**
 * An attempt to use the WholeBodyControllerCore directly to get IK solutions for
 * a humanoid robot arm.
 *
 * TODO:
 *   - Collision constraints
 *
 * @author Duncan Calvert
 */
public class ArmIKSolver
{
   /** The gains of the solver depend on this dt, it shouldn't change based on the actual thread scheduled period. */
   public static final double CONTROL_DT = 0.001;
   public static final double GRAVITY = 9.81;
   private static final int INVERSE_KINEMATICS_CALCULATIONS_PER_UPDATE = 10;

   private final YoRegistry registry = new YoRegistry(getClass().getSimpleName());
   private final ReferenceFrame armWorldFrame = ReferenceFrame.getWorldFrame();
   private final RigidBodyBasics workChest;
   private final RigidBodyBasics workHand;
   // TODO: Mess with these settings
   private final KinematicsToolboxOptimizationSettings optimizationSettings = new KinematicsToolboxOptimizationSettings();
   private final InverseKinematicsOptimizationSettingsCommand activeOptimizationSettings = new InverseKinematicsOptimizationSettingsCommand();
   private final PrivilegedConfigurationCommand privilegedConfigurationCommand = new PrivilegedConfigurationCommand();
   private final WholeBodyControllerCore controllerCore;
   private final SpatialFeedbackControlCommand spatialFeedbackControlCommand = new SpatialFeedbackControlCommand();
   private final ControllerCoreCommand controllerCoreCommand = new ControllerCoreCommand();
   private final DefaultPIDSE3Gains gains = new DefaultPIDSE3Gains();
   private final SelectionMatrix6D selectionMatrix = new SelectionMatrix6D();
   private final WeightMatrix6D weightMatrix = new WeightMatrix6D();
   private final ReferenceFrame handControlDesiredFrame;
   private final FramePose3D handControlDesiredPose = new FramePose3D();
   private final FramePose3D lastHandControlDesiredPose = new FramePose3D();
   private final RigidBodyTransform handControlDesiredPoseToChestCoMTransform = new RigidBodyTransform();
   private final SpatialVectorReadOnly zeroVector6D = new SpatialVector(armWorldFrame);
   private final FramePose3D controlFramePose = new FramePose3D();
   private final OneDoFJointBasics[] syncedOneDoFJoints;
   private final OneDoFJointBasics[] workingOneDoFJoints;
   private final OneDoFJointBasics[] solutionOneDoFJoints;
   private final KinematicsSolutionQualityCalculator solutionQualityCalculator = new KinematicsSolutionQualityCalculator();
   private final FeedbackControllerDataHolderReadOnly feedbackControllerDataHolder;
   private final RigidBodyBasics syncedChest;

   public ArmIKSolver(RobotSide side,
                      DRCRobotModel robotModel,
                      FullHumanoidRobotModel syncedRobot,
                      FullHumanoidRobotModel solutionRobot,
                      ReferenceFrame handControlDesiredFrame)
   {
      this.handControlDesiredFrame = handControlDesiredFrame;
      syncedChest = syncedRobot.getChest();
      OneDoFJointBasics syncedFirstArmJoint = syncedRobot.getArmJoint(side, robotModel.getJointMap().getArmJointNames()[0]);
      syncedOneDoFJoints = FullRobotModelUtils.getArmJoints(syncedRobot, side, robotModel.getJointMap().getArmJointNames());
      solutionOneDoFJoints = FullRobotModelUtils.getArmJoints(solutionRobot, side, robotModel.getJointMap().getArmJointNames());

      // We clone a detached chest and single arm for the WBCC to work with. We just want to find arm joint angles.
      workChest = MultiBodySystemMissingTools.getDetachedCopyOfSubtree(syncedChest, armWorldFrame, syncedFirstArmJoint);

      // Remove fingers
      workHand = MultiBodySystemTools.findRigidBody(workChest, robotModel.getJointMap().getHandName(side));
      workHand.getChildrenJoints().clear();

      // Set the control frame pose to our palm centered control frame.
      // The spatial feedback command wants this relative to the fixed CoM of the hand link.
      RigidBodyTransform handControlToBodyFixedCoMFrameTransform = new RigidBodyTransform();
      HandTransformTools.getHandControlToBodyFixedCoMFrameTransform(syncedRobot, side, handControlToBodyFixedCoMFrameTransform);
      controlFramePose.setToZero(workHand.getBodyFixedFrame());
      controlFramePose.set(handControlToBodyFixedCoMFrameTransform);

      workingOneDoFJoints = MultiBodySystemMissingTools.getSubtreeJointArray(OneDoFJointBasics.class, workChest);

      SixDoFJoint rootSixDoFJoint = null; // don't need this for fixed base single limb IK
      CenterOfMassReferenceFrame centerOfMassFrame = null; // we don't need this
      YoGraphicsListRegistry yoGraphicsListRegistry = null; // opt out
      WholeBodyControlCoreToolbox toolbox = new WholeBodyControlCoreToolbox(CONTROL_DT,
                                                                            GRAVITY,
                                                                            rootSixDoFJoint,
                                                                            workingOneDoFJoints,
                                                                            centerOfMassFrame,
                                                                            optimizationSettings,
                                                                            yoGraphicsListRegistry,
                                                                            registry);

      JointPrivilegedConfigurationParameters jointPrivilegedConfigurationParameters = new JointPrivilegedConfigurationParameters();
      toolbox.setJointPrivilegedConfigurationParameters(jointPrivilegedConfigurationParameters);

      JointTorqueSoftLimitWeightCalculator jointTorqueMinimizationWeightCalculator = new JointTorqueSoftLimitWeightCalculator(toolbox.getJointIndexHandler());
      jointTorqueMinimizationWeightCalculator.setParameters(0.0, 0.001, 0.10);
      toolbox.setupForInverseKinematicsSolver(jointTorqueMinimizationWeightCalculator);

      // No reason to preallocate our feedback controllers here
      FeedbackControllerTemplate controllerCoreTemplate = new FeedbackControllerTemplate();
      controllerCoreTemplate.setAllowDynamicControllerConstruction(true);

      JointDesiredOutputList lowLevelControllerOutput = new JointDesiredOutputList(workingOneDoFJoints);

      controllerCore = new WholeBodyControllerCore(toolbox, controllerCoreTemplate, lowLevelControllerOutput, registry);

      feedbackControllerDataHolder = controllerCore.getWholeBodyFeedbackControllerDataHolder();

      controllerCoreCommand.setControllerCoreMode(WholeBodyControllerCoreMode.INVERSE_KINEMATICS);

      double gain = 1200.0;
      double weight = 20.0;
      gains.setPositionProportionalGains(gain);
      gains.setOrientationProportionalGains(gain);
      weightMatrix.setLinearWeights(weight, weight, weight);
      weightMatrix.setAngularWeights(weight, weight, weight);

      selectionMatrix.setLinearAxisSelection(true, true, true);
      selectionMatrix.setAngularAxisSelection(true, true, true);

      privilegedConfigurationCommand.setDefaultWeight(0.025);
      privilegedConfigurationCommand.setDefaultConfigurationGain(50.0);

      for (OneDoFJointBasics workingOneDoFJoint : workingOneDoFJoints)
      {
         // Try to keep the elbow bent like a human instead of backwards
         privilegedConfigurationCommand.addJoint(workingOneDoFJoint, workingOneDoFJoint.getName().contains("ELBOW") ? 1.04 : 0.0);
      }
   }

   public void copyActualToWork()
   {
      MultiBodySystemMissingTools.copyOneDoFJointsConfiguration(syncedOneDoFJoints, workingOneDoFJoints);
   }

   public void update()
   {
      // since this is temporaririly modifying the desired pose and it's passed
      // to the WBCC command on another thread below, we need to synchronize.
      synchronized (handControlDesiredPose)
      {
         // Get the hand desired pose, but put it in the world of the detached arm
         handControlDesiredPose.setToZero(handControlDesiredFrame);
         handControlDesiredPose.changeFrame(syncedChest.getParentJoint().getFrameAfterJoint());
         handControlDesiredPose.get(handControlDesiredPoseToChestCoMTransform);

         // The world of the arm is at the chest root (after parent joint), but the solver solves w.r.t. the chest fixed CoM frame
         workChest.getBodyFixedFrame().getTransformToParent().transform(handControlDesiredPoseToChestCoMTransform);

         handControlDesiredPose.setToZero(armWorldFrame);
         handControlDesiredPose.set(handControlDesiredPoseToChestCoMTransform);
      }
   }

   public boolean getDesiredHandControlPoseChanged()
   {
      boolean desiredHandControlPoseChanged = !handControlDesiredPose.geometricallyEquals(lastHandControlDesiredPose, 0.0001);
      lastHandControlDesiredPose.setIncludingFrame(handControlDesiredPose);
      return desiredHandControlPoseChanged;
   }

   public void solve()
   {
      copyActualToWork();
      workChest.updateFramesRecursively();

      spatialFeedbackControlCommand.set(workChest, workHand);
      spatialFeedbackControlCommand.setGains(gains);
      spatialFeedbackControlCommand.setSelectionMatrix(selectionMatrix);
      spatialFeedbackControlCommand.setWeightMatrixForSolver(weightMatrix);
      synchronized (handControlDesiredPose)
      {
         spatialFeedbackControlCommand.setInverseKinematics(handControlDesiredPose, zeroVector6D);
      }
      spatialFeedbackControlCommand.setControlFrameFixedInEndEffector(controlFramePose);

      for (int i = 0; i < INVERSE_KINEMATICS_CALCULATIONS_PER_UPDATE; i++)
      {
         controllerCoreCommand.clear();
         controllerCoreCommand.addInverseKinematicsCommand(activeOptimizationSettings);
         controllerCoreCommand.addInverseKinematicsCommand(privilegedConfigurationCommand);
         controllerCoreCommand.addFeedbackControlCommand(spatialFeedbackControlCommand);

         controllerCore.compute(controllerCoreCommand);

         ControllerCoreOutput controllerCoreOutput = controllerCore.getControllerCoreOutput();
         JointDesiredOutputListReadOnly output = controllerCoreOutput.getLowLevelOneDoFJointDesiredDataHolder();
         for (int j = 0; j < workingOneDoFJoints.length; j++)
         {
            if (output.hasDataForJoint(workingOneDoFJoints[j]))
            {
               JointDesiredOutputReadOnly jointDesiredOutput = output.getJointDesiredOutput(workingOneDoFJoints[j]);
               workingOneDoFJoints[j].setQ(jointDesiredOutput.getDesiredPosition());
               workingOneDoFJoints[j].setQd(jointDesiredOutput.getDesiredVelocity());
               if (jointDesiredOutput.hasDesiredTorque())
               {
                  workingOneDoFJoints[j].setTau(jointDesiredOutput.getDesiredTorque());
               }
            }
         }
         workChest.updateFramesRecursively();
      }

      double totalRobotMass = 0.0; // We don't need this parameter
      double quality = solutionQualityCalculator.calculateSolutionQuality(feedbackControllerDataHolder, totalRobotMass, 1.0);
      if (quality > 1.0)
      {
         LogTools.debug("Bad quality solution: {} Try upping the gains, giving more iteration, or setting a more acheivable goal.", quality);
      }
   }

   public void copyWorkToDesired()
   {
      MultiBodySystemMissingTools.copyOneDoFJointsConfiguration(workingOneDoFJoints, solutionOneDoFJoints);
   }

   public void setDesiredToCurrent()
   {
      MultiBodySystemMissingTools.copyOneDoFJointsConfiguration(syncedOneDoFJoints, solutionOneDoFJoints);
   }
}
