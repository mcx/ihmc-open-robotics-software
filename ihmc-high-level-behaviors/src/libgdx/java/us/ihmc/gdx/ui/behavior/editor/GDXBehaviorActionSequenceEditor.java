package us.ihmc.gdx.ui.behavior.editor;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.ImGui;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.ROS2SyncedRobotModel;
import us.ihmc.avatar.networkProcessor.footstepPlanningModule.FootstepPlanningModuleLauncher;
import us.ihmc.avatar.ros2.ROS2ControllerHelper;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.footstepPlanning.FootstepPlanningModule;
import us.ihmc.gdx.FocusBasedGDXCamera;
import us.ihmc.gdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.gdx.input.ImGui3DViewInput;
import us.ihmc.ros2.ROS2Node;

import java.util.LinkedList;

public class GDXBehaviorActionSequenceEditor
{
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final LinkedList<GDXWalkAction> actionSequence = new LinkedList<>();
   private FocusBasedGDXCamera camera3D;
   private DRCRobotModel robotModel;
   private int playbackNextIndex = 0;
   private FootstepPlanningModule footstepPlanner;
   private ROS2SyncedRobotModel syncedRobot;
   private ROS2ControllerHelper ros2ControllerHelper;

   public void create(FocusBasedGDXCamera camera3D, DRCRobotModel robotModel, ROS2Node ros2Node, ROS2SyncedRobotModel syncedRobot)
   {
      this.camera3D = camera3D;
      this.robotModel = robotModel;
      footstepPlanner = FootstepPlanningModuleLauncher.createModule(robotModel);
      this.syncedRobot = syncedRobot;
      ros2ControllerHelper = new ROS2ControllerHelper(ros2Node, robotModel);
   }

   public void process3DViewInput(ImGui3DViewInput input)
   {
      for (GDXWalkAction action : actionSequence)
      {
         action.process3DViewInput(input);
      }
   }

   public void renderImGuiWidgets()
   {
      if (ImGui.button(labels.get("<")))
      {
         if (playbackNextIndex > 0)
            playbackNextIndex--;
      }
      ImGui.sameLine();
      if (playbackNextIndex < actionSequence.size())
      {
         ImGui.text("Index: " + playbackNextIndex);
         ImGui.sameLine();
         if (ImGui.button(labels.get("Execute")))
         {
            actionSequence.get(playbackNextIndex).walk(ReferenceFrame.getWorldFrame(), ros2ControllerHelper, syncedRobot);
            playbackNextIndex++;
         }
      }
      else
      {
         ImGui.text("No actions left.");
      }
      ImGui.sameLine();
      if (ImGui.button(labels.get(">")))
      {
         if (playbackNextIndex < actionSequence.size())
            playbackNextIndex++;
      }

      ImGui.separator();

      for (int i = 0; i < actionSequence.size(); i++)
      {
         ImGui.text("Action " + i);
         ImGui.sameLine();
         if (ImGui.button(labels.get("X", i)))
         {
            GDXWalkAction removedAction = actionSequence.remove(i);
            removedAction.destroy();
         }
      }

      if (ImGui.button(labels.get("Add Walk")))
      {
         GDXWalkAction walkAction = new GDXWalkAction();
         walkAction.create(camera3D, robotModel, footstepPlanner);
         actionSequence.addLast(walkAction);
      }
   }

   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      for (GDXWalkAction action : actionSequence)
      {
         action.getRenderables(renderables, pool);
      }
   }
}
