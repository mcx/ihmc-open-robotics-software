package us.ihmc.rdx.ui.behavior.actions;

import imgui.ImGui;
import us.ihmc.behaviors.sequence.actions.HandWrenchActionDefinition;
import us.ihmc.behaviors.sequence.actions.HandWrenchActionState;
import us.ihmc.rdx.imgui.ImDoubleWrapper;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.ui.behavior.sequence.RDXBehaviorAction;
import us.ihmc.rdx.ui.behavior.sequence.RDXBehaviorActionSequenceEditor;

public class RDXHandWrenchAction extends RDXBehaviorAction<HandWrenchActionState, HandWrenchActionDefinition>
{
   private final HandWrenchActionState state;
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImDoubleWrapper trajectoryDurationWidget;
   private final ImDoubleWrapper forceWidget;

   public RDXHandWrenchAction(long id, RDXBehaviorActionSequenceEditor editor)
   {
      super(editor);

      state = new HandWrenchActionState(id);

      trajectoryDurationWidget = new ImDoubleWrapper(getDefinition()::getTrajectoryDuration,
                                                     getDefinition()::setTrajectoryDuration,
                                                     imDouble -> ImGui.inputDouble(labels.get("Trajectory duration"), imDouble));
      forceWidget = new ImDoubleWrapper(getDefinition()::getForce,
                                        getDefinition()::setForce,
                                        imDouble -> ImGui.inputDouble(labels.get("Force"), imDouble));
   }

   @Override
   protected void renderImGuiWidgetsInternal()
   {
      ImGui.pushItemWidth(80.0f);
      trajectoryDurationWidget.renderImGuiWidget();
      forceWidget.renderImGuiWidget();
      ImGui.popItemWidth();
   }

   @Override
   public String getActionTypeTitle()
   {
      return getDefinition().getSide().getPascalCaseName() + " Hand Wrench";
   }

   @Override
   public HandWrenchActionState getState()
   {
      return state;
   }
}
