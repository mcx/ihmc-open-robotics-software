package us.ihmc.rdx.ui.behavior.editor;

import behavior_msgs.msg.dds.*;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import imgui.ImGui;
import imgui.type.ImBoolean;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.tuple.MutablePair;
import std_msgs.msg.dds.Bool;
import std_msgs.msg.dds.Empty;
import std_msgs.msg.dds.Int32;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.ROS2SyncedRobotModel;
import us.ihmc.avatar.ros2.ROS2ControllerHelper;
import us.ihmc.behaviors.sequence.BehaviorActionSequence;
import us.ihmc.commons.FormattingTools;
import us.ihmc.communication.IHMCROS2Input;
import us.ihmc.rdx.imgui.ImGuiPanel;
import us.ihmc.rdx.imgui.ImGuiTools;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.input.ImGui3DViewInput;
import us.ihmc.rdx.ui.RDX3DPanel;
import us.ihmc.log.LogTools;
import us.ihmc.rdx.ui.behavior.editor.actions.*;
import us.ihmc.robotics.referenceFrames.ReferenceFrameLibrary;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.tools.io.*;

import java.util.LinkedList;

/**
 * This class is primarily an interactable sequence/list of robot actions.
 * Rendering it all gets kind of intense, it has some functionality to collapse everything
 * into a more simplified view.
 *
 * For example, this class is a panel that would render several hand poses and a walk goal
 * all in a sequence and allow you to run them on the real robot, but also preview and
 * tune them.
 *
 * TODO: Improve usability. Some things that need improving:
 *   - Actions get inserted in a weird place currently, I know I changed this a couple times,
 *     it may need to be dynamic.
 *   - Maybe including separators between the actions would help.
 *   - Icons like a little hand for the hand poses, and feet for walk goal would likely help.
 *     A little clock for the wait actions. etc.
 */
public class RDXBehaviorActionSequenceEditor
{
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private ImGuiPanel panel;
   private final ImBoolean automaticExecution = new ImBoolean(false);
   private String name;
   private final WorkspaceResourceFile workspaceFile;
   private final LinkedList<RDXBehaviorAction> actionSequence = new LinkedList<>();
   private String pascalCasedName;
   private RDX3DPanel panel3D;
   private DRCRobotModel robotModel;
   private ROS2SyncedRobotModel syncedRobot;
   private ReferenceFrameLibrary referenceFrameLibrary;
   private ROS2ControllerHelper ros2;
   private final MutablePair<Integer, Integer> reorderRequest = MutablePair.of(-1, 0);
   private boolean loading = false;
   private volatile long receivedSequenceStatusMessageCount = 0;
   private long receivedStatusMessageCount = 0;
   private int executionNextIndexStatus;
   private final Int32 currentActionIndexCommandMessage = new Int32();
   private IHMCROS2Input<Int32> executionNextIndexStatusSubscription;
   private IHMCROS2Input<Bool> automaticExecutionStatusSubscription;
   private IHMCROS2Input<ActionSequenceUpdateMessage> sequenceStatusSubscription;
   private final Empty manuallyExecuteNextActionMessage = new Empty();
   private final Bool automaticExecutionCommandMessage = new Bool();

   public RDXBehaviorActionSequenceEditor(WorkspaceResourceFile fileToLoadFrom)
   {
      this.workspaceFile = fileToLoadFrom;
      loadNameFromFile();
      afterNameDetermination();
   }

   public RDXBehaviorActionSequenceEditor(String name, WorkspaceResourceDirectory storageDirectory)
   {
      this.name = name;
      afterNameDetermination();
      this.workspaceFile = new WorkspaceResourceFile(storageDirectory, pascalCasedName + ".json");
   }

   public void afterNameDetermination()
   {
      panel = new ImGuiPanel(name + " Behavior Sequence Editor", this::renderImGuiWidgets, false, true);
      pascalCasedName = FormattingTools.titleToPascalCase(name);
   }

