package us.ihmc.rdx.ui.affordances.editor;

import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FramePose3DReadOnly;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.interfaces.Point3DBasics;
import us.ihmc.euclid.yawPitchRoll.YawPitchRoll;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.ui.interactable.RDXInteractableSakeGripper;
import us.ihmc.robotics.referenceFrames.ReferenceFrameMissingTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class RDXAffordanceMirror
{
   private final SideDependentList<RDXInteractableSakeGripper> interactableHands;
   private final SideDependentList<FramePose3D> handPoses;
   private final SideDependentList<RigidBodyTransform> handTransformsToWorld;
   private final RobotSide[] activeSide;
   private final ImBoolean mirrorActive = new ImBoolean(false);
   private final Map<String, Boolean> activeTransformAxisMirror = new LinkedHashMap<>();
   private boolean negatedAxis = false;
   private final RigidBodyTransform frameActiveSideTransform =  new RigidBodyTransform();
   private final ReferenceFrame frameActiveSide = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent(ReferenceFrame.getWorldFrame(),
                                                                                                                         frameActiveSideTransform);
   private final RigidBodyTransform frameNonActiveSideTransform =  new RigidBodyTransform();
   private final ReferenceFrame frameNonActiveSide = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent(ReferenceFrame.getWorldFrame(),
                                                                                                                            frameNonActiveSideTransform);
   private FramePose3DReadOnly lastActivePose = new FramePose3D(frameActiveSide);

   public RDXAffordanceMirror(SideDependentList<RDXInteractableSakeGripper> interactableHands,
                              SideDependentList<RigidBodyTransform> handTransformsToWorld,
                              SideDependentList<FramePose3D> handPoses,
                              RobotSide[] activeSide)
   {
      this.interactableHands = interactableHands;
      this.handPoses = handPoses;
      this.handTransformsToWorld = handTransformsToWorld;
      this.activeSide = activeSide;

      activeTransformAxisMirror.put("X", false);
      activeTransformAxisMirror.put("Y", false);
      activeTransformAxisMirror.put("Z", false);
      activeTransformAxisMirror.put("Yaw", false);
      activeTransformAxisMirror.put("Pitch", false);
      activeTransformAxisMirror.put("Roll", false);
   }

   public void update()
   {
      if (mirrorActive.get())
      {
         RobotSide nonActiveSide = (activeSide[0] == RobotSide.RIGHT ? RobotSide.LEFT : RobotSide.RIGHT);
         FramePose3D activeHandPose = new FramePose3D(handPoses.get(activeSide[0]));
         activeHandPose.changeFrame(frameActiveSide);

         FramePose3D nonActiveHandPose = new FramePose3D(handPoses.get(nonActiveSide));
         nonActiveHandPose.changeFrame(frameNonActiveSide);

         Point3DBasics activeSideTranslationIncrement = new FramePoint3D();
         YawPitchRoll activeSideYawPitchRollIncrement = new YawPitchRoll();
         for (var axis : activeTransformAxisMirror.entrySet())
         {
            if (axis.getValue())
            {
               switch (axis.getKey())
               {
                  case "X" ->
                  {
                     if (negatedAxis)
                        activeSideTranslationIncrement.setX(-activeHandPose.getTranslation().getX() + lastActivePose.getTranslation().getX());
                     else
                        activeSideTranslationIncrement.setX(activeHandPose.getTranslation().getX() - lastActivePose.getTranslation().getX());
                  }
                  case "Y" ->
                  {
                     if (negatedAxis)
                        activeSideTranslationIncrement.setY(-activeHandPose.getTranslation().getY() + lastActivePose.getTranslation().getY());
                     else
                        activeSideTranslationIncrement.setY(activeHandPose.getTranslation().getY() - lastActivePose.getTranslation().getY());
                  }
                  case "Z" ->
                  {
                     if (negatedAxis)
                        activeSideTranslationIncrement.setZ(-activeHandPose.getTranslation().getZ() + lastActivePose.getTranslation().getZ());
                     else
                        activeSideTranslationIncrement.setZ(activeHandPose.getTranslation().getZ() - lastActivePose.getTranslation().getZ());
                  }
                  case "Yaw" ->
                  {
                     if (negatedAxis)
                        activeSideYawPitchRollIncrement.setYaw(-activeHandPose.getYaw() + lastActivePose.getYaw());
                     else
                        activeSideYawPitchRollIncrement.setYaw(activeHandPose.getYaw() - lastActivePose.getYaw());
                  }
                  case "Pitch" ->
                  {
                     if (negatedAxis)
                        activeSideYawPitchRollIncrement.setPitch(-activeHandPose.getPitch() + lastActivePose.getPitch());
                     else
                        activeSideYawPitchRollIncrement.setPitch(activeHandPose.getPitch() - lastActivePose.getPitch());
                  }
                  case "Roll" ->
                  {
                     if (negatedAxis)
                        activeSideYawPitchRollIncrement.setRoll(-activeHandPose.getRoll() + lastActivePose.getRoll());
                     else
                        activeSideYawPitchRollIncrement.setRoll(activeHandPose.getRoll() - lastActivePose.getRoll());
                  }
               }
            }
         }
         RigidBodyTransform nonActiveSideTransform = new RigidBodyTransform(activeSideYawPitchRollIncrement, activeSideTranslationIncrement);
         if (!nonActiveSideTransform.geometricallyEquals(new RigidBodyTransform(), 0.001))
         {
            nonActiveHandPose.applyTransform(nonActiveSideTransform);
            nonActiveHandPose.changeFrame(ReferenceFrame.getWorldFrame());
            handTransformsToWorld.get(nonActiveSide).set(nonActiveHandPose);
            lastActivePose = new FramePose3D(frameActiveSide, activeHandPose);
         }
      }
   }

   public void renderImGuiWidgets(ImGuiUniqueLabelMap labels)
   {
      if (handPoses.containsKey(RobotSide.RIGHT) && handPoses.containsKey(RobotSide.LEFT))
      {
         if (ImGui.checkbox("Mirror Other Hand", mirrorActive))
         {
            if (!mirrorActive.get())
               reset();
         }
         if (mirrorActive.get())
         {
            if (ImGui.button("Set Reference Frame Axis"))
            {
               setReferenceFrameMirror();
            }
            ImGui.text("Transform: ");
            Map<String, Boolean> changedColorTranslationAxisButton = new HashMap<>();
            for (var axisMirror : activeTransformAxisMirror.entrySet())
            {
               changedColorTranslationAxisButton.put(axisMirror.getKey(), false);
               ImGui.sameLine();
               if (axisMirror.getValue())
               {
                  ImGui.pushStyleColor(ImGuiCol.Button, 0.0f, 0.0f, 1.0f, 0.5f);
                  changedColorTranslationAxisButton.replace(axisMirror.getKey(), true);
               }
               if (ImGui.button(labels.get(axisMirror.getKey()) + "##" + "Transform"))
               {
                  axisMirror.setValue(!axisMirror.getValue());
                  setReferenceFrameMirror();
                  if (axisMirror.getValue())
                  {
                     for (var otherAxis : activeTransformAxisMirror.entrySet())
                     {
                        if (!otherAxis.getKey().equals(axisMirror.getKey()))
                        {
                           otherAxis.setValue(false);
                        }
                     }
                  }
               }
               if (changedColorTranslationAxisButton.get(axisMirror.getKey()))
                  ImGui.popStyleColor();
            }
            if (ImGui.button(labels.get("Switch Direction")))
            {
               negatedAxis = !negatedAxis;
            }
         }

         ImGui.separator();
      }
      else
      {
         reset();
      }
   }

   public void reset()
   {
      mirrorActive.set(false);
      lastActivePose = new FramePose3D(frameActiveSide, new RigidBodyTransform());
      for (var axisMirror : activeTransformAxisMirror.entrySet())
         axisMirror.setValue(false);
   }

   private void setReferenceFrameMirror()
   {
      RobotSide nonActiveSide = (activeSide[0] == RobotSide.RIGHT ? RobotSide.LEFT : RobotSide.RIGHT);
      frameActiveSideTransform.set((interactableHands.get(activeSide[0]).getReferenceFrameHand().getTransformToWorldFrame()));
      frameActiveSide.update();
      frameNonActiveSideTransform.set((interactableHands.get(nonActiveSide).getReferenceFrameHand().getTransformToWorldFrame()));
      frameNonActiveSide.update();

      lastActivePose = new FramePose3D(frameActiveSide, new RigidBodyTransform());
   }
}
