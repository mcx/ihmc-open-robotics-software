package us.ihmc.footstepPlanning.graphSearch.stepChecking;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.InterpolationTools;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tools.EuclidCoreTools;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.footstepPlanning.graphSearch.footstepSnapping.FootstepSnapAndWiggler;
import us.ihmc.footstepPlanning.graphSearch.graph.DiscreteFootstep;
import us.ihmc.footstepPlanning.graphSearch.graph.LatticePoint;
import us.ihmc.footstepPlanning.graphSearch.graph.visualization.BipedalFootstepPlannerNodeRejectionReason;
import us.ihmc.footstepPlanning.graphSearch.parameters.DefaultFootstepPlannerParameters;
import us.ihmc.footstepPlanning.tools.PlannerTools;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.geometry.PlanarRegionsListGenerator;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.yoVariables.registry.YoRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.robotics.Assert.assertEquals;
import static us.ihmc.robotics.Assert.assertTrue;

public class FootstepPoseCheckerTest
{
   private final YoRegistry registry = new YoRegistry(getClass().getSimpleName());

   @Test
   public void testStanceFootPitchedTooMuch()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();

      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      FootstepPoseHeuristicChecker checker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);
      parameters.setMaximumStepXWhenFullyPitched(0.3);
      parameters.setMinimumStepZWhenFullyPitched(0.05);

      PlanarRegionsListGenerator planarRegionGenerator = new PlanarRegionsListGenerator();
      planarRegionGenerator.rotate(new Quaternion(0.0, Math.toRadians(-30.0), 0.0));
      planarRegionGenerator.translate(0.0, 0.15, 0.0);
      planarRegionGenerator.addRectangle(0.3, 0.3);
      planarRegionGenerator.identity();
      planarRegionGenerator.translate(0.3, -0.15, -0.15);
      planarRegionGenerator.addRectangle(0.3, 0.3);

      PlanarRegionsList angledGround = planarRegionGenerator.getPlanarRegionsList();

      planarRegionGenerator = new PlanarRegionsListGenerator();
      planarRegionGenerator.translate(0.0, 0.15, 0.0);
      planarRegionGenerator.addRectangle(0.3, 0.3);
      planarRegionGenerator.identity();
      planarRegionGenerator.translate(0.3, -0.15, -0.15);
      planarRegionGenerator.addRectangle(0.3, 0.3);

      PlanarRegionsList flatGround = planarRegionGenerator.getPlanarRegionsList();

      snapper.setPlanarRegions(flatGround);

      DiscreteFootstep stanceNode = new DiscreteFootstep(0.0, 0.15, 0.0, RobotSide.LEFT);
      DiscreteFootstep childNode = new DiscreteFootstep(0.3, -0.15, 0.0, RobotSide.RIGHT);

      BipedalFootstepPlannerNodeRejectionReason rejectionReason = checker.checkStepValidity(childNode, stanceNode, null);
      assertNull(rejectionReason);

      snapper.setPlanarRegions(angledGround);

      rejectionReason = checker.checkStepValidity(childNode, stanceNode, null);
      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_TOO_LOW_AND_FORWARD_WHEN_PITCHED, rejectionReason);

      // TODO check that this doesn't cause the other rejection reasons to fail if the pitch is flat.
   }

   @Test
   public void testMaxMinYawOnLeftFootAtOrigin()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      double maxYaw = 1.2;
      double minYaw = -0.5;
      double yawReduction = 0.5;
      parameters.setMaximumStepYaw(maxYaw);
      parameters.setMinimumStepYaw(minYaw);
      parameters.setStepYawReductionFactorAtMaxReach(yawReduction);

      double maxYawAtFullLength = yawReduction * maxYaw;
      double minYawAtFullLength = yawReduction * minYaw;

      FootstepPoseHeuristicChecker nodeChecker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);

      double snappedPosition = snapToGrid(parameters.getIdealFootstepWidth());
      double reachAtChild = Math.abs(snappedPosition - parameters.getIdealFootstepWidth());

      double maxValue = InterpolationTools.linearInterpolate(maxYaw, maxYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      double minValue = InterpolationTools.linearInterpolate(minYaw, minYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      DiscreteFootstep parentNode = new DiscreteFootstep(0.0, 0.0, 0.0, RobotSide.RIGHT);
      DiscreteFootstep childNodeAtMaxYaw = new DiscreteFootstep(0.0, parameters.getIdealFootstepWidth(), snapDownToYaw(maxValue), RobotSide.LEFT);
      DiscreteFootstep childNodeAtMinYaw = new DiscreteFootstep(0.0, parameters.getIdealFootstepWidth(), snapUpToYaw(minValue), RobotSide.LEFT);

      assertNull(nodeChecker.checkStepValidity(childNodeAtMaxYaw, parentNode, null));
      assertNull(nodeChecker.checkStepValidity(childNodeAtMinYaw, parentNode, null));

      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(0.05, parameters.getIdealFootstepWidth(), maxYaw, RobotSide.LEFT), parentNode, null));
      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(0.05, parameters.getIdealFootstepWidth(), minYaw, RobotSide.LEFT), parentNode, null));
   }

   @Test
   public void testMaxMinYawOnRightFootAtOrigin()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      double maxYaw = 1.2;
      double minYaw = -0.5;
      double yawReduction = 0.5;
      parameters.setMaximumStepYaw(maxYaw);
      parameters.setMinimumStepYaw(minYaw);
      parameters.setStepYawReductionFactorAtMaxReach(yawReduction);

      double maxYawAtFullLength = yawReduction * maxYaw;
      double minYawAtFullLength = yawReduction * minYaw;

      FootstepPoseHeuristicChecker nodeChecker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);

      double snappedPosition = snapToGrid(parameters.getIdealFootstepWidth());
      double reachAtChild = Math.abs(snappedPosition - parameters.getIdealFootstepWidth());

      double maxValue = InterpolationTools.linearInterpolate(maxYaw, maxYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      double minValue = InterpolationTools.linearInterpolate(minYaw, minYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      DiscreteFootstep parentNode = new DiscreteFootstep(0.0, 0.0, 0.0, RobotSide.LEFT);
      DiscreteFootstep childNodeAtMaxYaw = new DiscreteFootstep(0.0, -parameters.getIdealFootstepWidth(), -snapDownToYaw(maxValue), RobotSide.RIGHT);
      DiscreteFootstep childNodeAtMinYaw = new DiscreteFootstep(0.0, -parameters.getIdealFootstepWidth(), -snapUpToYaw(minValue), RobotSide.RIGHT);

      assertNull(nodeChecker.checkStepValidity(childNodeAtMaxYaw, parentNode, null));
      assertNull(nodeChecker.checkStepValidity(childNodeAtMinYaw, parentNode, null));

      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(0.05, -parameters.getIdealFootstepWidth(), -maxYaw, RobotSide.RIGHT), parentNode, null));
      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(0.05, -parameters.getIdealFootstepWidth(), -minYaw, RobotSide.RIGHT), parentNode, null));
   }

   @Test
   public void testMaxMinYawOnLeftFootAtOriginWithParentYaw()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      double maxYaw = 1.2;
      double minYaw = -0.5;
      double yawReduction = 0.5;
      parameters.setMaximumStepYaw(maxYaw);
      parameters.setMinimumStepYaw(minYaw);
      parameters.setStepYawReductionFactorAtMaxReach(yawReduction);

      double maxYawAtFullLength = yawReduction * maxYaw;
      double minYawAtFullLength = yawReduction * minYaw;

      FootstepPoseHeuristicChecker nodeChecker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);

      double parentYaw = snapToYawGrid(Math.toRadians(75));

      double snappedPosition = snapToGrid(parameters.getIdealFootstepWidth());
      double reachAtChild = Math.abs(snappedPosition - parameters.getIdealFootstepWidth());

      PoseReferenceFrame parentFrame = new PoseReferenceFrame("parentFrame", ReferenceFrame.getWorldFrame());
      parentFrame.setOrientationAndUpdate(new Quaternion(parentYaw, 0.0, 0.0));

      FramePoint3D childPosition = new FramePoint3D(parentFrame, 0.0, parameters.getIdealFootstepWidth(), 0.0);
      childPosition.changeFrame(ReferenceFrame.getWorldFrame());

      double maxValue = InterpolationTools.linearInterpolate(maxYaw, maxYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      double minValue = InterpolationTools.linearInterpolate(minYaw, minYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      DiscreteFootstep parentNode = new DiscreteFootstep(0.0, 0.0, parentYaw, RobotSide.RIGHT);

      DiscreteFootstep childNodeAtMaxYaw = new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw + snapDownToYaw(maxValue), RobotSide.LEFT);
      DiscreteFootstep childNodeAtMinYaw = new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw + snapUpToYaw(minValue), RobotSide.LEFT);

      assertNull(nodeChecker.checkStepValidity(childNodeAtMaxYaw, parentNode, null));
      assertNull(nodeChecker.checkStepValidity(childNodeAtMinYaw, parentNode, null));

      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw + maxYaw, RobotSide.LEFT), parentNode, null));
      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw + minYaw, RobotSide.LEFT), parentNode, null));
   }

   @Test
   public void testMaxMinYawOnRightFootAtOriginWithParentYaw()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      double maxYaw = 1.2;
      double minYaw = -0.5;
      double yawReduction = 0.5;
      parameters.setMaximumStepYaw(maxYaw);
      parameters.setMinimumStepYaw(minYaw);
      parameters.setStepYawReductionFactorAtMaxReach(yawReduction);

      double maxYawAtFullLength = yawReduction * maxYaw;
      double minYawAtFullLength = yawReduction * minYaw;

      FootstepPoseHeuristicChecker nodeChecker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);

      double parentYaw = snapToYawGrid(Math.toRadians(75));

      double snappedPosition = snapToGrid(parameters.getIdealFootstepWidth());
      double reachAtChild = Math.abs(snappedPosition - parameters.getIdealFootstepWidth());

      PoseReferenceFrame parentFrame = new PoseReferenceFrame("parentFrame", ReferenceFrame.getWorldFrame());
      parentFrame.setOrientationAndUpdate(new Quaternion(parentYaw, 0.0, 0.0));

      FramePoint3D childPosition = new FramePoint3D(parentFrame, 0.0, -parameters.getIdealFootstepWidth(), 0.0);
      childPosition.changeFrame(ReferenceFrame.getWorldFrame());

      double maxValue = InterpolationTools.linearInterpolate(maxYaw, maxYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      double minValue = InterpolationTools.linearInterpolate(minYaw, minYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      DiscreteFootstep parentNode = new DiscreteFootstep(0.0, 0.0, parentYaw, RobotSide.LEFT);

      DiscreteFootstep childNodeAtMaxYaw = new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw - snapDownToYaw(maxValue), RobotSide.RIGHT);
      DiscreteFootstep childNodeAtMinYaw = new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw - snapUpToYaw(minValue), RobotSide.RIGHT);

      assertNull(nodeChecker.checkStepValidity(childNodeAtMaxYaw, parentNode, null));
      assertNull(nodeChecker.checkStepValidity(childNodeAtMinYaw, parentNode, null));

      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw - maxYaw, RobotSide.RIGHT), parentNode, null));
      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH,
                   nodeChecker.checkStepValidity(new DiscreteFootstep(childPosition.getX(), childPosition.getY(), parentYaw - minYaw, RobotSide.RIGHT), parentNode, null));
   }

   @Test
   public void testMaxMinYawOnLeftFootAtOriginSteppingUp()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      double maxYaw = 1.2;
      double minYaw = -0.5;
      double yawReduction = 0.5;
      parameters.setMaximumStepYaw(maxYaw);
      parameters.setMinimumStepYaw(minYaw);
      parameters.setStepYawReductionFactorAtMaxReach(yawReduction);

      double maxYawAtFullLength = yawReduction * maxYaw;
      double minYawAtFullLength = yawReduction * minYaw;

      FootstepPoseHeuristicChecker nodeChecker = new FootstepPoseHeuristicChecker(parameters, snapper, registry);

      double snappedYPosition = snapToGrid(parameters.getIdealFootstepWidth());
      double snappedXPosition = snapDownToGrid(0.8 * parameters.getMaximumStepReach());
      double reachAtChild = EuclidCoreTools.norm(snappedXPosition, snappedYPosition - parameters.getIdealFootstepWidth());

      double maxValue = InterpolationTools.linearInterpolate(maxYaw, maxYawAtFullLength, reachAtChild / parameters.getMaximumStepReach());
      DiscreteFootstep parentNode = new DiscreteFootstep(0.0, 0.0, 0.0, RobotSide.RIGHT);
      DiscreteFootstep childNodeAtMaxYaw = new DiscreteFootstep(snappedXPosition, parameters.getIdealFootstepWidth(), snapDownToYaw(maxValue), RobotSide.LEFT);

      PlanarRegionsListGenerator planarRegionsListGenerator = new PlanarRegionsListGenerator();
      planarRegionsListGenerator.addRectangle(0.25, 0.15);
      planarRegionsListGenerator.translate(snappedXPosition, snappedYPosition, 0.15);
      planarRegionsListGenerator.addRectangle(0.25, 0.15);

      PlanarRegionsList planarRegionsList = planarRegionsListGenerator.getPlanarRegionsList();

      snapper.setPlanarRegions(planarRegionsList);

      assertEquals(BipedalFootstepPlannerNodeRejectionReason.STEP_YAWS_TOO_MUCH, nodeChecker.checkStepValidity(childNodeAtMaxYaw, parentNode, null));
   }

   @Test
   public void testFindNearestReachabilityCheckpoint()
   {
      SideDependentList<ConvexPolygon2D> footPolygons = PlannerTools.createDefaultFootPolygons();
      DefaultFootstepPlannerParameters parameters = new DefaultFootstepPlannerParameters();
      FootstepSnapAndWiggler snapper = new FootstepSnapAndWiggler(footPolygons, parameters);
      Map<FramePose3D, Boolean> reachabilityMap = populateReachabilityMap();

            FootstepPoseReachabilityChecker reachabilityChecker = new FootstepPoseReachabilityChecker(parameters, snapper, reachabilityMap, registry);
      FramePose3D testFootPose = new FramePose3D();
      FramePose3D nearestCheckpointTrue = new FramePose3D();
      FramePose3D nearestCheckpointCalculated;

      // Test same XY, different yaw
      testFootPose.getPosition().set(-0.500, 0.056, 0.0);
      testFootPose.getOrientation().setYawPitchRoll(Math.toRadians(69), 0.0, 0.0);
      nearestCheckpointTrue.getPosition().set(-0.500, 0.056, 0.0);
      nearestCheckpointTrue.getOrientation().setYawPitchRoll(Math.toRadians(70), 0.0, 0.0);
      nearestCheckpointCalculated = reachabilityChecker.findNearestCheckpoint(testFootPose, reachabilityMap.keySet());
      assertTrue(nearestCheckpointTrue.geometricallyEquals(nearestCheckpointCalculated, 0.001));

      // Test same X and yaw, different Y
      testFootPose.getPosition().set(-0.278, -0.47, 0.0);
      testFootPose.getOrientation().setYawPitchRoll(Math.toRadians(70-5*(11/70)), 0.0, 0.0);
      nearestCheckpointTrue.getPosition().set(-0.278, -0.50, 0.0);
      nearestCheckpointTrue.getOrientation().setYawPitchRoll(Math.toRadians(70-5*(11/70)), 0.0, 0.0);
      nearestCheckpointCalculated = reachabilityChecker.findNearestCheckpoint(testFootPose, reachabilityMap.keySet());
      assertTrue(nearestCheckpointTrue.geometricallyEquals(nearestCheckpointCalculated, 0.001));

      // Test same Y and yaw, different X
      testFootPose.getPosition().set(0.171, 0.056, 0.0);
      testFootPose.getOrientation().setYawPitchRoll(Math.toRadians(70-2*(11/70)), 0.0, 0.0);
      nearestCheckpointTrue.getPosition().set(0.167, 0.056, 0.0);
      nearestCheckpointTrue.getOrientation().setYawPitchRoll(Math.toRadians(70-2*(11/70)), 0.0, 0.0);
      nearestCheckpointCalculated = reachabilityChecker.findNearestCheckpoint(testFootPose, reachabilityMap.keySet());
      assertTrue(nearestCheckpointTrue.geometricallyEquals(nearestCheckpointCalculated, 0.001));

      // Test different XY and yaw
      testFootPose.getPosition().set(-0.48, 0.510, 0.0);
      testFootPose.getOrientation().setYawPitchRoll(Math.toRadians(70-3*(11/70) + 5), 0.0, 0.0);
      nearestCheckpointTrue.getPosition().set(-0.500, 0.500, 0.0);
      nearestCheckpointTrue.getOrientation().setYawPitchRoll(Math.toRadians(70-3*(11/70)), 0.0, 0.0);
      nearestCheckpointCalculated = reachabilityChecker.findNearestCheckpoint(testFootPose, reachabilityMap.keySet());
      assertTrue(nearestCheckpointTrue.geometricallyEquals(nearestCheckpointCalculated, 0.001));
   }

   private Map<FramePose3D, Boolean> populateReachabilityMap()
   {
      Map<FramePose3D, Boolean> map = new HashMap<>();

      int queriesPerAxis = 10;
      double minimumOffsetX = -0.5;
      double maximumOffsetX = 0.5;
      double minimumOffsetY = -0.5;
      double maximumOffsetY = 0.5;
      double minimumOffsetYaw = - Math.toRadians(70.0);
      double maximumOffsetYaw = Math.toRadians(70.0);

      for (int i = 0; i < queriesPerAxis; i++)
      {
         for (int j = 0; j < queriesPerAxis; j++)
         {
            for (int k = 0; k < queriesPerAxis; k++)
            {
               double alphaX = ((double) i) / (queriesPerAxis - 1);
               double alphaY = ((double) j) / (queriesPerAxis - 1);
               double alphaYaw = ((double) k) / (queriesPerAxis - 1);

               double x = EuclidCoreTools.interpolate(minimumOffsetX, maximumOffsetX, alphaX);
               double y = EuclidCoreTools.interpolate(minimumOffsetY, maximumOffsetY, alphaY);
               double yaw = AngleTools.interpolateAngle(minimumOffsetYaw, maximumOffsetYaw, alphaYaw);

               FramePose3D pose = new FramePose3D();
               pose.getPosition().set(x, y, 0.0);
               pose.getOrientation().setYawPitchRoll(yaw, 0.0, 0.0);

               // Don't add foot pose where both at origin
               if (pose.getPosition().distanceFromOrigin() != 0)
                  map.put(pose, true);
            }
         }
      }
      return map;
   }

   private static double snapToGrid(double value)
   {
      return LatticePoint.gridSizeXY * Math.round(value / LatticePoint.gridSizeXY);
   }

   private static double snapToYawGrid(double yaw)
   {
      return LatticePoint.gridSizeYaw * Math.floorMod((int) (Math.round((yaw) / LatticePoint.gridSizeYaw)), LatticePoint.yawDivisions);
   }

   private static double snapDownToYaw(double yaw)
   {
      return LatticePoint.gridSizeYaw * Math.floor(yaw / LatticePoint.gridSizeYaw);
   }

   private static double snapDownToGrid(double yaw)
   {
      return LatticePoint.gridSizeXY * Math.floor(yaw / LatticePoint.gridSizeXY);
   }

   private static double snapUpToYaw(double yaw)
   {
      return LatticePoint.gridSizeYaw * Math.ceil(yaw / LatticePoint.gridSizeYaw);
   }

   public static Map<FramePose3D, Boolean> loadFeasabilityMap()
   {
      String logDirectory = System.getProperty("user.home") + File.separator + ".ihmc" + File.separator + "logs" + File.separator;
      String reachabilityDataFileName = logDirectory + "StepReachabilityMap.txt";
      Map<FramePose3D, Boolean> feasibilityMap = new HashMap<>();

      try
      {
         Scanner scanner = new Scanner(new File(reachabilityDataFileName));
         while(scanner.hasNextLine())
         {
            String line = scanner.nextLine();
            FramePose3D frame = new FramePose3D();

            // Parse to get frame position, orientation and feasibility boolean
            String[] data = line.split(",");
            double posX = Double.parseDouble(data[0]);
            double posY = Double.parseDouble(data[1]);
            double posZ = Double.parseDouble(data[2]);
            frame.getPosition().set(posX, posY, posZ);

            double orX = Double.parseDouble(data[3]);
            double orY = Double.parseDouble(data[4]);
            double orZ = Double.parseDouble(data[5]);
            double orS = Double.parseDouble(data[6]);
            frame.getOrientation().set(orX, orY, orZ, orS);

            feasibilityMap.put(frame, line.contains("true"));
         }
         scanner.close();
         System.out.println("Done loading from file");
         return feasibilityMap;
      }
      catch (FileNotFoundException e)
      {
         e.printStackTrace();
      }
      return null;
   }
}