   public void create(RDX3DPanel panel3D,
                      DRCRobotModel robotModel,
                      ROS2Node ros2Node,
                      ROS2SyncedRobotModel syncedRobot,
                      ReferenceFrameLibrary referenceFrameLibrary)
   {
      this.panel3D = panel3D;
      this.robotModel = robotModel;
      this.syncedRobot = syncedRobot;
      this.referenceFrameLibrary = referenceFrameLibrary;
      ros2 = new ROS2ControllerHelper(ros2Node, robotModel);

      executionNextIndexStatusSubscription = ros2.subscribe(BehaviorActionSequence.EXECUTION_NEXT_INDEX_STATUS_TOPIC);
      automaticExecutionStatusSubscription = ros2.subscribe(BehaviorActionSequence.AUTOMATIC_EXECUTION_STATUS_TOPIC);
      sequenceStatusSubscription = ros2.subscribe(BehaviorActionSequence.SEQUENCE_STATUS_TOPIC);
      sequenceStatusSubscription.addCallback(message -> ++receivedSequenceStatusMessageCount);
   }

   public void loadNameFromFile()
   {
      JSONFileTools.load(workspaceFile, jsonNode -> name = jsonNode.get("name").asText());
   }

   public boolean loadActionsFromFile()
   {
      actionSequence.clear();
      executionNextIndexStatus = 0;
      loading = true;
      LogTools.info("Loading from {}", workspaceFile.getClasspathResource().toString());
      MutableBoolean successfullyLoadedActions = new MutableBoolean(true);
      JSONFileTools.load(workspaceFile.getClasspathResourceAsStream(), jsonNode ->
      {
         JSONTools.forEachArrayElement(jsonNode, "actions", actionNode ->
         {
            String actionTypeName = actionNode.get("type").asText();
            RDXBehaviorAction action = RDXActionSequenceTools.createBlankAction(actionTypeName, robotModel, syncedRobot, panel3D, referenceFrameLibrary);
            if (action != null)
            {
               action.getActionData().loadFromFile(actionNode);
               action.updateAfterLoading();
               insertNewAction(action);
            }
            else
            {
               successfullyLoadedActions.setValue(false);
            }
         });
      });
      loading = false;
      if (successfullyLoadedActions.getValue())
      {
         commandNextActionIndex(0);
         return true;
      }

      return false;
   }

   private void commandNextActionIndex(int nextActionIndex)
   {
      currentActionIndexCommandMessage.setData(nextActionIndex);
      ros2.publish(BehaviorActionSequence.EXECUTION_NEXT_INDEX_COMMAND_TOPIC, currentActionIndexCommandMessage);
   }

   public void saveToFile()
   {
      if (workspaceFile.isFileAccessAvailable())
      {
         LogTools.info("Saving to {}", workspaceFile.getPathForResourceLoadingPathFiltered());
         JSONFileTools.save(workspaceFile, jsonRootObjectNode ->
         {
            jsonRootObjectNode.put("name", name);
            ArrayNode actionsArrayNode = jsonRootObjectNode.putArray("actions");
            for (RDXBehaviorAction behaviorAction : actionSequence)
            {
               ObjectNode actionNode = actionsArrayNode.addObject();
               actionNode.put("type", behaviorAction.getClass().getSimpleName());
               behaviorAction.getActionData().saveToFile(actionNode);
            }
         });
      }
      else
      {
         LogTools.error("Saving not available.");
      }
   }

   public void calculate3DViewPick(ImGui3DViewInput input)
   {
      for (var action : actionSequence)
         action.calculate3DViewPick(input);
   }

   public void process3DViewInput(ImGui3DViewInput input)
   {
      for (var action : actionSequence)
         action.process3DViewInput(input);
   }

   public void update()
   {
      for (var action : actionSequence)
         action.update();
   }

   public void renderImGuiWidgets()
   {
      renderMenuBar();

      renderSequencePrimaryControlsArea();

      ImGui.separator();

      // This, paired with the endChild call after, allows this area to scroll separately
      // from the rest, so the top controls are still available while editing later parts
      // of the sequence.
      ImGui.beginChild(labels.get("childRegion"));

      renderInteractableActionListArea();

      ImGui.separator();

      renderActionCreationArea();

      ImGui.endChild();
   }

