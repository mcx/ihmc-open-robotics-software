package us.ihmc.footstepPlanning.bodyPath;

import us.ihmc.commons.MathTools;
import us.ihmc.euclid.Axis3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.euclid.referenceFrame.FrameBox3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.tools.EuclidCoreTools;
import us.ihmc.euclid.tuple2D.Vector2D;
import us.ihmc.euclid.tuple2D.interfaces.Vector2DBasics;
import us.ihmc.euclid.tuple2D.interfaces.Vector2DReadOnly;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.UnitVector3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DBasics;
import us.ihmc.euclid.tuple3D.interfaces.Tuple3DReadOnly;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.*;
import us.ihmc.perception.OpenCLFloatBuffer;
import us.ihmc.perception.OpenCLIntBuffer;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.sensorProcessing.heightMap.HeightMapData;
import us.ihmc.sensorProcessing.heightMap.HeightMapTools;
import us.ihmc.yoVariables.euclid.YoVector2D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint3D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoseUsingYawPitchRoll;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFrameVector3D;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoInteger;

import java.util.List;

import static us.ihmc.footstepPlanning.bodyPath.AStarBodyPathPlanner.boxSizeX;
import static us.ihmc.footstepPlanning.bodyPath.AStarBodyPathPlanner.boxSizeY;
import static us.ihmc.footstepPlanning.bodyPath.AStarBodyPathSmoother.*;

public class GPUAStarBodyPathSmootherWaypoint
{
    private static final FrameBox3D collisionBox = new FrameBox3D();

   static
   {
      collisionBox.getSize().set(boxSizeX, boxSizeY, 0.6);
   }

   private static final AppearanceDefinition collisionBoxColor = YoAppearance.RGBColorFromHex(0x824e38);
   private final boolean visualize;

   private final int waypointIndex;
   private final YoFramePoint3D initialWaypoint;
   private final YoFramePoseUsingYawPitchRoll waypoint;
   private final PoseReferenceFrame waypointFrame;
   private final SideDependentList<ReferenceFrame> nominalStepFrames;
   private final YoBoolean isTurnPoint;
   private int previousCellKey, cellKey, dataKey;

   private final SideDependentList<YoDouble> traversibilitySamplePos;
   private final SideDependentList<YoDouble> traversibilitySampleNeg;
   private final SideDependentList<YoDouble> traversibilitySampleNominal;

   private final YoDouble maxCollision;
   private final YoDouble alphaRoll;
   private final YoDouble sampledLSNormalHeight;
   private final YoDouble elevationIncline;

   private static final double gradientGraphicScale = 0.17;
   private static final AppearanceDefinition smoothnessColor = YoAppearance.Blue();
   private static final AppearanceDefinition spacingColor = YoAppearance.Green();
   private static final AppearanceDefinition collisionColor = YoAppearance.Crimson();
   private static final AppearanceDefinition rollColor = YoAppearance.Red();
   private static final AppearanceDefinition traversibilityColor = YoAppearance.Violet();
   private static final AppearanceDefinition displacementColor = YoAppearance.White();
   private static final AppearanceDefinition groundPlaneColor = YoAppearance.Orange();
   private final YoGraphicPosition waypointGraphic, turnPointGraphic;

   private final YoFrameVector3D yoSmoothnessGradient;
   private final YoFrameVector3D yoEqualSpacingGradient;
   private final YoFrameVector3D yoCollisionGradient;
   private final YoFrameVector3D yoRollGradient;
   private final YoFrameVector3D yoDisplacementGradient;
   private final YoFrameVector3D yoGroundPlaneGradient;
   private final YoFrameVector3D yoTraversibilityGradient;
   private final YoFrameVector3D yoSurfaceNormal;

   private final SideDependentList<YoFramePoseUsingYawPitchRoll> yoNominalStepPoses;
   private final SideDependentList<YoFrameVector3D> yoNominalTraversibility;
   private final FrameVector3D tempVector = new FrameVector3D();

