package us.ihmc.rdx.ui.behavior.editor.actions;

import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import us.ihmc.avatar.arm.PresetArmConfiguration;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.behaviors.sequence.actions.ArmJointAnglesActionDefinition;
import us.ihmc.behaviors.sequence.actions.ArmJointAnglesActionState;
import us.ihmc.rdx.imgui.ImBooleanWrapper;
import us.ihmc.rdx.imgui.ImDoubleWrapper;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.imgui.ImIntegerWrapper;
import us.ihmc.rdx.ui.behavior.editor.RDXBehaviorAction;
import us.ihmc.rdx.ui.behavior.editor.RDXBehaviorActionBasics;

public class RDXArmJointAnglesAction implements RDXBehaviorAction
{
   private final DRCRobotModel robotModel;
   private final ArmJointAnglesActionState state = new ArmJointAnglesActionState();
   private final ArmJointAnglesActionDefinition definition = state.getDefinition();
   private final RDXBehaviorActionBasics rdxActionBasics = new RDXBehaviorActionBasics(this);
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImIntegerWrapper sideWidget = new ImIntegerWrapper(definition::getSide, definition::setSide, labels.get("Side"));
   private final String[] configurations = new String[PresetArmConfiguration.values().length + 1];
   private final ImInt currentConfiguration = new ImInt(PresetArmConfiguration.HOME.ordinal() + 1);
   private final ImDoubleWrapper[] jointAngleWidgets = new ImDoubleWrapper[ArmJointAnglesActionDefinition.NUMBER_OF_JOINTS];
   private final ImDoubleWrapper trajectoryDurationWidget = new ImDoubleWrapper(definition::getTrajectoryDuration,
                                                                                definition::setTrajectoryDuration,
                                                                                imDouble -> ImGui.inputDouble(labels.get("Trajectory duration"), imDouble));
   public RDXArmJointAnglesAction(DRCRobotModel robotModel)
   {
      this.robotModel = robotModel;
      int c = 0;
      configurations[c++] = ArmJointAnglesActionDefinition.CUSTOM_ANGLES_NAME;
      for (PresetArmConfiguration preset : PresetArmConfiguration.values())
      {
         configurations[c++] = preset.name();
      }

      for (int i = 0; i < ArmJointAnglesActionDefinition.NUMBER_OF_JOINTS; i++)
      {
         int jointIndex = i;
         jointAngleWidgets[i] = new ImDoubleWrapper(() -> definition.getJointAngles()[jointIndex],
                                                    jointAngle -> definition.getJointAngles()[jointIndex] = jointAngle,
                                                    imDouble -> ImGui.inputDouble(labels.get("j" + jointIndex), imDouble));
      }
   }

   @Override
   public void update(boolean concurrentActionIsNextForExecution)
   {
      definition.update();

      PresetArmConfiguration preset = definition.getPreset();
      currentConfiguration.set(preset == null ? 0 : preset.ordinal() + 1);

      // Copy the preset values into the custom data fields so they can be tweaked
      // relatively when switching to custom angles.
      if (preset != null)
         robotModel.getPresetArmConfiguration(definition.getSide(), preset, definition.getJointAngles());
   }

   @Override
   public void renderImGuiWidgets()
   {
      rdxActionBasics.renderImGuiWidgets();
   }

   @Override
   public void renderImGuiSettingWidgets()
   {
      ImGui.pushItemWidth(100.0f);
      sideWidget.renderImGuiWidget();
      ImGui.popItemWidth();
      ImGui.pushItemWidth(80.0f);
      trajectoryDurationWidget.renderImGuiWidget();
      ImGui.popItemWidth();

      ImGui.pushItemWidth(200.0f);
      if (ImGui.combo(labels.get("Configuration"), currentConfiguration, configurations))
         definition.setPreset(currentConfiguration.get() == 0 ? null : PresetArmConfiguration.values()[currentConfiguration.get() - 1]);
      ImGui.popItemWidth();

      if (definition.getPreset() == null)
      {
         ImGui.pushItemWidth(80.0f);
         for (int i = 0; i < ArmJointAnglesActionDefinition.NUMBER_OF_JOINTS; i++)
         {
            jointAngleWidgets[i].renderImGuiWidget();
         }
         ImGui.popItemWidth();
      }
   }

   @Override
   public String getActionTypeTitle()
   {
      return "Arm Joint Angles";
   }

   @Override
   public ImBooleanWrapper getSelected()
   {
      return rdxActionBasics.getSelected();
   }

   @Override
   public ImBoolean getExpanded()
   {
      return rdxActionBasics.getExpanded();
   }

   @Override
   public ImString getImDescription()
   {
      return rdxActionBasics.getDescription();
   }

   @Override
   public ImString getRejectionTooltip()
   {
      return rdxActionBasics.getRejectionTooltip();
   }

   @Override
   public int getActionIndex()
   {
      return rdxActionBasics.getActionIndex();
   }

   @Override
   public void setActionIndex(int actionIndex)
   {
      rdxActionBasics.setActionIndex(actionIndex);
   }

   @Override
   public int getActionNextExecutionIndex()
   {
      return rdxActionBasics.getActionNextExecutionIndex();
   }

   @Override
   public void setActionNextExecutionIndex(int actionNextExecutionIndex)
   {
      rdxActionBasics.setActionNextExecutionIndex(actionNextExecutionIndex);
   }

   @Override
   public ArmJointAnglesActionState getState()
   {
      return state;
   }

   public ArmJointAnglesActionDefinition getDefinition()
   {
      return definition;
   }
}
