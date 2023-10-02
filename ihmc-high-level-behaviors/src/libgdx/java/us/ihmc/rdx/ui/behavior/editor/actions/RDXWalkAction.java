package us.ihmc.rdx.ui.behavior.editor.actions;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.behaviors.sequence.actions.WalkActionDefinition;
import us.ihmc.behaviors.sequence.actions.WalkActionState;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerParametersBasics;
import us.ihmc.rdx.imgui.ImBooleanWrapper;
import us.ihmc.rdx.imgui.ImDoubleWrapper;
import us.ihmc.rdx.imgui.ImGuiReferenceFrameLibraryCombo;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.input.ImGui3DViewInput;
import us.ihmc.rdx.ui.RDX3DPanel;
import us.ihmc.rdx.ui.RDX3DPanelTooltip;
import us.ihmc.rdx.ui.behavior.editor.RDXBehaviorAction;
import us.ihmc.rdx.ui.behavior.editor.RDXBehaviorActionBasics;
import us.ihmc.rdx.ui.gizmo.RDXPose3DGizmo;
import us.ihmc.rdx.ui.gizmo.RDXSelectablePathControlRingGizmo;
import us.ihmc.rdx.ui.graphics.RDXFootstepGraphic;
import us.ihmc.rdx.ui.graphics.RDXFootstepPlanGraphic;
import us.ihmc.rdx.vr.RDXVRContext;
import us.ihmc.robotics.referenceFrames.ReferenceFrameLibrary;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;

public class RDXWalkAction implements RDXBehaviorAction
{
   private final ReferenceFrameLibrary referenceFrameLibrary;

   private final WalkActionState state = new WalkActionState();
   private final WalkActionDefinition definition = state.getDefinition();
   private final RDXBehaviorActionBasics rdxActionBasics = new RDXBehaviorActionBasics(this);
   private final RDXFootstepPlanGraphic footstepPlanGraphic;
   private final ImGuiReferenceFrameLibraryCombo referenceFrameLibraryCombo;
   private final SideDependentList<RDXFootstepGraphic> goalFeetGraphics = new SideDependentList<>();
   private final RDXSelectablePathControlRingGizmo footstepPlannerGoalGizmo = new RDXSelectablePathControlRingGizmo(definition.getConditionalReferenceFrame().get(),
                                                                                                                    definition.getTransformToParent());
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImBooleanWrapper selectedWrapper = new ImBooleanWrapper(footstepPlannerGoalGizmo::getSelected,
                                                                         footstepPlannerGoalGizmo::setSelected,
                                                                         imBoolean -> ImGui.checkbox(labels.get("Selected"), imBoolean));
   private final SideDependentList<ImBoolean> goalFeetPosesSelected = new SideDependentList<>();
   private final SideDependentList<RDXPose3DGizmo> goalFeetGizmos = new SideDependentList<>();
   private final ImDoubleWrapper swingDurationWidget = new ImDoubleWrapper(definition::getSwingDuration,
                                                                           definition::setSwingDuration,
                                                                           imDouble -> ImGui.inputDouble(labels.get("Swing duration"), imDouble));
   private final ImDoubleWrapper transferDurationWidget = new ImDoubleWrapper(definition::getTransferDuration,
                                                                              definition::setTransferDuration,
                                                                              imDouble -> ImGui.inputDouble(labels.get("Transfer duration"), imDouble));
   private final RDX3DPanelTooltip tooltip;

   public RDXWalkAction(RDX3DPanel panel3D,
                        DRCRobotModel robotModel,
                        ReferenceFrameLibrary referenceFrameLibrary)
   {
      this.referenceFrameLibrary = referenceFrameLibrary;
      footstepPlanGraphic = new RDXFootstepPlanGraphic(robotModel.getContactPointParameters().getControllerFootGroundContactPoints());
      referenceFrameLibraryCombo = new ImGuiReferenceFrameLibraryCombo(referenceFrameLibrary);

      footstepPlannerGoalGizmo.create(panel3D);
      FootstepPlannerParametersBasics footstepPlannerParameters = robotModel.getFootstepPlannerParameters();

      tooltip = new RDX3DPanelTooltip(panel3D);
      panel3D.addImGuiOverlayAddition(this::render3DPanelImGuiOverlays);

      for (RobotSide side : RobotSide.values)
      {
         goalFeetPosesSelected.put(side, new ImBoolean(false));

         RDXPose3DGizmo footGizmo = new RDXPose3DGizmo(definition.getGoalFootstepToParentTransforms().get(side),
                                                       definition.getConditionalReferenceFrame().get());
         footGizmo.create(panel3D);
         goalFeetGizmos.put(side, footGizmo);

         RDXFootstepGraphic goalFootGraphic = new RDXFootstepGraphic(robotModel.getContactPointParameters().getControllerFootGroundContactPoints(), side);
         goalFootGraphic.create();
         goalFeetGraphics.put(side, goalFootGraphic);

         // Set initial placement of goal feet poses
         FramePose3D goalFootPose = new FramePose3D();
         goalFootPose.setToZero(definition.getConditionalReferenceFrame().get());
         goalFootPose.getPosition().addY(0.5 * side.negateIfRightSide(footstepPlannerParameters.getIdealFootstepWidth()));
         goalFootPose.get(footGizmo.getTransformToParent());
      }
   }

