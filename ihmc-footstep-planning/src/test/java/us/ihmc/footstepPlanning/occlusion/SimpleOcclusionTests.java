package us.ihmc.footstepPlanning.occlusion;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import us.ihmc.commons.PrintTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.Axis3D;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.footstepPlanning.*;
import us.ihmc.footstepPlanning.graphSearch.parameters.DefaultFootstepPlannerParameters;
import us.ihmc.footstepPlanning.graphSearch.parameters.FootstepPlannerParametersReadOnly;
import us.ihmc.footstepPlanning.tools.PlanarRegionToHeightMapConverter;
import us.ihmc.footstepPlanning.tools.PlannerTools;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.appearance.YoAppearanceRGBColor;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPolygon;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.pathPlanning.visibilityGraphs.parameters.DefaultVisibilityGraphParameters;
import us.ihmc.pathPlanning.visibilityGraphs.parameters.VisibilityGraphsParametersReadOnly;
import us.ihmc.robotics.Assert;
import us.ihmc.robotics.geometry.*;
import us.ihmc.robotics.graphics.Graphics3DObjectTools;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.sensorProcessing.heightMap.HeightMapMessageTools;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.util.simulationTesting.SimulationTestingParameters;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFrameConvexPolygon2D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint3D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoseUsingYawPitchRoll;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;

