package us.ihmc.rdx.ui.behavior.tree;

import imgui.ImGui;
import us.ihmc.commons.thread.Notification;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;

public class RDXBehaviorTreeFileMenu
{
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final Notification fileMenuShouldClose = new Notification();

   public void renderFileMenu()
   {
      if (ImGui.beginMenu(labels.get("File"), !fileMenuShouldClose.poll()))
      {

         // TODO: Iterate through tree finding nodes that coorespond to JSON files.

         // TODO: Probably some widgets in here that manage whether the selected node
         //   currently cooresponds to a JSON file and the path to the file

         ImGui.endMenu();
      }
   }
}
