package us.ihmc.rdx.ui.vr;

import imgui.ImGui;
import imgui.type.ImBoolean;
import org.lwjgl.openvr.InputDigitalActionData;
import toolbox_msgs.msg.dds.KinematicsToolboxOutputStatus;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.behaviors.sharedControl.ProMPAssistant;
import us.ihmc.behaviors.sharedControl.TeleoperationAssistant;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.log.LogTools;
import us.ihmc.mecano.multiBodySystem.interfaces.OneDoFJointBasics;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.perception.RDXObjectDetector;
import us.ihmc.rdx.ui.graphics.RDXMultiBodyGraphic;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotModels.FullRobotModelUtils;
import us.ihmc.scs2.definition.robot.RobotDefinition;
import us.ihmc.scs2.definition.visual.ColorDefinitions;
import us.ihmc.scs2.definition.visual.MaterialDefinition;

public class RDXVRSharedControl implements TeleoperationAssistant
{
   private ImBoolean enabledReplay;
   private ImBoolean enabledIKStreaming;
   private final ImBoolean enabled = new ImBoolean(false);
   private final ProMPAssistant proMPAssistant = new ProMPAssistant();
   private RDXObjectDetector objectDetector;
   private String objectName = "";
   private FramePose3D objectPose;
   private boolean previewValidated = false;
   private FullHumanoidRobotModel ghostRobotModel;
   private RDXMultiBodyGraphic ghostRobotGraphic;
   private OneDoFJointBasics[] ghostOneDoFJointsExcludingHands;
   private boolean previewSetToActive = true; // once the validated motion is executed and preview disabled, activate ghostRobotGraphic based on this
   private KinematicsToolboxOutputStatus statusBeforeAssistance;
   private boolean assistanceJustStarted = false;
   private boolean restartedMotion = false;

   public RDXVRSharedControl(DRCRobotModel robotModel, ImBoolean enabledIKStreaming, ImBoolean enabledReplay)
   {
      this.enabledIKStreaming = enabledIKStreaming;
      this.enabledReplay = enabledReplay;

      // create ghost robot for assistance preview
      RobotDefinition ghostRobotDefinition = new RobotDefinition(robotModel.getRobotDefinition());
      MaterialDefinition material = new MaterialDefinition(ColorDefinitions.parse("#9370DB").derive(0.0, 1.0, 1.0, 0.5));
      RobotDefinition.forEachRigidBodyDefinition(ghostRobotDefinition.getRootBodyDefinition(),
                                                 body -> body.getVisualDefinitions().forEach(visual -> visual.setMaterialDefinition(material)));

      ghostRobotModel = robotModel.createFullRobotModel();
      ghostOneDoFJointsExcludingHands = FullRobotModelUtils.getAllJointsExcludingHands(ghostRobotModel);
      ghostRobotGraphic = new RDXMultiBodyGraphic(robotModel.getSimpleRobotName() + " (Assistance Preview Ghost)");
      ghostRobotGraphic.loadRobotModelAndGraphics(ghostRobotDefinition, ghostRobotModel.getElevator());
      ghostRobotGraphic.setActive(true);
      ghostRobotGraphic.create();
   }

   public void processInput(InputDigitalActionData triggerButton)
   {
      // enable if trigger button has been pressed once. if button is pressed again shared control is stopped
      if (triggerButton.bChanged() && !triggerButton.bState())
      {
         setEnabled(!enabled.get());
      }
   }

   public void updatePreviewModel(KinematicsToolboxOutputStatus status)
   {
      ghostRobotModel.getRootJoint().setJointPosition(status.getDesiredRootPosition());
      ghostRobotModel.getRootJoint().setJointOrientation(status.getDesiredRootOrientation());
      for (int i = 0; i < ghostOneDoFJointsExcludingHands.length; i++)
      {
         ghostOneDoFJointsExcludingHands[i].setQ(status.getDesiredJointAngles().get(i));
      }
      ghostRobotModel.getElevator().updateFramesRecursively();
   }

   @Override
   public void processFrameInformation(Pose3DReadOnly observedPose, String bodyPart)
   {
      proMPAssistant.processFrameAndObjectInformation(observedPose, bodyPart, objectPose, objectName);

      if (previewSetToActive)
      {
         ghostRobotGraphic.setActive(false); // do not show ghost robot since there is no preview available yet
         assistanceJustStarted = false;
      }
   }