   private final SideDependentList<YoFramePoint3D> yoElevatedStepPositions;
   private final SideDependentList<YoFrameVector3D> yoSidedTraversibility;
   private final SideDependentList<YoInteger> yoGroundPlaneCells;

   private int pathSize;
   private GPUAStarBodyPathSmootherWaypoint[] waypoints;
   private final YoVector2D rollDelta;

   public GPUAStarBodyPathSmootherWaypoint(int waypointIndex,
                                           YoGraphicsListRegistry graphicsListRegistry,
                                           YoRegistry parentRegistry)
   {
      this.waypointIndex = waypointIndex;

      YoRegistry registry = new YoRegistry("Waypoint" + waypointIndex);
      maxCollision = new YoDouble("maxCollision" + waypointIndex, registry);
      alphaRoll = new YoDouble("alphaRoll" + waypointIndex, registry);
      rollDelta = new YoVector2D("deltaRoll" + waypointIndex, registry);
      isTurnPoint = new YoBoolean("isTurnPoint" + waypointIndex, registry);
      sampledLSNormalHeight = new YoDouble("sampledLSNormalHeight" + waypointIndex, registry);
      elevationIncline = new YoDouble("elevationIncline" + waypointIndex, registry);

      visualize = parentRegistry != null;
      waypoint = new YoFramePoseUsingYawPitchRoll("waypoint" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      initialWaypoint = new YoFramePoint3D("initWaypoint" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      waypointFrame = new PoseReferenceFrame("waypointFrame" + waypointIndex, ReferenceFrame.getWorldFrame());
      nominalStepFrames = new SideDependentList<>(side -> ReferenceFrameTools.constructFrameWithUnchangingTranslationFromParent(side.getCamelCaseNameForStartOfExpression() + "nominalStepFrame" + waypointIndex, waypointFrame, new Vector3D(0.0, side.negateIfRightSide(halfStanceWidthTraversibility), 0.0)));

      yoSmoothnessGradient = new YoFrameVector3D("smoothGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoEqualSpacingGradient = new YoFrameVector3D("spacingGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoCollisionGradient = new YoFrameVector3D("collisionGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoRollGradient = new YoFrameVector3D("rollGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoDisplacementGradient = new YoFrameVector3D("displacementGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoGroundPlaneGradient = new YoFrameVector3D("groundPlaneGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoSurfaceNormal = new YoFrameVector3D("surfaceNormal" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);
      yoNominalTraversibility = new SideDependentList<>(side -> new YoFrameVector3D(side.getCamelCaseNameForStartOfExpression() + "TravNominal" + waypointIndex, ReferenceFrame.getWorldFrame(), registry));
      yoTraversibilityGradient = new YoFrameVector3D("traversibilityGradient" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);

      traversibilitySampleNeg = new SideDependentList<>(side -> new YoDouble(side.getCamelCaseNameForStartOfExpression() + "TravSampleNeg" + waypointIndex, registry));
      traversibilitySamplePos = new SideDependentList<>(side -> new YoDouble(side.getCamelCaseNameForStartOfExpression() + "TravSamplePos" + waypointIndex, registry));
      traversibilitySampleNominal = new SideDependentList<>(side -> new YoDouble(side.getCamelCaseNameForStartOfExpression() + "TravSampleNominal" + waypointIndex, registry));

      yoSidedTraversibility = new SideDependentList<>(side -> new YoFrameVector3D(side.getCamelCaseNameForStartOfExpression() + "DebugTrav" + waypointIndex, ReferenceFrame.getWorldFrame(), registry));

      yoGroundPlaneCells = new SideDependentList<>(side -> new YoInteger(side.getCamelCaseNameForStartOfExpression() + "GroundCells" + waypointIndex, registry));

      if (visualize)
      {
         yoNominalStepPoses = new SideDependentList<>(side -> new YoFramePoseUsingYawPitchRoll(side.getCamelCaseNameForStartOfExpression() + "nominalStepPose" + waypointIndex, ReferenceFrame.getWorldFrame(), registry));
         yoElevatedStepPositions = new SideDependentList<>(side -> new YoFramePoint3D(side.getCamelCaseNameForStartOfExpression() + "DebugStepPose" + waypointIndex, ReferenceFrame.getWorldFrame(), registry));

         waypointGraphic = new YoGraphicPosition("waypointViz" + waypointIndex, waypoint.getPosition(), 0.02, YoAppearance.Red());
         turnPointGraphic = new YoGraphicPosition("turnPointViz" + waypointIndex, waypoint.getPosition(), 0.02, YoAppearance.White());
         Graphics3DObject collisionBoxGraphic = new Graphics3DObject();
         collisionBoxColor.setTransparency(0.6);
         collisionBoxGraphic.addCube(collisionBox.getSizeX(), collisionBox.getSizeY(), collisionBox.getSizeZ(), true, collisionBoxColor);
         YoGraphicShape yoCollisionBoxGraphic = new YoGraphicShape("collisionGraphic" + waypointIndex, collisionBoxGraphic, waypoint, 1.0);

         YoGraphicCoordinateSystem waypointOrientedGraphic = new YoGraphicCoordinateSystem("waypointCoordViz" + waypointIndex, waypoint, 0.2);
         YoGraphicVector surfaceNormal = new YoGraphicVector("surfaceNormal" + waypointIndex, waypoint.getPosition(), yoSurfaceNormal, 0.3);

         for (RobotSide side : RobotSide.values)
         {
            YoGraphicCoordinateSystem nominalStepPose = new YoGraphicCoordinateSystem(side.getCamelCaseNameForStartOfExpression() + "stepPoseViz" + waypointIndex, yoNominalStepPoses.get(side), 0.2);
            YoGraphicVector nominalTraversibilityViz = new YoGraphicVector(side.getCamelCaseNameForStartOfExpression() + "nominalTraversibilityViz" + waypointIndex, yoNominalStepPoses.get(side).getPosition(), yoNominalTraversibility.get(side), 0.5, YoAppearance.Yellow());
            YoGraphicVector debugTravViz = new YoGraphicVector(side.getCamelCaseNameForStartOfExpression() + "debugTravViz" + waypointIndex, yoElevatedStepPositions.get(side), yoSidedTraversibility.get(side), 0.5);

            graphicsListRegistry.registerYoGraphic("Step Poses", nominalStepPose);
            graphicsListRegistry.registerYoGraphic("Step Poses", nominalTraversibilityViz);
            graphicsListRegistry.registerYoGraphic("Debug Trav", debugTravViz);
         }

         graphicsListRegistry.registerYoGraphic("Waypoints", waypointGraphic);
         graphicsListRegistry.registerYoGraphic("Waypoints", turnPointGraphic);
         graphicsListRegistry.registerYoGraphic("Collisions", yoCollisionBoxGraphic);
         graphicsListRegistry.registerYoGraphic("Normals", waypointOrientedGraphic);
         graphicsListRegistry.registerYoGraphic("Normals", surfaceNormal);

         YoGraphicVector smoothnessGradientViz = new YoGraphicVector("smoothnessGradientViz" + waypointIndex, waypoint.getPosition(), yoSmoothnessGradient, gradientGraphicScale, smoothnessColor);
         YoGraphicVector equalSpacingGradientViz = new YoGraphicVector("spacingGradientViz" + waypointIndex, waypoint.getPosition(), yoEqualSpacingGradient, gradientGraphicScale, spacingColor);
         YoGraphicVector collisionGradientViz = new YoGraphicVector("collisionGradientViz" + waypointIndex, waypoint.getPosition(), yoCollisionGradient, gradientGraphicScale, collisionColor);
         YoGraphicVector rollGradientViz = new YoGraphicVector("rollGradientViz" + waypointIndex, waypoint.getPosition(), yoRollGradient, gradientGraphicScale, rollColor);
         YoGraphicVector displacementGradientViz = new YoGraphicVector("displacementGradientViz" + waypointIndex, waypoint.getPosition(), yoDisplacementGradient, gradientGraphicScale, displacementColor);
         YoGraphicVector traversibilityGradientViz = new YoGraphicVector("traversibilityGradientViz" + waypointIndex, waypoint.getPosition(), yoTraversibilityGradient, gradientGraphicScale, traversibilityColor);
         YoGraphicVector groundPlaneGradientViz = new YoGraphicVector("groundGradientViz" + waypointIndex, waypoint.getPosition(), yoGroundPlaneGradient, gradientGraphicScale, groundPlaneColor);

         graphicsListRegistry.registerYoGraphic("Smoothness Gradient", smoothnessGradientViz);
         graphicsListRegistry.registerYoGraphic("Spacing Gradient", equalSpacingGradientViz);
         graphicsListRegistry.registerYoGraphic("Collision Gradient", collisionGradientViz);
         graphicsListRegistry.registerYoGraphic("Roll Gradient", rollGradientViz);
         graphicsListRegistry.registerYoGraphic("Displacement Gradient", displacementGradientViz);
         graphicsListRegistry.registerYoGraphic("Traversibility Gradient", traversibilityGradientViz);
         graphicsListRegistry.registerYoGraphic("Ground Plane Gradient", groundPlaneGradientViz);

         parentRegistry.addChild(registry);
      }
      else
      {
         yoNominalStepPoses = null;
         yoElevatedStepPositions = null;

         waypointGraphic = null;
         turnPointGraphic = null;
      }
   }

   private int getMapBufferKey(HeightMapData heightMapData)
   {
      return HeightMapTools.coordinateToKey(waypoint.getX(),
                                            waypoint.getY(),
                                            heightMapData.getGridCenter().getX(),
                                            heightMapData.getGridCenter().getY(),
                                            heightMapData.getGridResolutionXY(),
                                            heightMapData.getCenterIndex());
   }

   private int getDataBufferKey(HeightMapData heightMapData)
   {
      int mapKey = getMapBufferKey(heightMapData);
      int yawIndex = GPUAStarBodyPathSmoother.yawToIndex(waypoint.getYaw());
      return mapKey * GPUAStarBodyPathSmoother.yawDiscretizations + yawIndex;
   }

   public void initialize(List<Point3D> bodyPath)
   {
      this.pathSize = bodyPath.size();

      isTurnPoint.set(false);

      for (RobotSide side : RobotSide.values)
      {
         if (waypointIndex == 0 || waypointIndex == pathSize - 1)
         {
            traversibilitySampleNominal.get(side).set(1.0);
         }
         else
         {
            traversibilitySampleNominal.get(side).set(0.0);
         }
      }


      if (visualize)
      {
         waypointGraphic.showGraphicObject();
         turnPointGraphic.hideGraphicObject();
      }

      if (waypointIndex < bodyPath.size())
      {
         initialWaypoint.set(bodyPath.get(waypointIndex));
         waypoint.getPosition().set(bodyPath.get(waypointIndex));

         if (waypointIndex == 0 || waypointIndex == bodyPath.size() - 1)
         {
            // hide collision boxes
            waypoint.setOrientationYawPitchRoll(Double.NaN, Double.NaN, Double.NaN);
         }
      }
      else
      {
         initialWaypoint.setToNaN();
         waypoint.setToNaN();
      }

      yoSmoothnessGradient.setToNaN();
      yoEqualSpacingGradient.setToNaN();
      yoCollisionGradient.setToNaN();
      yoRollGradient.setToNaN();
      yoDisplacementGradient.setToNaN();
      yoGroundPlaneGradient.setToNaN();
   }

   public void setNeighbors(GPUAStarBodyPathSmootherWaypoint[] waypoints)
   {
      this.waypoints = waypoints;
   }

   public double getHeading()
   {
      return waypoint.getYaw();
   }

   public Point3DBasics getPosition()
   {
      return waypoint.getPosition();
   }

   public Pose3DReadOnly getPose()
   {
      return waypoint;
   }

   public void setTurnPoint()
   {
      this.isTurnPoint.set(true);

      if (visualize)
      {
         turnPointGraphic.showGraphicObject();
         waypointGraphic.hideGraphicObject();
      }
   }

   public boolean isTurnPoint()
   {
      return isTurnPoint.getValue();
   }

   public Vector2DReadOnly computeCollisionGradient(HeightMapData heightMapData, OpenCLIntBuffer maxCollisionsBuffer, OpenCLFloatBuffer collisionGradientsBuffer)
   {
      int key = getDataBufferKey(heightMapData);
      maxCollision.set(maxCollisionsBuffer.getBackingDirectIntBuffer().get(key));

      Vector2D gradient = new Vector2D();
      gradient.setX(collisionGradientsBuffer.getBackingDirectFloatBuffer().get(2 * key));
      gradient.setY(collisionGradientsBuffer.getBackingDirectFloatBuffer().get(2 * key + 1));

      if (visualize)
      {
         yoCollisionGradient.set(-gradient.getX(), -gradient.getY(), 0.0);
      }

      return gradient;
   }

   public Vector2DReadOnly computeRollInclineGradient(OpenCLFloatBuffer leastSquaresNormalXYZBuffer, OpenCLFloatBuffer leastSquaresSampledHeightBuffer)
   {
      UnitVector3D surfaceNormal = new UnitVector3D();
      surfaceNormal.set(leastSquaresNormalXYZBuffer.getBackingDirectFloatBuffer().get(3 * cellKey),
                        leastSquaresNormalXYZBuffer.getBackingDirectFloatBuffer().get(3 * cellKey + 1),
                        leastSquaresNormalXYZBuffer.getBackingDirectFloatBuffer().get(3 * cellKey + 2));

      yoSurfaceNormal.set(surfaceNormal);
      tempVector.setIncludingFrame(waypointFrame, Axis3D.Y);
      tempVector.changeFrame(ReferenceFrame.getWorldFrame());
      double rollDotY = tempVector.dot(surfaceNormal);
      alphaRoll.set(rollDotY);
      tempVector.scale(alphaRoll.getValue());

      GPUAStarBodyPathSmootherWaypoint previous = waypoints[Math.max(0, waypointIndex - 1)];
      GPUAStarBodyPathSmootherWaypoint next = waypoints[Math.min(pathSize - 1, waypointIndex + 1)];

      double incline = Math.atan2(next.getPosition().getZ() - previous.getPosition().getZ(), next.getPosition().distanceXY(previous.getPosition()));
      elevationIncline.set(incline);

      double inclineClipped = EuclidCoreTools.clamp((Math.abs(incline) - Math.toRadians(2.0)) / Math.toRadians(7.0), 0.0, 1.0);
      rollDelta.set(rollWeight * inclineClipped * tempVector.getX(), rollWeight * inclineClipped * tempVector.getY());

      sampledLSNormalHeight.set(leastSquaresSampledHeightBuffer.getBackingDirectFloatBuffer().get(cellKey));

      return rollDelta;
   }

   public Tuple3DReadOnly computeDisplacementGradient()
   {
      tempVector.setToZero(ReferenceFrame.getWorldFrame());
      tempVector.sub(initialWaypoint, waypoint.getPosition());
      tempVector.changeFrame(waypointFrame);
      tempVector.setX(0.0);
      tempVector.changeFrame(ReferenceFrame.getWorldFrame());
      tempVector.scale(displacementWeight);
      yoDisplacementGradient.set(tempVector);

      return tempVector;
   }

   public void computeCurrentTraversibility(OpenCLFloatBuffer leftTraversibilityBuffer, OpenCLFloatBuffer rightTraversibilityBuffer)
   {
      YoDouble leftNominalTraversibility = traversibilitySampleNominal.get(RobotSide.LEFT);
      YoDouble rightNominalTraversibility = traversibilitySampleNominal.get(RobotSide.RIGHT);
      leftNominalTraversibility.set(leftTraversibilityBuffer.getBackingDirectFloatBuffer().get(dataKey));
      rightNominalTraversibility.set(rightTraversibilityBuffer.getBackingDirectFloatBuffer().get(dataKey));
      yoNominalTraversibility.get(RobotSide.LEFT).setZ(leftNominalTraversibility.getValue());
      yoNominalTraversibility.get(RobotSide.RIGHT).setZ(rightNominalTraversibility.getValue());
   }

   public Tuple3DReadOnly computeTraversibilityGradient(OpenCLFloatBuffer leftTraversibilitiesForGradientBuffer,
                                                        OpenCLFloatBuffer rightTraversibilitesForGradientBuffer)
   {
      yoTraversibilityGradient.setToZero();

      for (RobotSide side : RobotSide.values)
      {
         YoFrameVector3D sidedTraversibility = yoSidedTraversibility.get(side);
         sidedTraversibility.setToZero();

         double localTraversibilityThreshold = 0.9;
         double maxLocalTraversibilityThresholdDiscount = 0.75;

         double currentTraversibility = traversibilitySampleNominal.get(side).getValue();
         double previousTraversibility0 = getNeighbor(waypointIndex - 1).traversibilitySampleNominal.get(side).getValue();
         double previousTraversibility1 = getNeighbor(waypointIndex - 2).traversibilitySampleNominal.get(side).getValue();
         double nextTraversibility0 = getNeighbor(waypointIndex + 1).traversibilitySampleNominal.get(side).getValue();
         double nextTraversibility1 = getNeighbor(waypointIndex + 2).traversibilitySampleNominal.get(side).getValue();
         double maxLocalTraversibility = max(currentTraversibility, previousTraversibility0, previousTraversibility1, nextTraversibility0, nextTraversibility1);

         if (maxLocalTraversibility > localTraversibilityThreshold)
         {
            continue;
         }
         OpenCLFloatBuffer sidedBuffer;
         if (side == RobotSide.LEFT)
            sidedBuffer = leftTraversibilitiesForGradientBuffer;
         else
            sidedBuffer = rightTraversibilitesForGradientBuffer;

         traversibilitySampleNeg.get(side).set(sidedBuffer.getBackingDirectFloatBuffer().get(2 * dataKey));
         traversibilitySamplePos.get(side).set(sidedBuffer.getBackingDirectFloatBuffer().get(2 * dataKey + 1));

         double alpha = EuclidCoreTools.clamp((localTraversibilityThreshold - maxLocalTraversibility) / (localTraversibilityThreshold - maxLocalTraversibilityThresholdDiscount), 0.0, 1.0);
         tempVector.setIncludingFrame(waypointFrame, Axis3D.Y);
         tempVector.scale(alpha * (traversibilitySamplePos.get(side).getValue() - traversibilitySampleNeg.get(side).getValue()));
         tempVector.changeFrame(ReferenceFrame.getWorldFrame());

         sidedTraversibility.set(tempVector);
         yoTraversibilityGradient.add(tempVector);
      }

      yoTraversibilityGradient.scale(traversibilityWeight);
      return yoTraversibilityGradient;
   }

   public Tuple3DReadOnly computeGroundPlaneGradient(HeightMapData heightMapData,
                                                     OpenCLIntBuffer leftGroundPlaneCellsBuffer,
                                                     OpenCLIntBuffer rightGroundPlaneCellsBuffer,
                                                     OpenCLFloatBuffer groundPlaneGradientBuffer)
   {
      yoGroundPlaneGradient.setToZero();
      for (RobotSide side : RobotSide.values)
      {
         yoGroundPlaneCells.get(side).set(0);
      }

      double heightThresholdForGround = 0.015;
      double currentHeightAboveGroundPlane = waypoint.getZ() - heightMapData.getEstimatedGroundHeight();
      double nextHeightAboveGroundPlane = getNeighbor(waypointIndex + 1).getPosition().getZ() - heightMapData.getEstimatedGroundHeight();

      if (currentHeightAboveGroundPlane > heightThresholdForGround || nextHeightAboveGroundPlane > heightThresholdForGround)
      {
         return yoGroundPlaneGradient;
      }

      yoGroundPlaneCells.get(RobotSide.LEFT).set(leftGroundPlaneCellsBuffer.getBackingDirectIntBuffer().get(dataKey));
      yoGroundPlaneCells.get(RobotSide.RIGHT).set(rightGroundPlaneCellsBuffer.getBackingDirectIntBuffer().get(dataKey));

      yoGroundPlaneGradient.setX(groundPlaneGradientBuffer.getBackingDirectFloatBuffer().get(2 * dataKey));
      yoGroundPlaneGradient.setY(groundPlaneGradientBuffer.getBackingDirectFloatBuffer().get(2 * dataKey + 1));

      return yoGroundPlaneGradient;
   }

   private static double max(double... x)
   {
      double max = -Double.MAX_VALUE;
      for (int i = 0; i < x.length; i++)
      {
         if (x[i] > max)
            max = x[i];
      }
      return max;
   }

   private GPUAStarBodyPathSmootherWaypoint getNeighbor(int index)
   {
      return waypoints[MathTools.clamp(index, 0, pathSize - 1)];
   }

   public void update(boolean firstTick, HeightMapData heightMapData, OpenCLFloatBuffer snapHeightBUffer)
   {
      // Cell key
      int currentKey = getMapBufferKey(heightMapData);
      previousCellKey = cellKey;
      cellKey = currentKey;
      dataKey = getDataBufferKey(heightMapData);

      if (firstTick)
      {
         previousCellKey = currentKey;
      }

      // Update orientation
      GPUAStarBodyPathSmootherWaypoint previous = waypoints[waypointIndex - 1];
      GPUAStarBodyPathSmootherWaypoint next = waypoints[waypointIndex + 1];

      double x0 = previous.getPosition().getX();
      double y0 = previous.getPosition().getY();
      double x1 = getPosition().getX();
      double y1 = getPosition().getY();
      double x2 = next.getPosition().getX();
      double y2 = next.getPosition().getY();

      double heading0 = Math.atan2(y1 - y0, x1 - x0);
      double heading1 = Math.atan2(y2 - y1, x2 - x1);
      this.waypoint.setOrientationYawPitchRoll(AngleTools.computeAngleAverage(heading0, heading1), 0.0,  0.0);

      // Compute new height if shifted
      if (firstTick || cellKey != previousCellKey)
      {
         computeHeight(heightMapData, snapHeightBUffer);
      }

      // Update frames
      waypointFrame.setPoseAndUpdate(waypoint);
      for (RobotSide side : RobotSide.values)
      {
         nominalStepFrames.get(side).update();

         if (visualize)
         {
            yoNominalStepPoses.get(side).setFromReferenceFrame(nominalStepFrames.get(side));

            yoElevatedStepPositions.get(side).set(yoNominalStepPoses.get(side).getPosition());
            yoElevatedStepPositions.get(side).addZ(0.2);
         }
      }
   }

   private void computeHeight(HeightMapData heightMapData, OpenCLFloatBuffer snappedHeightBuffer)
   {
      int centerIndex = HeightMapTools.computeCenterIndex(heightMapData.getGridSizeXY(), BodyPathLatticePoint.gridSizeXY);
      int xIndex = HeightMapTools.coordinateToIndex(waypoint.getX(), heightMapData.getGridCenter().getX(), BodyPathLatticePoint.gridSizeXY, centerIndex);
      int yIndex = HeightMapTools.coordinateToIndex(waypoint.getY(), heightMapData.getGridCenter().getY(), BodyPathLatticePoint.gridSizeXY, centerIndex);
      int planKey = HeightMapTools.indicesToKey(xIndex, yIndex, centerIndex);

      waypoint.setZ(snappedHeightBuffer.getBackingDirectFloatBuffer().get(planKey));
   }

   public void updateGradientGraphics(double spacingGradientX, double spacingGradientY, double smoothnessGradientX, double smoothnessGradientY)
   {
      yoEqualSpacingGradient.set(-spacingGradientX, -spacingGradientY, 0.0);
      yoSmoothnessGradient.set(-smoothnessGradientX, -smoothnessGradientY, 0.0);
      yoRollGradient.setToZero();
   }

   public void updateRollGraphics(double gradientX, double gradientY)
   {
      yoRollGradient.add(-gradientX, -gradientY, 0.0);
   }

   public double getMaxCollision()
   {
      return maxCollision.getValue();
   }
}
