package us.ihmc.rdx.ui.footstepPlanner;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.ImGui;
import imgui.type.ImString;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.behaviors.tools.footstepPlanner.MinimalFootstep;
import us.ihmc.commons.nio.BasicPathVisitor;
import us.ihmc.commons.nio.PathTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.commons.thread.TypedNotification;
import us.ihmc.communication.packets.PlanarRegionMessageConverter;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.footstepPlanning.FootstepDataMessageConverter;
import us.ihmc.footstepPlanning.FootstepPlan;
import us.ihmc.footstepPlanning.FootstepPlanningResult;
import us.ihmc.footstepPlanning.graphSearch.graph.visualization.BipedalFootstepPlannerNodeRejectionReason;
import us.ihmc.footstepPlanning.log.FootstepPlannerLog;
import us.ihmc.footstepPlanning.log.FootstepPlannerLogLoader;
import us.ihmc.footstepPlanning.log.FootstepPlannerLogger;
import us.ihmc.footstepPlanning.tools.FootstepPlannerRejectionReasonReport;
import us.ihmc.log.LogTools;
import us.ihmc.rdx.imgui.ImGuiPanel;
import us.ihmc.rdx.imgui.ImGuiTools;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.sceneManager.RDX3DScene;
import us.ihmc.rdx.sceneManager.RDXSceneLevel;
import us.ihmc.rdx.ui.RDX3DPanel;
import us.ihmc.rdx.ui.RDXBaseUI;
import us.ihmc.rdx.ui.graphics.RDXFootstepGraphic;
import us.ihmc.rdx.ui.graphics.RDXFootstepPlanGraphic;
import us.ihmc.rdx.visualizers.RDXPlanarRegionsGraphic;
import us.ihmc.rdx.visualizers.RDXSphereAndArrowGraphic;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class RDXFootstepPlannerLogViewer
{
   private final RDX3DScene scene3D;
   private final RDX3DPanel panel3D;
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImString logFolder = new ImString();
   private final TypedNotification<FootstepPlannerLog> logLoadedNotification = new TypedNotification<>();
   private FootstepPlannerLog footstepPlannerLog = null;
   private final SideDependentList<RDXFootstepGraphic> startFootPoses = new SideDependentList<>();
   private final SideDependentList<RDXFootstepGraphic> goalFootPoses = new SideDependentList<>();
   private final RDXSphereAndArrowGraphic goalGraphic;
   private final Pose3D goalPose = new Pose3D();
   private final Comparator<Path> naturalOrderComparator = Comparator.comparing(path -> path.getFileName().toString());
   private final SortedSet<Path> sortedLogFolderPaths = new TreeSet<>(naturalOrderComparator.reversed());
   private boolean indexedLogFolderOnce = false;
   private final RDXPlanarRegionsGraphic planarRegionsGraphic;
   private final RDXFootstepPlanGraphic footstepPlanGraphic;
   private FootstepPlan footstepPlan;
   private FootstepPlannerRejectionReasonReport rejectionReasonReport;

   public RDXFootstepPlannerLogViewer(RDXBaseUI baseUI, DRCRobotModel robotModel)
   {
      scene3D = new RDX3DScene();
      scene3D.create(RDXSceneLevel.values());
      scene3D.addDefaultLighting();
      panel3D = new RDX3DPanel("Footstep Planner Log 3D View");
      baseUI.add3DPanel(panel3D, scene3D);
      panel3D.getImGuiPanel().addChild(new ImGuiPanel("Footstep Planner Log Viewer Controls", this::renderImGuiWidgets));
      scene3D.addRenderableProvider(this::getRenderables);

      planarRegionsGraphic = new RDXPlanarRegionsGraphic();
      scene3D.addRenderableProvider(planarRegionsGraphic::getRenderables);

      logFolder.set(FootstepPlannerLogger.defaultLogsDirectory);

      goalGraphic = new RDXSphereAndArrowGraphic();
      goalGraphic.create(0.027, 0.027 * 6.0, Color.GREEN);
      footstepPlanGraphic = new RDXFootstepPlanGraphic(robotModel.getContactPointParameters().getControllerFootGroundContactPoints());

      for (RobotSide side : RobotSide.values)
      {
         RDXFootstepGraphic goalPoseGraphic = new RDXFootstepGraphic(robotModel.getContactPointParameters().getControllerFootGroundContactPoints(), side);
         goalPoseGraphic.create();
         goalFootPoses.put(side, goalPoseGraphic);
         RDXFootstepGraphic startPoseGraphic = new RDXFootstepGraphic(robotModel.getContactPointParameters().getControllerFootGroundContactPoints(), side);
         startPoseGraphic.create();
         startFootPoses.put(side, startPoseGraphic);
      }
   }

   public void update()
   {
      if (logLoadedNotification.poll())
      {
         footstepPlannerLog = logLoadedNotification.read();
         planarRegionsGraphic.generateMeshesAsync(PlanarRegionMessageConverter.convertToPlanarRegionsList(footstepPlannerLog.getRequestPacket()
                                                                                                                            .getPlanarRegionsListMessage()));
         footstepPlan = FootstepDataMessageConverter.convertToFootstepPlan(footstepPlannerLog.getStatusPacket().getFootstepDataList());
         footstepPlanGraphic.generateMeshesAsync(MinimalFootstep.reduceFootstepPlanForUIMessager(footstepPlan, "Footstep plan"));

         rejectionReasonReport = new FootstepPlannerRejectionReasonReport(footstepPlannerLog);
         rejectionReasonReport.update();
      }
      planarRegionsGraphic.update();
      footstepPlanGraphic.update();
   }

   public void renderImGuiWidgets()
   {
      ImGuiTools.inputText(labels.get("Log folder"), logFolder);

      boolean reindexClicked = ImGui.button(labels.get("Reindex log folder"));
      if (!indexedLogFolderOnce || reindexClicked)
      {
         indexedLogFolderOnce = true;
         reindexLogFolder();
      }
      ImGui.sameLine();
      ImGui.text("Available logs:");

      ImGui.beginChild(labels.get("Scroll area"), ImGui.getColumnWidth(), 150.0f);
      for (Path sortedLogFolderPath : sortedLogFolderPaths)
      {
         String logName = sortedLogFolderPath.getFileName().toString();
         if (ImGui.radioButton(labels.get(logName), footstepPlannerLog != null && footstepPlannerLog.getLogName().equals(logName)))
         {
            footstepPlannerLog = null;
            ThreadTools.startAThread(() ->
            {
               FootstepPlannerLogLoader logLoader = new FootstepPlannerLogLoader();
               FootstepPlannerLogLoader.LoadResult loadResult = logLoader.load(new File(logFolder.get() + "/" + logName));
               if (loadResult == FootstepPlannerLogLoader.LoadResult.LOADED)
               {
                  logLoadedNotification.set(logLoader.getLog());
               }
               else if (loadResult == FootstepPlannerLogLoader.LoadResult.ERROR)
               {
                  LogTools.error("Error loading log");
               }

            }, "FootstepPlanLogLoading");
         }
      }
      ImGui.endChild();

      if (footstepPlannerLog != null)
      {
         ImGui.text("Loaded log:");
         ImGui.text(footstepPlannerLog.getLogName());

         goalPose.set(footstepPlannerLog.getStatusPacket().getGoalPose().getPosition(), footstepPlannerLog.getStatusPacket().getGoalPose().getOrientation());
         goalGraphic.setToPose(goalPose);
         goalFootPoses.get(RobotSide.LEFT).setPose(footstepPlannerLog.getRequestPacket().getGoalLeftFootPose());
         goalFootPoses.get(RobotSide.RIGHT).setPose(footstepPlannerLog.getRequestPacket().getGoalRightFootPose());
         startFootPoses.get(RobotSide.LEFT).setPose(footstepPlannerLog.getRequestPacket().getStartLeftFootPose());
         startFootPoses.get(RobotSide.RIGHT).setPose(footstepPlannerLog.getRequestPacket().getStartRightFootPose());

         ImGui.text("Number of planned footsteps: " + footstepPlan.getNumberOfSteps());
         ImGui.text("Requested initial stance side: " + RobotSide.fromByte(footstepPlannerLog.getRequestPacket().getRequestedInitialStanceSide()).name());
         ImGui.text("Goal distance proximity: " + footstepPlannerLog.getRequestPacket().getGoalDistanceProximity());
         ImGui.text("Goal yaw proximity: " + footstepPlannerLog.getRequestPacket().getGoalYawProximity());
         ImGui.text("Timeout: " + footstepPlannerLog.getRequestPacket().getTimeout());
         ImGui.text("Horizon length: " + footstepPlannerLog.getRequestPacket().getHorizonLength());
         ImGui.text("Assume flat ground: " + footstepPlannerLog.getRequestPacket().getAssumeFlatGround());
         ImGui.text("Snap goal steps: " + footstepPlannerLog.getRequestPacket().getSnapGoalSteps());
         ImGui.text("Planner request ID: " + footstepPlannerLog.getRequestPacket().getPlannerRequestId());
         ImGui.text("Perform A* search: " + footstepPlannerLog.getRequestPacket().getPerformAStarSearch());
         ImGui.text("Plan body path: " + footstepPlannerLog.getRequestPacket().getPlanBodyPath());
         ImGui.text("Result: " + FootstepPlanningResult.fromByte(footstepPlannerLog.getStatusPacket().getFootstepPlanningResult()).name());

         for (BipedalFootstepPlannerNodeRejectionReason reason : rejectionReasonReport.getSortedReasons())
         {
            double rejectionPercentage = rejectionReasonReport.getRejectionReasonPercentage(reason);
            ImGui.text(String.format("Rejection %.3f%%: %s", rejectionPercentage, reason));
         }
      }
      else
      {
         ImGui.text("No log loaded.");
      }
   }

   private void reindexLogFolder()
   {
      sortedLogFolderPaths.clear();
      PathTools.walkFlat(Paths.get(logFolder.get()), (path, type) -> {
         if (type == BasicPathVisitor.PathType.DIRECTORY
             && path.getFileName().toString().endsWith(FootstepPlannerLogger.FOOTSTEP_PLANNER_LOG_POSTFIX))
            sortedLogFolderPaths.add(path);
         return FileVisitResult.CONTINUE;
      });
   }

   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool, Set<RDXSceneLevel> sceneLevels)
   {
      goalGraphic.getRenderables(renderables, pool);
      for (RDXFootstepGraphic goalFootPose : goalFootPoses)
      {
         goalFootPose.getRenderables(renderables, pool);
      }
      for (RDXFootstepGraphic startFootPose : startFootPoses)
      {
         startFootPose.getRenderables(renderables, pool);
      }
   }
}