   private void renderMenuBar()
   {
      ImGui.beginMenuBar();
      if (ImGui.beginMenu(labels.get("File")))
      {
         if (workspaceFile.isFileAccessAvailable() && ImGui.menuItem("Save to JSON"))
         {
            saveToFile();
         }
         if (ImGui.menuItem("Load from JSON"))
         {
            if (!loadActionsFromFile())
            {
               LogTools.warn("Invalid action!");
               actionSequence.clear();
            }
         }
         ImGui.endMenu();
      }
      //      if (ImGui.beginMenu(labels.get("View")))
      //      {
      //         ImGui.endMenu();
      //      }
      ImGui.endMenuBar();
   }

   private void renderSequencePrimaryControlsArea()
   {
      if (ImGui.button(labels.get("[+]")))
      {
         for (var action : actionSequence)
         {
            action.getExpanded().set(true);
         }
      }
      ImGuiTools.previousWidgetTooltip("Expand all action settings");
      ImGui.sameLine();
      if (ImGui.button(labels.get("[-]")))
      {
         for (var action : actionSequence)
         {
            action.getExpanded().set(false);
         }
      }
      ImGuiTools.previousWidgetTooltip("Collapse all action settings");
      ImGui.sameLine();

      if (executionNextIndexStatusSubscription.getMessageNotification().poll())
         ++receivedStatusMessageCount;
      executionNextIndexStatus = executionNextIndexStatusSubscription.getLatest().getData();

      if (ImGui.button(labels.get("<")))
      {
         if (executionNextIndexStatus > 0)
            commandNextActionIndex(executionNextIndexStatus - 1);
      }
      ImGuiTools.previousWidgetTooltip("Go to previous action");
      ImGui.sameLine();
      ImGui.text("Index: " + String.format("%03d", executionNextIndexStatus));
      ImGui.sameLine();
      if (ImGui.button(labels.get(">")))
      {
         if (executionNextIndexStatus < actionSequence.size())
            commandNextActionIndex(executionNextIndexStatus + 1);
      }
      ImGuiTools.previousWidgetTooltip("Go to next action");

      long remoteSequenceSize = sequenceStatusSubscription.getLatest().getSequenceSize();
      boolean outOfSync = remoteSequenceSize != actionSequence.size();

      boolean endOfSequence = executionNextIndexStatus >= actionSequence.size();
      if (!endOfSequence && !outOfSync)
      {
         ImGui.sameLine();
         ImGui.text("Execute");
         ImGui.sameLine();

         automaticExecution.set(automaticExecutionStatusSubscription.getLatest().getData());
         if (ImGui.checkbox(labels.get("Autonomously"), automaticExecution))
         {
            automaticExecutionCommandMessage.setData(automaticExecution.get());
            ros2.publish(BehaviorActionSequence.AUTOMATIC_EXECUTION_COMMAND_TOPIC, automaticExecutionCommandMessage);
         }
         ImGuiTools.previousWidgetTooltip("Enables autonomous execution. Will immediately start executing when checked.");
         if (!automaticExecution.get())
         {
            ImGui.sameLine();
            if (ImGui.button(labels.get("Manually")))
            {
               ros2.publish(BehaviorActionSequence.MANUALLY_EXECUTE_NEXT_ACTION_TOPIC, manuallyExecuteNextActionMessage);
            }
            ImGuiTools.previousWidgetTooltip("Executes the next action.");
         }
      }

      // TODO: Automatically sync between this UI and remote process
      if (ImGui.button("Send to robot"))
      {
         RDXActionSequenceTools.publishActionSequenceUpdateMessage(actionSequence, ros2);
      }
      ImGui.sameLine();
      ImGui.text("# " + receivedSequenceStatusMessageCount);
      if (outOfSync)
      {
         ImGui.sameLine();
         ImGui.text(String.format("Out of sync! # Actions: Local: %d Remote: %d", actionSequence.size(), remoteSequenceSize));
         ImGuiTools.previousWidgetTooltip("Try clicking \"Send to robot\"");
      }

      ImGui.text(String.format("Status # %d: Current action:", receivedStatusMessageCount));
      ImGui.sameLine();
      endOfSequence = executionNextIndexStatus >= actionSequence.size();
      if (endOfSequence)
         ImGui.text("End of sequence.");
      else
         ImGui.text(actionSequence.get(executionNextIndexStatus).getNameForDisplay());
   }