   @Override
   public void updateAfterLoading()
   {
      referenceFrameLibraryCombo.setSelectedReferenceFrame(definition.getConditionalReferenceFrame());
      footstepPlannerGoalGizmo.getPathControlRingGizmo().setGizmoFrame(definition.getConditionalReferenceFrame().get());
      for (RobotSide side : RobotSide.values)
      {
         goalFeetGizmos.get(side).setParentFrame(definition.getConditionalReferenceFrame().get());
      }
   }

   public void setIncludingFrame(ReferenceFrame parentFrame, RigidBodyTransform transformToParent)
   {
      definition.getConditionalReferenceFrame().setParentFrameName(parentFrame.getName());
      definition.setTransformToParent(transformToParent);
      update();
   }

   public void setToReferenceFrame(ReferenceFrame referenceFrame)
   {
      definition.getConditionalReferenceFrame().setParentFrameName(ReferenceFrame.getWorldFrame().getName());
      definition.setTransformToParent(referenceFrame.getTransformToWorldFrame());
      update();
   }

   @Override
   public void update(boolean concurrentActionIsNextForExecution)
   {
      definition.update(referenceFrameLibrary);

      if (!getSelected().get())
         goalFeetPosesSelected.forEach(imBoolean -> imBoolean.set(false));

      if (footstepPlannerGoalGizmo.getPathControlRingGizmo().getGizmoFrame() != definition.getConditionalReferenceFrame().get())
      {
         footstepPlannerGoalGizmo.getPathControlRingGizmo().setGizmoFrame(definition.getConditionalReferenceFrame().get());
         for (RobotSide side : RobotSide.values)
         {
            goalFeetGizmos.get(side).setParentFrame(definition.getConditionalReferenceFrame().get());
         }
      }

      footstepPlannerGoalGizmo.getPathControlRingGizmo().update();
      for (RobotSide side : RobotSide.values)
      {
         goalFeetGizmos.get(side).update();
         goalFeetGraphics.get(side).setPose(goalFeetGizmos.get(side).getPose());
      }
      footstepPlanGraphic.update();
   }

   @Override
   public void calculateVRPick(RDXVRContext vrContext)
   {
      footstepPlannerGoalGizmo.calculateVRPick(vrContext);
   }

   @Override
   public void processVRInput(RDXVRContext vrContext)
   {
      footstepPlannerGoalGizmo.processVRInput(vrContext);
   }

   @Override
   public void calculate3DViewPick(ImGui3DViewInput input)
   {
      footstepPlannerGoalGizmo.calculate3DViewPick(input);
      if (getSelected().get())
      {
         for (RobotSide side : RobotSide.values)
         {
            if (goalFeetPosesSelected.get(side).get())
            {
               goalFeetGizmos.get(side).calculate3DViewPick(input);
            }
         }
      }
   }

   @Override
   public void process3DViewInput(ImGui3DViewInput input)
   {
      footstepPlannerGoalGizmo.process3DViewInput(input);
      tooltip.setInput(input);
      if (getSelected().get())
      {
         for (RobotSide side : RobotSide.values)
         {
            if (goalFeetPosesSelected.get(side).get())
            {
               goalFeetGizmos.get(side).process3DViewInput(input);
            }
         }
      }
   }

   @Override
   public void renderImGuiWidgets()
   {
      rdxActionBasics.renderImGuiWidgets();
   }

   @Override
   public void renderImGuiSettingWidgets()
   {
      if (referenceFrameLibraryCombo.render())
      {
         definition.getConditionalReferenceFrame().setParentFrameName(referenceFrameLibraryCombo.getSelectedReferenceFrame().getParent().getName());
      }
      if (ImGui.button(labels.get("Plan")))
      {
         // TODO: Plan preview message
      }
      ImGui.sameLine();
      for (RobotSide side : RobotSide.values)
      {
         ImGui.checkbox(labels.get("Edit " + side.getPascalCaseName()), goalFeetPosesSelected.get(side));
         if (side == RobotSide.LEFT)
            ImGui.sameLine();
      }
      ImGui.pushItemWidth(80.0f);
      swingDurationWidget.renderImGuiWidget();
      transferDurationWidget.renderImGuiWidget();
      ImGui.popItemWidth();
   }

   public void render3DPanelImGuiOverlays()
   {
      if (footstepPlannerGoalGizmo.getPathControlRingGizmo().getRingHovered())
      {
         tooltip.render("%s Action\nIndex: %d\nDescription: %s".formatted(getActionTypeTitle(),
                                                                          getActionIndex(),
                                                                          definition.getDescription()));
      }
   }

   @Override
   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      footstepPlanGraphic.getRenderables(renderables, pool);
      footstepPlannerGoalGizmo.getVirtualRenderables(renderables, pool);
      if (getSelected().get())
      {
         for (RobotSide side : RobotSide.values)
         {
            if (goalFeetPosesSelected.get(side).get())
            {
               goalFeetGizmos.get(side).getRenderables(renderables, pool);
            }
         }
      }
      for (RobotSide side : RobotSide.values)
         goalFeetGraphics.get(side).getRenderables(renderables, pool);
   }

   @Override
   public ImBooleanWrapper getSelected()
   {
      return selectedWrapper;
   }

   @Override
   public String getActionTypeTitle()
   {
      return "Walk Goal";
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
   public WalkActionState getState()
   {
      return state;
   }

   public WalkActionDefinition getDefinition()
   {
      return definition;
   }
}
