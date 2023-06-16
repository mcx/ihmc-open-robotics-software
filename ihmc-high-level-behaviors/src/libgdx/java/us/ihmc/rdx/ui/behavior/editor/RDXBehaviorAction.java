package us.ihmc.rdx.ui.behavior.editor;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import org.apache.commons.lang3.mutable.MutableBoolean;
import us.ihmc.behaviors.sequence.BehaviorActionData;
import us.ihmc.rdx.imgui.ImBooleanWrapper;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.input.ImGui3DViewInput;

/**
 * The UI representation of a robot behavior action. It provides a base
 * template for implementing an interactable action.
 */
public abstract class RDXBehaviorAction
{
   private final MutableBoolean selected = new MutableBoolean();
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImBooleanWrapper selectedWrapper = new ImBooleanWrapper(selected::booleanValue,
                                                                         selected::setValue,
                                                                         imBoolean -> ImGui.checkbox(labels.get("Selected"), imBoolean));
   private final ImBoolean expanded = new ImBoolean(true);
   private final ImString description = new ImString();

   public RDXBehaviorAction()
   {

   }

   public void update()
   {

   }

   public void updateAfterLoading()
   {

   }

   public void calculate3DViewPick(ImGui3DViewInput input)
   {

   }

   public void process3DViewInput(ImGui3DViewInput input)
   {

   }

   public final void renderImGuiWidgets()
   {
      if (expanded.get())
      {
         renderImGuiSettingWidgets();
      }
   }

   public void renderImGuiSettingWidgets()
   {

   }

   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {

   }

   public abstract BehaviorActionData getActionData();

   public ImBooleanWrapper getSelected()
   {
      return selectedWrapper;
   }

   public ImBoolean getExpanded()
   {
      return expanded;
   }

   public abstract String getActionTypeTitle();

   public ImString getDescription()
   {
      return description;
   }
}
