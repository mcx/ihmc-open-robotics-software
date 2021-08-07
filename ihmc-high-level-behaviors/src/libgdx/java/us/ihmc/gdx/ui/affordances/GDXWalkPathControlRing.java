package us.ihmc.gdx.ui.affordances;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import controller_msgs.msg.dds.FootstepDataListMessage;
import imgui.flag.ImGuiMouseButton;
import imgui.internal.ImGui;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.ROS2SyncedRobotModel;
import us.ihmc.avatar.networkProcessor.footstepPlanningModule.FootstepPlanningModuleLauncher;
import us.ihmc.avatar.ros2.ROS2ControllerHelper;
import us.ihmc.behaviors.tools.footstepPlanner.MinimalFootstep;
import us.ihmc.communication.packets.ExecutionMode;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.footstepPlanning.*;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerParametersBasics;
import us.ihmc.gdx.input.ImGui3DViewInput;
import us.ihmc.gdx.ui.GDXImGuiBasedUI;
import us.ihmc.gdx.ui.gizmo.GDXFootstepPlannerGoalGizmo;
import us.ihmc.gdx.ui.graphics.GDXFootstepGraphic;
import us.ihmc.gdx.ui.graphics.GDXFootstepPlanGraphic;
import us.ihmc.mecano.frames.MovingReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrameMissingTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SegmentDependentList;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.tools.thread.MissingThreadTools;
import us.ihmc.tools.thread.ResettableExceptionHandlingExecutorService;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class GDXWalkPathControlRing
{
   private final GDXFootstepPlannerGoalGizmo footstepPlannerGoalGizmo = new GDXFootstepPlannerGoalGizmo();
   private boolean selected = false;
   private boolean modified = false;
   private boolean mouseIntersects;
   private ROS2SyncedRobotModel syncedRobot;
   private ROS2ControllerHelper ros2Helper;
   private MovingReferenceFrame midFeetZUpFrame;
   private ResettableExceptionHandlingExecutorService footstepPlanningThread;
   private FootstepPlannerParametersBasics footstepPlannerParameters;
   private GDXFootstepGraphic leftStanceFootstepGraphic;
   private GDXFootstepGraphic rightStanceFootstepGraphic;
   private GDXFootstepGraphic leftGoalFootstepGraphic;
   private GDXFootstepGraphic rightGoalFootstepGraphic;
   private final FramePose3D leftStanceFootPose = new FramePose3D();
   private final FramePose3D rightStanceFootPose = new FramePose3D();
   private final FramePose3D leftGoalFootPose = new FramePose3D();
   private final FramePose3D rightGoalFootPose = new FramePose3D();
   private ReferenceFrame goalFrame;
   private SideDependentList<MovingReferenceFrame> footFrames;
   private final AtomicInteger footstepPlannerId = new AtomicInteger(0);
   private FootstepPlanningModule footstepPlanner;
   private GDXFootstepPlanGraphic foostepPlanGraphic;
   private double halfIdealFootstepWidth;
   private volatile FootstepPlan footstepPlan;

   public void create(GDXImGuiBasedUI baseUI, DRCRobotModel robotModel, ROS2SyncedRobotModel syncedRobot, ROS2ControllerHelper ros2Helper)
   {
      this.syncedRobot = syncedRobot;
      this.ros2Helper = ros2Helper;
      footstepPlannerGoalGizmo.create(baseUI.get3DSceneManager().getCamera3D());
      baseUI.addImGui3DViewInputProcessor(this::process3DViewInput);
      midFeetZUpFrame = syncedRobot.getReferenceFrames().getMidFeetZUpFrame();
      footFrames = syncedRobot.getReferenceFrames().getSoleFrames();

      // periodic thread to footstep plan
      footstepPlanningThread = MissingThreadTools.newSingleThreadExecutor("WalkPathControlPlanning", true, 1);

      footstepPlannerParameters = robotModel.getFootstepPlannerParameters();
      SegmentDependentList<RobotSide, ArrayList<Point2D>> contactPoints = robotModel.getContactPointParameters().getControllerFootGroundContactPoints();
      leftStanceFootstepGraphic = new GDXFootstepGraphic(contactPoints, RobotSide.LEFT);
      rightStanceFootstepGraphic = new GDXFootstepGraphic(contactPoints, RobotSide.RIGHT);
      leftGoalFootstepGraphic = new GDXFootstepGraphic(contactPoints, RobotSide.LEFT);
      rightGoalFootstepGraphic = new GDXFootstepGraphic(contactPoints, RobotSide.RIGHT);

      goalFrame = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent("goalPose",
                                                                                         ReferenceFrame.getWorldFrame(),
                                                                                         footstepPlannerGoalGizmo.getTransform());

      footstepPlanner = FootstepPlanningModuleLauncher.createModule(robotModel);
      foostepPlanGraphic = new GDXFootstepPlanGraphic(contactPoints);
      leftStanceFootstepGraphic.create();
      rightStanceFootstepGraphic.create();
      leftGoalFootstepGraphic.create();
      rightGoalFootstepGraphic.create();

      halfIdealFootstepWidth = footstepPlannerParameters.getIdealFootstepWidth() / 2.0;
      leftStanceFootPose.getPosition().addY(halfIdealFootstepWidth);
      rightStanceFootPose.getPosition().subY(halfIdealFootstepWidth);
      leftStanceFootstepGraphic.setPose(leftStanceFootPose);
      rightStanceFootstepGraphic.setPose(rightStanceFootPose);
   }

   public void update()
   {
      if (!modified)
      {
         footstepPlannerGoalGizmo.getTransform().set(midFeetZUpFrame.getTransformToWorldFrame());
      }

      foostepPlanGraphic.update();
   }

   // This happens after update.
   public void process3DViewInput(ImGui3DViewInput input)
   {
      boolean leftMouseReleasedWithoutDrag = input.mouseReleasedWithoutDrag(ImGuiMouseButton.Left);

      footstepPlannerGoalGizmo.process3DViewInput(input);
      mouseIntersects = footstepPlannerGoalGizmo.getHollowCylinderIntersects();

      if (!modified && mouseIntersects && leftMouseReleasedWithoutDrag)
      {
         selected = true;
         modified = true;
      }
      if (selected && !mouseIntersects && leftMouseReleasedWithoutDrag)
      {
         selected = false;
      }
      if (modified && mouseIntersects && leftMouseReleasedWithoutDrag)
      {
         selected = true;
      }

      if (modified)
      {
         updateStuff();
      }
      if (selected && mouseIntersects && input.isDragging(ImGuiMouseButton.Right))
      {
         footstepPlanningThread.clearQueueAndExecute(() -> planFoosteps(new Pose3D(leftStanceFootPose),
                                                                        new Pose3D(rightStanceFootPose),
                                                                        new Pose3D(leftGoalFootPose),
                                                                        new Pose3D(rightGoalFootPose)));
      }
      if (selected && ImGui.isKeyReleased(input.getSpaceKey()))
      {
         // Send footsteps to robot
         double swingDuration = 1.2;
         double transferDuration = 0.8;
         FootstepDataListMessage footstepDataListMessage = FootstepDataMessageConverter.createFootstepDataListFromPlan(footstepPlan,
                                                                                                                       swingDuration,
                                                                                                                       transferDuration);
         footstepDataListMessage.getQueueingProperties().setExecutionMode(ExecutionMode.OVERRIDE.toByte());
         footstepDataListMessage.getQueueingProperties().setMessageId(UUID.randomUUID().getLeastSignificantBits());
         ros2Helper.publishToController(footstepDataListMessage);
      }

      if (modified && selected && ImGui.isKeyReleased(input.getDeleteKey()))
      {
         selected = false;
         modified = false;
         foostepPlanGraphic.clearAsync();
      }
      if (selected && ImGui.isKeyReleased(input.getEscapeKey()))
      {
         selected = false;
      }

      footstepPlannerGoalGizmo.setShowArrows(selected);
      footstepPlannerGoalGizmo.setHighlightingEnabled(modified);
   }

   private void updateStuff()
   {
      goalFrame.update();
      leftGoalFootPose.setToZero(goalFrame);
      leftGoalFootPose.getPosition().addY(halfIdealFootstepWidth);
      leftGoalFootPose.changeFrame(ReferenceFrame.getWorldFrame());
      rightGoalFootPose.setToZero(goalFrame);
      rightGoalFootPose.getPosition().subY(halfIdealFootstepWidth);
      rightGoalFootPose.changeFrame(ReferenceFrame.getWorldFrame());
      leftStanceFootPose.setToZero(footFrames.get(RobotSide.LEFT));
      leftStanceFootPose.changeFrame(ReferenceFrame.getWorldFrame());
      rightStanceFootPose.setToZero(footFrames.get(RobotSide.RIGHT));
      rightStanceFootPose.changeFrame(ReferenceFrame.getWorldFrame());

      leftGoalFootstepGraphic.setPose(leftGoalFootPose);
      rightGoalFootstepGraphic.setPose(rightGoalFootPose);
   }

   private void planFoosteps(Pose3DReadOnly leftStanceFootPose,
                             Pose3DReadOnly rightStanceFootPose,
                             Pose3DReadOnly leftGoalFootPose,
                             Pose3DReadOnly rightGoalFootPose)
   {

      FootstepPlannerRequest footstepPlannerRequest = new FootstepPlannerRequest();
      footstepPlannerRequest.setPlanBodyPath(false);
      footstepPlannerRequest.setStartFootPoses(leftStanceFootPose, rightStanceFootPose);
      footstepPlannerRequest.setGoalFootPoses(leftGoalFootPose, rightGoalFootPose);
      footstepPlannerRequest.setAssumeFlatGround(true);
      footstepPlannerRequest.setRequestId(footstepPlannerId.getAndIncrement());
      FootstepPlannerOutput footstepPlannerOutput = footstepPlanner.handleRequest(footstepPlannerRequest);
      if (footstepPlannerOutput.getFootstepPlan().getNumberOfSteps() > 0)
      {
         foostepPlanGraphic.generateMeshesAsync(MinimalFootstep.reduceFootstepPlanForUIMessager(footstepPlannerOutput.getFootstepPlan(), "plan"));
      }
      footstepPlan = new FootstepPlan(footstepPlannerOutput.getFootstepPlan());
   }

   public void getVirtualRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      if (modified)
      {
         leftStanceFootstepGraphic.getRenderables(renderables, pool);
         rightStanceFootstepGraphic.getRenderables(renderables, pool);
         leftGoalFootstepGraphic.getRenderables(renderables, pool);
         rightGoalFootstepGraphic.getRenderables(renderables, pool);
         foostepPlanGraphic.getRenderables(renderables, pool);
      }
      if (modified || mouseIntersects)
      {
         footstepPlannerGoalGizmo.getRenderables(renderables, pool);
      }
   }

   public void destroy()
   {
      footstepPlanningThread.destroy();
      foostepPlanGraphic.destroy();
   }
}