public class SimpleOcclusionTests
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromSystemProperties();
   private static final boolean visualize = true; //  simulationTestingParameters.getKeepSCSUp();
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private static final int maxSteps = 100;
   private static final int rays = 1000;
   private static final int maxPolygonsToVisualize = 10;
   private static final int maxPolygonsVertices = 50;
   private static final int stepsPerSideToVisualize = 4;
   private static final double defaultMaxAllowedSolveTime = 5.0;

   @Test
   @Disabled
   public void testSimpleOcclusions(TestInfo testInfo)
   {
      FramePose3D startPose = new FramePose3D();
      FramePose3D goalPose = new FramePose3D();
      PlanarRegionsList regions = createSimpleOcclusionField(startPose, goalPose);
      runTest(testInfo, startPose, goalPose, regions, defaultMaxAllowedSolveTime);
   }

   @Test
   @Disabled
   public void testMazeWithOcclusions(TestInfo testInfo)
   {
      FramePose3D startPose = new FramePose3D();
      FramePose3D goalPose = new FramePose3D();
      PlanarRegionsList regions = createMazeOcclusionField(startPose, goalPose);
      runTest(testInfo, startPose, goalPose, regions, defaultMaxAllowedSolveTime);
   }

   private void runTest(TestInfo testInfo, FramePose3D startPose, FramePose3D goalPose, PlanarRegionsList regions, double maxAllowedSolveTime)
   {
      runTest(testInfo, startPose, goalPose, regions, getParameters(), maxAllowedSolveTime);
   }

   private void runTest(TestInfo testInfo, FramePose3D startPose, FramePose3D goalPose, PlanarRegionsList regions, FootstepPlannerParametersReadOnly parameters,
                        double maxAllowedSolveTime)
   {
      YoRegistry registry = new YoRegistry(testInfo.getTestMethod().get().getName());
      YoGraphicsListRegistry graphicsListRegistry = new YoGraphicsListRegistry();

      FootstepPlanningModule footstepPlanningModule = new FootstepPlanningModule(getClass().getSimpleName());
      FootstepPlannerRequest request = new FootstepPlannerRequest();
      request.setGoalFootPoses(parameters.getIdealFootstepWidth(), goalPose);

      FramePose3D stancePose = new FramePose3D();
      RobotSide stanceSide = computeStanceFootPose(startPose, parameters, stancePose);

      SimulationConstructionSet scs = null;
      SideDependentList<List<YoFramePoseUsingYawPitchRoll>> solePosesForVisualization = null;
      List<YoFramePoseUsingYawPitchRoll> stepPosesTaken = null;
      YoFramePoseUsingYawPitchRoll startStep = null;

      YoFramePoint3D observerPoint = null;
      List<YoFramePoint3D> rayIntersectionVisualizations = null;
      List<YoFrameConvexPolygon2D> visiblePolygons = null;
      List<YoFramePoseUsingYawPitchRoll> visiblePolygonPoses = null;
      List<YoGraphicPolygon> polygonVisualizations = null;

      YoBoolean plannerFailed = new YoBoolean("PlannerFailed", registry);
      YoDouble solveTime = new YoDouble("SolveTime", registry);

      if (visualize)
      {
         YoFrameConvexPolygon2D defaultPolygon = new YoFrameConvexPolygon2D("DefaultFootPolygon", worldFrame, 4, registry);
         defaultPolygon.set(PlannerTools.createDefaultFootPolygon());

         solePosesForVisualization = new SideDependentList<>(new ArrayList<>(), new ArrayList<>());
         for (int i = 0; i < stepsPerSideToVisualize; i++)
         {
            for (RobotSide robotSide : RobotSide.values)
            {
               AppearanceDefinition appearance = robotSide == RobotSide.RIGHT ? YoAppearance.Green() : YoAppearance.Red();
               String sideName = robotSide.getCamelCaseName();
               YoFramePoseUsingYawPitchRoll yoPose = new YoFramePoseUsingYawPitchRoll("footPose" + sideName + i, worldFrame, registry);
               yoPose.setToNaN();
               solePosesForVisualization.get(robotSide).add(yoPose);
               YoGraphicPolygon footstepViz = new YoGraphicPolygon("footstep" + sideName + i, defaultPolygon, yoPose, 1.0, appearance);
               graphicsListRegistry.registerYoGraphic("viz", footstepViz);
            }
         }

         startStep = new YoFramePoseUsingYawPitchRoll("startFootPose", worldFrame, registry);
         startStep.setToNaN();
         YoGraphicPolygon stanceViz = new YoGraphicPolygon("startFootPose", defaultPolygon, startStep, 1.0, YoAppearance.Black());
         graphicsListRegistry.registerYoGraphic("viz", stanceViz);

         stepPosesTaken = new ArrayList<>();
         for (int i = 0; i < maxSteps; i++)
         {
            YoFramePoseUsingYawPitchRoll step = new YoFramePoseUsingYawPitchRoll("step" + i, worldFrame, registry);
            step.setToNaN();
            stepPosesTaken.add(step);
            YoGraphicPolygon polygon = new YoGraphicPolygon("step" + i, defaultPolygon, step, 1.0, YoAppearance.Gray());
            graphicsListRegistry.registerYoGraphic("viz", polygon);
         }

         visiblePolygons = new ArrayList<>();
         visiblePolygonPoses = new ArrayList<>();
         polygonVisualizations = new ArrayList<>();
         for (int i = 0; i < maxPolygonsToVisualize; i++)
         {
            YoFrameConvexPolygon2D polygon = new YoFrameConvexPolygon2D("Polygon" + i, worldFrame, maxPolygonsVertices, registry);
            YoFramePoseUsingYawPitchRoll pose = new YoFramePoseUsingYawPitchRoll("PolygonPose" + i, worldFrame, registry);
            pose.setToNaN();
            visiblePolygons.add(polygon);
            visiblePolygonPoses.add(pose);
            YoGraphicPolygon visualization = new YoGraphicPolygon("Polygon" + i, polygon, pose.getPosition(), pose.getYawPitchRoll(), 1.0, 0.02,
                                                                  new YoAppearanceRGBColor(Color.BLUE, 0.8));
            polygonVisualizations.add(visualization);
            graphicsListRegistry.registerYoGraphic("viz", visualization);
            graphicsListRegistry.registerGraphicsUpdatableToUpdateInAPlaybackListener(visualization);
         }

         rayIntersectionVisualizations = new ArrayList<>();
         for (int i = 0; i < rays; i++)
         {
            YoFramePoint3D point = new YoFramePoint3D("RayIntersection" + i, ReferenceFrame.getWorldFrame(), registry);
            point.setToNaN();
            YoGraphicPosition visualization = new YoGraphicPosition("RayIntersection" + i, point, 0.02, YoAppearance.Blue());
            rayIntersectionVisualizations.add(point);
            graphicsListRegistry.registerYoGraphic("viz", visualization);
         }

         observerPoint = new YoFramePoint3D("Observer", worldFrame, registry);
         observerPoint.setToNaN();
         YoGraphicPosition observerVisualization = new YoGraphicPosition("Observer", observerPoint, 0.05, YoAppearance.Red());
         graphicsListRegistry.registerYoGraphic("viz", observerVisualization);

         scs = setupSCS(testInfo.getTestMethod().get().getName(), registry, regions, startPose, goalPose);
         scs.addYoGraphicsListRegistry(graphicsListRegistry);
         scs.setInPoint();
      }

      FootstepPlan plan = null;
      int failCount = 0;
      double maxSolveTime = 0.0;
      boolean reachedGoal = false;

      // Add the ground plane here so the visibility graph works. Remove that later.
      PlanarRegionsList visiblePlanarRegions = new PlanarRegionsList(regions.getPlanarRegion(0));
//      PlanarRegionsList visiblePlanarRegions = new PlanarRegionsList();

      for (int i = 0; i < maxSteps; i++)
      {
         Point3D observer = computeBodyPoint(stancePose, stanceSide, parameters, 0.8);
         visiblePlanarRegions = createVisibleRegions(regions, observer, visiblePlanarRegions, rayIntersectionVisualizations);

         if (visualize)
         {
            observerPoint.set(PlanarRegionTools.projectPointToPlanesVertically(observer, regions));

            for (int polygonIdx = 0; polygonIdx < maxPolygonsToVisualize; polygonIdx++)
            {
               visiblePolygonPoses.get(polygonIdx).setToNaN();
            }
            int polygons = Math.min(maxPolygonsToVisualize, visiblePlanarRegions.getNumberOfPlanarRegions());
            RigidBodyTransform transformToWorld = new RigidBodyTransform();
            FramePose3D pose = new FramePose3D();
            for (int polygonIdx = 0; polygonIdx < polygons; polygonIdx++)
            {
               PlanarRegion planarRegion = visiblePlanarRegions.getPlanarRegion(polygonIdx);
               if (planarRegion.getConvexHull().getNumberOfVertices() > visiblePolygons.get(polygonIdx).getMaxNumberOfVertices())
               {
                  throw new RuntimeException("Increase max number of vertices for visualization.");
               }
               planarRegion.getTransformToWorld(transformToWorld);
               pose.set(transformToWorld);
               visiblePolygonPoses.get(polygonIdx).set(pose);
               visiblePolygons.get(polygonIdx).set(planarRegion.getConvexHull());
            }
         }

         request.setGoalFootPoses(parameters.getIdealFootstepWidth(), goalPose);
         request.setHeightMapData(HeightMapMessageTools.unpackMessage(PlanarRegionToHeightMapConverter.convertFromPlanarRegionsToHeightMap(visiblePlanarRegions)));
         request.setRequestedInitialStanceSide(stanceSide);
         request.setStartFootPoses(parameters.getIdealFootstepWidth(), stancePose);
         request.setTimeout(maxAllowedSolveTime + 5.0);
         request.setPlanBodyPath(true);
         request.setHorizonLength(1.0);

         boolean haveNewPlan = false;
         try
         {
            long startTime = System.currentTimeMillis();
            FootstepPlannerOutput plannerOutput = footstepPlanningModule.handleRequest(request);
            double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
            solveTime.set(seconds);

            if (seconds > maxSolveTime)
            {
               maxSolveTime = seconds;
            }

            if (plannerOutput.getFootstepPlanningResult().validForExecution())
            {
               haveNewPlan = true;
               plan = plannerOutput.getFootstepPlan();
            }
            else
            {
               PrintTools.info("Planner failed: " + plannerOutput.getFootstepPlanningResult());
            }
         }
         catch (Exception e)
         {
            // The catch needs to be removed once the visibility graph is improved.
            PrintTools.info("Planner threw exception:");
            e.printStackTrace();
         }

         if (plan == null)
         {
            if (visualize)
            {
               scs.setTime(i);
               scs.tickAndUpdate();
            }
            PrintTools.info("Failed");
            break;
         }

         plannerFailed.set(!haveNewPlan);
         if (plannerFailed.getBooleanValue())
         {
            plan.remove(0);
            failCount++;
         }

         if (plan.getNumberOfSteps() < 1)
         {
            if (visualize)
            {
               scs.setTime(i);
               scs.tickAndUpdate();
            }
            PrintTools.info("Failed");
            break;
         }

         if (visualize)
         {
            for (int hideIdx = 0; hideIdx < stepsPerSideToVisualize; hideIdx++)
            {
               for (RobotSide robotSide : RobotSide.values)
               {
                  solePosesForVisualization.get(robotSide).get(hideIdx).setToNaN();
               }
            }

            startStep.set(stancePose);
            int stepsToShow = Math.min(plan.getNumberOfSteps(), 2 * stepsPerSideToVisualize);
            for (int stepIdx = 0; stepIdx < stepsToShow; stepIdx++)
            {
               PlannedFootstep footstep = plan.getFootstep(stepIdx);
               FramePose3D footstepPose = new FramePose3D();
               footstep.getFootstepPose(footstepPose);

               List<YoFramePoseUsingYawPitchRoll> listOfPoses = solePosesForVisualization.get(footstep.getRobotSide());
               YoFramePoseUsingYawPitchRoll yoSolePose = listOfPoses.get(stepIdx / 2);
               yoSolePose.set(footstepPose);
            }

            scs.setTime(i);
            scs.tickAndUpdate();

            stepPosesTaken.get(i).set(stancePose);
         }

         PlannedFootstep firstStep = plan.getFootstep(0);
         firstStep.getFootstepPose(stancePose);
         stanceSide = firstStep.getRobotSide();

         Point3D bodyPoint = computeBodyPoint(stancePose, stanceSide, parameters, 0.0);
         if (bodyPoint.epsilonEquals(goalPose.getPosition(), 0.1))
         {
            reachedGoal = true;
            break;
         }
      }

      PrintTools.info("Planner failed " + failCount + " times.");
      PrintTools.info("Maximum solve time was " + maxSolveTime + "s.");
      PrintTools.info("Reached goal: " + reachedGoal);

      if (visualize)
      {
         scs.setOutPoint();
         scs.cropBuffer();
         scs.setPlaybackRealTimeRate(0.001);
         scs.play();
         scs.startOnAThread();
         ThreadTools.sleepForever();
      }
      else
      {
         Assert.assertTrue("Planner took too long: " + maxSolveTime + "s.", maxSolveTime < maxAllowedSolveTime);
         Assert.assertTrue("Did not reach goal.", reachedGoal);

         // Add that after the visibility graph is fixed to deal with start points in no-go-zones.
         // Assert.assertTrue("Planner failed at least once.", failCount == 0);
      }
   }

   private static SimulationConstructionSet setupSCS(String testName, YoRegistry testRegistry, PlanarRegionsList regions, FramePose3D startPose,
                                                     FramePose3D goalPose)
   {
      Robot robot = new Robot(SimpleOcclusionTests.class.getSimpleName());
      robot.addYoRegistry(testRegistry);
      SimulationConstructionSet scs = new SimulationConstructionSet(robot);

      Graphics3DObject graphics3DObject = new Graphics3DObject();
      graphics3DObject.addCoordinateSystem(0.8);
      if (regions != null)
      {
         Graphics3DObjectTools.addPlanarRegionsList(graphics3DObject, regions, YoAppearance.White(), YoAppearance.Grey(), YoAppearance.DarkGray());
         scs.setGroundVisible(false);
      }

      RigidBodyTransform tempTransform = new RigidBodyTransform();

      graphics3DObject.identity();
      startPose.get(tempTransform);
      graphics3DObject.transform(tempTransform);
      graphics3DObject.translate(0.0, 0.0, 0.05);
      graphics3DObject.rotate(Math.PI / 2.0, new Vector3D(0.0, 1.0, 0.0));
      graphics3DObject.addArrow(0.8, YoAppearance.Green(), YoAppearance.Green());

      graphics3DObject.identity();
      goalPose.get(tempTransform);
      graphics3DObject.transform(tempTransform);
      graphics3DObject.translate(0.0, 0.0, 0.05);
      graphics3DObject.rotate(Math.PI / 2.0, new Vector3D(0.0, 1.0, 0.0));
      graphics3DObject.addArrow(0.8, YoAppearance.Red(), YoAppearance.Red());

      scs.addStaticLinkGraphics(graphics3DObject);

      scs.setCameraPosition(-7.0, -1.0, 25.0);
      scs.setCameraFix(0.0, 0.0, 0.0);

      return scs;
   }

   private FootstepPlannerGoal createPlannerGoal(FramePose3D goalPose)
   {
      FootstepPlannerGoal goal = new FootstepPlannerGoal();
      goal.setFootstepPlannerGoalType(FootstepPlannerGoalType.POSE_BETWEEN_FEET);
      goal.setGoalPoseBetweenFeet(goalPose);
      return goal;
   }

   private RobotSide computeStanceFootPose(FramePose3D startPose, FootstepPlannerParametersReadOnly parameters, FramePose3D stancePoseToPack)
   {
      RobotSide side = RobotSide.LEFT;

      double stanceWidth = parameters.getIdealFootstepWidth();
      ReferenceFrame bodyFrame = new PoseReferenceFrame("stanceFrame", startPose);
      FramePoint3D footPosition = new FramePoint3D(bodyFrame);
      footPosition.setY(side.negateIfRightSide(stanceWidth / 2.0));
      footPosition.changeFrame(ReferenceFrame.getWorldFrame());

      stancePoseToPack.setToZero(ReferenceFrame.getWorldFrame());
      stancePoseToPack.getPosition().set(footPosition);
      stancePoseToPack.getOrientation().set(startPose.getOrientation());

      return side;
   }

   private Point3D computeBodyPoint(FramePose3D solePose, RobotSide side, FootstepPlannerParametersReadOnly parameters, double bodyHeight)
   {
      double stanceWidth = parameters.getIdealFootstepWidth();
      ReferenceFrame soleFrame = new PoseReferenceFrame("stanceFrame", solePose);
      FramePoint3D bodyPosition = new FramePoint3D(soleFrame);
      bodyPosition.setY(side.negateIfLeftSide(stanceWidth / 2.0));
      bodyPosition.changeFrame(ReferenceFrame.getWorldFrame());

      Point3D bodyPoint = new Point3D(bodyPosition);
      bodyPoint.addZ(bodyHeight);
      return bodyPoint;
   }

   private PlanarRegionsList createVisibleRegions(PlanarRegionsList regions, Point3D observer, PlanarRegionsList knownRegions,
                                                  List<YoFramePoint3D> rayPointsToPack)
   {
      Point3D[] pointsOnSphere = SpiralBasedAlgorithm.generatePointsOnSphere(observer, 1.0, rays);
      List<ConvexPolygon2D> visiblePolygons = new ArrayList<>();
      for (int i = 0; i < regions.getNumberOfPlanarRegions(); i++)
      {
         visiblePolygons.add(new ConvexPolygon2D());
      }

      RigidBodyTransform transform = new RigidBodyTransform();
      for (int rayIndex = 0; rayIndex < rays; rayIndex++)
      {
         Point3D pointOnSphere = pointsOnSphere[rayIndex];
         Vector3D rayDirection = new Vector3D();
         rayDirection.sub(pointOnSphere, observer);
         ImmutablePair<Point3D, PlanarRegion> intersectionPair = PlanarRegionTools.intersectRegionsWithRay(regions, observer, rayDirection);
         if (intersectionPair == null)
         {
            if (rayPointsToPack != null)
            {
               rayPointsToPack.get(rayIndex).setToNaN();
            }
            continue;
         }

         Point3D intersection = intersectionPair.getLeft();

         if (rayPointsToPack != null)
         {
            rayPointsToPack.get(rayIndex).set(intersection);
         }
         for (int regionIdx = 0; regionIdx < regions.getNumberOfPlanarRegions(); regionIdx++)
         {
            PlanarRegion region = regions.getPlanarRegion(regionIdx);
            if (PlanarRegionTools.isPointOnRegion(region, intersection, 0.01))
            {
               region.getTransformToWorld(transform);
               Point3D pointOnPlane = new Point3D(intersection);
               pointOnPlane.applyInverseTransform(transform);

               Point2D newVertex = new Point2D();
               newVertex.set(pointOnPlane);

               visiblePolygons.get(regionIdx).addVertex(newVertex);
            }
         }
      }

      PlanarRegionsList visible = new PlanarRegionsList();
      for (int i = 0; i < visiblePolygons.size(); i++)
      {
         ConvexPolygon2D polygon = visiblePolygons.get(i);
         polygon.update();
         if (polygon.getNumberOfVertices() < 2)
         {
            continue;
         }

         PlanarRegion originalRegion = regions.getPlanarRegion(i);
         originalRegion.getTransformToWorld(transform);
         PlanarRegion newRegion = new PlanarRegion(transform, polygon);
         visible.addPlanarRegion(newRegion);
      }

      return combine(knownRegions, visible);
   }

   private PlanarRegionsList combine(PlanarRegionsList regionsA, PlanarRegionsList regionsB)
   {
      PlanarRegionsList ret = new PlanarRegionsList();

      boolean[] added = new boolean[regionsB.getNumberOfPlanarRegions()];
      for (int regionBIdx = 0; regionBIdx < regionsB.getNumberOfPlanarRegions(); regionBIdx++)
      {
         added[regionBIdx] = false;
      }

      for (PlanarRegion regionA : regionsA.getPlanarRegionsAsList())
      {
         RigidBodyTransform transformA = new RigidBodyTransform();
         regionA.getTransformToWorld(transformA);
         boolean foundMatchingRegion = false;

         for (int regionBIdx = 0; regionBIdx < regionsB.getNumberOfPlanarRegions(); regionBIdx++)
         {
            PlanarRegion regionB = regionsB.getPlanarRegion(regionBIdx);
            RigidBodyTransform transformB = new RigidBodyTransform();
            regionB.getTransformToWorld(transformB);
            if (transformA.epsilonEquals(transformB, 0.01))
            {
               ConvexPolygon2D newHull = new ConvexPolygon2D(regionA.getConvexHull(), regionB.getConvexHull());
               ret.addPlanarRegion(new PlanarRegion(transformA, newHull));
               foundMatchingRegion = true;
               added[regionBIdx] = true;
            }
         }

         if (!foundMatchingRegion)
         {
            ret.addPlanarRegion(new PlanarRegion(transformA, new ConvexPolygon2D(regionA.getConvexHull())));
         }
      }

      for (int regionBIdx = 0; regionBIdx < regionsB.getNumberOfPlanarRegions(); regionBIdx++)
      {
         if (!added[regionBIdx])
         {
            ret.addPlanarRegion(regionsB.getPlanarRegion(regionBIdx));
         }
      }

      return ret;
   }

   private FootstepPlannerParametersReadOnly getParameters()
   {
      return new DefaultFootstepPlannerParameters();
   }

   private PlanarRegionsList createSimpleOcclusionField(FramePose3D startPoseToPack, FramePose3D goalPoseToPack)
   {
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.addRectangle(6.0, 6.0);
      generator.translate(-1.0, -1.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.Y);
      generator.addRectangle(1.0, 4.0);
      generator.identity();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.translate(1.0, 1.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.Y);
      generator.addRectangle(1.0, 4.0);

      startPoseToPack.setToZero(ReferenceFrame.getWorldFrame());
      startPoseToPack.getOrientation().setYawPitchRoll(Math.PI / 2.0, 0.0, 0.0);
      startPoseToPack.getPosition().set(-2.0, -2.0, 0.0);
      startPoseToPack.prependRollRotation(Math.toRadians(10.0));

      goalPoseToPack.setToZero(ReferenceFrame.getWorldFrame());
      goalPoseToPack.getOrientation().setYawPitchRoll(Math.PI / 2.0, 0.0, 0.0);
      goalPoseToPack.getPosition().set(2.0, 2.0, 0.0);
      goalPoseToPack.prependRollRotation(Math.toRadians(10.0));

      return generator.getPlanarRegionsList();
   }

   private PlanarRegionsList createMazeOcclusionField(FramePose3D startPoseToPack, FramePose3D goalPoseToPack)
   {
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.addRectangle(6.0, 12.0);

      generator.identity();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.translate(-1.0, -2.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.Y);
      generator.addRectangle(1.0, 8.0);

      generator.identity();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.translate(1.0, 0.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.Y);
      generator.addRectangle(1.0, 8.0);

      generator.identity();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.translate(0.0, -4.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.X);
      generator.addRectangle(2.0, 1.0);

      generator.identity();
      generator.rotate(Math.toRadians(10.0), Axis3D.X);
      generator.translate(0.0, 4.0, 0.5);
      generator.rotate(-Math.PI / 2.0, Axis3D.X);
      generator.addRectangle(2.0, 1.0);

      startPoseToPack.setToZero(ReferenceFrame.getWorldFrame());
      startPoseToPack.getOrientation().setYawPitchRoll(Math.PI / 2.0, 0.0, 0.0);
      startPoseToPack.getPosition().set(-2.0, -5.0, 0.0);
      startPoseToPack.prependRollRotation(Math.toRadians(10.0));

      goalPoseToPack.setToZero(ReferenceFrame.getWorldFrame());
      goalPoseToPack.getOrientation().setYawPitchRoll(-Math.PI / 2.0, 0.0, 0.0);
      goalPoseToPack.getPosition().set(0.0, -5.0, 0.0);
      goalPoseToPack.prependRollRotation(Math.toRadians(10.0));

      return generator.getPlanarRegionsList();
   }
}