   private void renderInteractableActionListArea()
   {
      reorderRequest.setLeft(-1);
      for (int i = 0; i < actionSequence.size(); i++)
      {
         RDXBehaviorAction action = actionSequence.get(i);

         if (!action.getExpanded().get())
         {
            if (ImGui.button(labels.get("[+]", "expand", i)))
               action.getExpanded().set(true);
            ImGuiTools.previousWidgetTooltip("Expand action settings");
         }
         else
         {
            if (ImGui.button(labels.get("[-]", "collapse", i)))
               action.getExpanded().set(false);
            ImGuiTools.previousWidgetTooltip("Collapse action settings");
         }
         ImGui.sameLine();

         ImGui.sameLine();
         ImGui.text("Index %d".formatted(i));
         ImGui.sameLine();
         if (ImGui.radioButton(labels.get("", "playbackNextIndex", i), executionNextIndexStatus == i))
         {
            commandNextActionIndex(i);
         }
         ImGuiTools.previousWidgetTooltip("Next for excecution");
         action.getDescription().set(action.getActionData().getDescription());
         ImGui.sameLine();
         ImGui.pushItemWidth(9.0f + action.getActionData().getDescription().length() * 7.0f);
         ImGuiTools.inputText(labels.get("", "description", i), action.getDescription());
         action.getActionData().setDescription(action.getDescription().get());
         ImGui.popItemWidth();
         ImGui.sameLine();
         if (i > 0)
         {
            if (ImGui.button(labels.get("^", i)))
            {
               reorderRequest.setLeft(i);
               reorderRequest.setRight(0);
            }
            ImGuiTools.previousWidgetTooltip("Swap with previous action (in ordering)");
            ImGui.sameLine();
         }
         if (i < actionSequence.size() - 1)
         {
            if (ImGui.button(labels.get("v", i)))
            {
               reorderRequest.setLeft(i);
               reorderRequest.setRight(1);
            }
            ImGuiTools.previousWidgetTooltip("Swap with next action (in ordering)");
            ImGui.sameLine();
         }
         if (ImGui.button(labels.get("X", i)))
         {
            RDXBehaviorAction removedAction = actionSequence.remove(i);
            commandNextActionIndex(actionSequence.size());
         }

         if (action.getExpanded().get())
         {
            ImGui.checkbox(labels.get("Show gizmo", "selected", i), action.getSelected());
            ImGuiTools.previousWidgetTooltip("Show gizmo");
            ImGui.sameLine();
            ImGui.text("Type: %s".formatted(action.getNameForDisplay()));
         }

         action.renderImGuiWidgets();

         ImGui.separator();
      }

      int indexToMove = reorderRequest.getLeft();
      if (indexToMove > -1)
      {
         int destinationIndex = reorderRequest.getRight() == 0 ? indexToMove - 1 : indexToMove + 1;
         actionSequence.add(destinationIndex, actionSequence.remove(indexToMove));
      }
   }