   @Override
   public boolean readyToPack()
   {
      return proMPAssistant.readyToPack();
   }

   @Override
   public void framePoseToPack(FramePose3D framePose, String bodyPart)
   {
      proMPAssistant.framePoseToPack(framePose, bodyPart);

      if (previewSetToActive && !previewValidated)  // preview active but not validated yet
      {
         ghostRobotGraphic.setActive(true); // show ghost robot of preview
         if (proMPAssistant.isCurrentTaskDone()) // if motion is over and not validated yet, replay it
         {
            proMPAssistant.setStartTrajectories(0);
            restartedMotion = true;
         }
         else
            restartedMotion = false;
         if (enabledIKStreaming.get()) // if streaming to controller has been activated again, it means the user validated the motion
         {
            ghostRobotGraphic.setActive(false); // stop displaying preview ghost robot
            previewValidated = true;
            proMPAssistant.setStartTrajectories(0);
            restartedMotion = true;
         }
      }
      else if(!previewSetToActive || previewValidated) // if user did not use the preview or preview has been validated
      {
         restartedMotion = false;
         if (proMPAssistant.isCurrentTaskDone())  // do not want the assistant to keep recomputing trajectories for the same task over and over
            setEnabled(false); // exit promp assistance when the current task is over, reactivate it in VR or UI when you want to use it again
      }
   }

   public void renderWidgets(ImGuiUniqueLabelMap labels)
   {
      ImGui.text("Toggle shared control assistance: Left B button");
      if (ImGui.checkbox(labels.get("Shared Control"), enabled))
      {
         setEnabled(enabled.get());
      }
      ghostRobotGraphic.renderImGuiWidgets();
   }

   public void destroy()
   {
      ghostRobotGraphic.destroy();
   }

   private void setEnabled(boolean enabled)
   {
      if (enabled != this.enabled.get())
      {
         this.enabled.set(enabled);
         if (enabled)
         {
            assistanceJustStarted = true;

            // store detected object name and pose
            if (objectDetector != null && objectDetector.isEnabled() && objectDetector.hasDetectedObject())
            {
               objectName = objectDetector.getObjectName();
               objectPose = objectDetector.getObjectPose();
               LogTools.info("Detected object {} pose: {}", objectName, objectPose);
            }

            if (enabledReplay.get())
               this.enabled.set(false); // check no concurrency with replay

            if (!enabledIKStreaming.get() && !isPreviewActive())
               this.enabled.set(false);  // if preview disabled we do not want to start the assistance while we're not streaming to the controller
            else if (isPreviewActive())
               enabledIKStreaming.set(false); // if preview is enabled we do not want to stream to the controller
            previewSetToActive = isPreviewActive();
         }
         else // deactivated
         {
            if (proMPAssistant.readyToPack()) // if the shared control had started to pack frame poses
               enabledIKStreaming.set(false); // stop the ik streaming so that you can reposition according to the robot state to avoid jumps in poses
            // reset promp assistance
            proMPAssistant.reset();
            proMPAssistant.setCurrentTaskDone(false);
            objectName = "";
            objectPose = null;
            assistanceJustStarted = false;
            previewValidated = false;
            ghostRobotGraphic.setActive(previewSetToActive); // set it back to what it was (graphic is disabled when using assistance after validation)
         }
      }
   }

   public RDXMultiBodyGraphic getPreviewGraphic()
   {
      return ghostRobotGraphic;
   }

   public FullHumanoidRobotModel getPreviewModel()
   {
      return ghostRobotModel;
   }

   public boolean isActive()
   {
      return this.enabled.get();
   }

   public boolean isPreviewActive()
   {
      return previewSetToActive;
   }

   public boolean isPreviewGraphicActive()
   {
      return ghostRobotGraphic.isActive();
   }

   public void setObjectDetector(RDXObjectDetector objectDetector)
   {
      this.objectDetector = objectDetector;
   }

   public void setStatusBeforeAssistance(KinematicsToolboxOutputStatus status)
   {
      statusBeforeAssistance = new KinematicsToolboxOutputStatus(status);
   }

   public KinematicsToolboxOutputStatus getStatusBeforeAssistance()
   {
      return statusBeforeAssistance;
   }

   public boolean hasAssistanceJustStarted()
   {
      return assistanceJustStarted;
   }

   public boolean hasMotionRestarted()
   {
      return restartedMotion;
   }
}
