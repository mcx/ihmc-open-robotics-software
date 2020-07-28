package us.ihmc.footstepPlanning.swing;

import java.io.File;

import us.ihmc.communication.packets.PlanarRegionMessageConverter;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.footstepPlanning.FootstepDataMessageConverter;
import us.ihmc.footstepPlanning.FootstepPlan;
import us.ihmc.footstepPlanning.graphSearch.parameters.DefaultFootstepPlannerParameters;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerParametersBasics;
import us.ihmc.footstepPlanning.log.FootstepPlannerLog;
import us.ihmc.footstepPlanning.log.FootstepPlannerLogLoader;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.graphics.Graphics3DObjectTools;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;

public class AdaptiveSwingTrajectoryLogViewer
{
   public AdaptiveSwingTrajectoryLogViewer()
   {
      FootstepPlannerLogLoader logLoader = new FootstepPlannerLogLoader();
      FootstepPlannerLogLoader.LoadResult loadResult = logLoader.load(new File("/home/smccrory/Documents/stairsTests/PlannerLog"));
      
      if (loadResult != FootstepPlannerLogLoader.LoadResult.LOADED)
      {
         return;
      }

      FootstepPlannerLog log = logLoader.getLog();

      SwingPlannerParametersBasics swingPlannerParameters = new DefaultSwingPlannerParameters();
      swingPlannerParameters.set(log.getSwingPlannerParametersPacket());

      FootstepPlannerParametersBasics footstepPlannerParameters = new DefaultFootstepPlannerParameters();
      footstepPlannerParameters.set(log.getFootstepParametersPacket());

      SimulationConstructionSet scs = new SimulationConstructionSet(new Robot("Dummy"));
      YoGraphicsListRegistry graphicsListRegistry = new YoGraphicsListRegistry();

      PlanarRegionsList planarRegionsList = PlanarRegionMessageConverter.convertToPlanarRegionsList(log.getRequestPacket().getPlanarRegionsListMessage());
      Graphics3DObject regionsGraphic = new Graphics3DObject();
      for (int i = 0; i < planarRegionsList.getNumberOfPlanarRegions(); i++)
      {
         Graphics3DObjectTools.addPlanarRegion(regionsGraphic, planarRegionsList.getPlanarRegion(i), 0.01, YoAppearance.DarkGray());
      }
      scs.addStaticLinkGraphics(regionsGraphic);

      AdaptiveSwingTrajectoryCalculator adaptiveSwingTrajectoryCalculator = new AdaptiveSwingTrajectoryCalculator(swingPlannerParameters,
                                                                                                                  footstepPlannerParameters,
                                                                                                                  new ProxyAtlasWalkingControllerParameters(),
                                                                                                                  scs,
                                                                                                                  graphicsListRegistry,
                                                                                                                  scs.getRootRegistry());

      scs.addYoGraphicsListRegistry(graphicsListRegistry);
      scs.setGroundVisible(false);
      scs.startOnAThread();

      FootstepPlan footstepPlan = FootstepDataMessageConverter.convertToFootstepPlan(log.getStatusPacket().getFootstepDataList());
      SideDependentList<Pose3D> initialFootPoses = new SideDependentList<>(log.getRequestPacket().getStartLeftFootPose(), log.getRequestPacket().getStartRightFootPose());

      adaptiveSwingTrajectoryCalculator.setPlanarRegionsList(planarRegionsList);
      adaptiveSwingTrajectoryCalculator.setSwingParameters(initialFootPoses, footstepPlan);
      scs.cropBuffer();
   }

   public static void main(String[] args)
   {
      new AdaptiveSwingTrajectoryLogViewer();
   }
}