   private void renderActionCreationArea()
   {
      RDXBehaviorAction newAction = null;
      if (ImGui.button(labels.get("Add Walk")))
      {
         newAction = new RDXWalkAction(panel3D, robotModel, referenceFrameLibrary);
      }
      ImGui.text("Add Hand Pose:");
      ImGui.sameLine();
      for (var side : RobotSide.values)
      {
         if (ImGui.button(labels.get(side.getPascalCaseName(), "HandPose")))
         {
            RDXHandPoseAction handPoseAction = new RDXHandPoseAction(panel3D, robotModel, syncedRobot.getFullRobotModel(), referenceFrameLibrary);
            // Set the new action to where the last one was for faster authoring
            handPoseAction.setSide(side);
            RDXHandPoseAction nextPreviousHandPoseAction = findNextPreviousHandPoseAction(side);
            if (nextPreviousHandPoseAction != null)
            {
               handPoseAction.setIncludingFrame(nextPreviousHandPoseAction.getReferenceFrame().getParent(),
                                                nextPreviousHandPoseAction.getReferenceFrame().getTransformToParent());
            }
            else // set to current robot's hand pose
            {
               handPoseAction.setToReferenceFrame(syncedRobot.getReferenceFrames().getHandFrame(side));
            }
            newAction = handPoseAction;
         }
         if (side.ordinal() < 1)
            ImGui.sameLine();
      }
      ImGui.text("Add Hand Wrench:");
      ImGui.sameLine();
      for (var side : RobotSide.values)
      {
         if (ImGui.button(labels.get(side.getPascalCaseName(), "HandWrench")))
         {
            RDXHandWrenchAction handWrenchAction = new RDXHandWrenchAction();
            handWrenchAction.getActionData().setSide(side);
            newAction = handWrenchAction;
         }
         if (side.ordinal() < 1)
            ImGui.sameLine();
      }
      if (ImGui.button(labels.get("Add Hand Configuration")))
      {
         newAction = new RDXHandConfigurationAction();
      }
      if (ImGui.button(labels.get("Add Chest Orientation")))
      {
         newAction = new RDXChestOrientationAction();
      }
      if (ImGui.button(labels.get("Add Pelvis Height")))
      {
         newAction = new RDXPelvisHeightAction();
      }
      if (ImGui.button(labels.get("Add Arm Joint Angles")))
      {
         newAction = new RDXArmJointAnglesAction();
      }
      ImGui.text("Add Footstep:");
      ImGui.sameLine();
      for (var side : RobotSide.values)
      {
         if (ImGui.button(labels.get(side.getPascalCaseName(), 1)))
         {
            RDXFootstepAction footstepAction = new RDXFootstepAction(panel3D, robotModel, syncedRobot, referenceFrameLibrary);
            // Set the new action to where the last one was for faster authoring
            footstepAction.setSide(side);
            RDXFootstepAction nextPreviousFootstepAction = findNextPreviousFootstepAction();
            if (nextPreviousFootstepAction != null)
            {
               footstepAction.setIncludingFrame(nextPreviousFootstepAction.getReferenceFrame().getParent(),
                                                nextPreviousFootstepAction.getReferenceFrame().getTransformToParent());
            }
            else // set to current robot's foot pose
            {
               footstepAction.setToReferenceFrame(syncedRobot.getReferenceFrames().getSoleFrame(side));
            }
            newAction = footstepAction;
         }
         if (side.ordinal() < 1)
            ImGui.sameLine();
      }
      if (ImGui.button(labels.get("Add Wait")))
      {
         newAction = new RDXWaitDurationAction();
      }

      if (newAction != null)
         insertNewAction(newAction);
   }

   private RDXFootstepAction findNextPreviousFootstepAction()
   {
      RDXFootstepAction previousAction = null;
      for (int i = 0; i < executionNextIndexStatus - 1; i++)
         if (actionSequence.get(i) instanceof RDXFootstepAction)
            previousAction = (RDXFootstepAction) actionSequence.get(i);
      return previousAction;
   }

   private RDXHandPoseAction findNextPreviousHandPoseAction(RobotSide side)
   {
      RDXHandPoseAction previousAction = null;
      for (int i = 0; i < executionNextIndexStatus - 1; i++)
      {
         if (actionSequence.get(i) instanceof RDXHandPoseAction
             && ((RDXHandPoseAction) actionSequence.get(i)).getActionData().getSide() == side)
         {
            previousAction = (RDXHandPoseAction) actionSequence.get(i);
         }
      }
      return previousAction;
   }

   private void insertNewAction(RDXBehaviorAction action)
   {
      if (executionNextIndexStatus == actionSequence.size()) // No actions left to execute
         actionSequence.add(action);
      else
         actionSequence.add(executionNextIndexStatus + 1, action);

      for (int i = 0; i < actionSequence.size(); i++)
      {
         // When loading, we want to deselect all the actions, otherwise the last one ends up being selected.
         actionSequence.get(i).getSelected().set(!loading && i == executionNextIndexStatus);
      }
      executionNextIndexStatus++;
   }

   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      if (panel.getIsShowing().get())
         for (var action : actionSequence)
            action.getRenderables(renderables, pool);
   }

   public ImGuiPanel getPanel()
   {
      return panel;
   }

   public String getName()
   {
      return name;
   }

   public WorkspaceResourceFile getWorkspaceFile()
   {
      return workspaceFile;
   }
}
