package us.ihmc.footstepPlanning.bodyPath;

import us.ihmc.euclid.Axis3D;
import us.ihmc.euclid.referenceFrame.FrameBox3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tools.EuclidCoreTools;
import us.ihmc.euclid.tuple2D.Vector2D;
import us.ihmc.euclid.tuple2D.interfaces.Vector2DBasics;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DBasics;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.UnitVector3DBasics;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicShape;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsList;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.robotics.geometry.AngleTools;
import us.ihmc.sensorProcessing.heightMap.HeightMapData;
import us.ihmc.sensorProcessing.heightMap.HeightMapTools;
import us.ihmc.yoVariables.euclid.YoVector2D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint3D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoseUsingYawPitchRoll;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

import java.util.List;

import static us.ihmc.footstepPlanning.bodyPath.AStarBodyPathSmoother.collisionWeight;
import static us.ihmc.footstepPlanning.bodyPath.AStarBodyPathSmoother.rollWeight;

public class AStarBodyPathSmootherWaypoint
{
   private static final double boxSizeX = 0.3;
   private static final double boxSizeY = 0.8;
   private static final double boxGroundOffset = 0.2;
   private final FrameBox3D collisionBox = new FrameBox3D();

   private static final AppearanceDefinition collisionBoxColor = YoAppearance.RGBColorFromHex(0x824e38);
   private final boolean visualize;

   private final int waypointIndex;
   private final YoFramePoseUsingYawPitchRoll waypoint;
   private final HeightMapLeastSquaresNormalCalculator surfaceNormalCalculator;

   private final YoDouble headingFromPrevious;
   private final YoDouble headingToNext;
   private final YoDouble maxCollision;
   private final YoDouble alphaRoll;

   private AStarBodyPathSmootherWaypoint previous, next;

   private final YoVector2D rollDelta;

   public AStarBodyPathSmootherWaypoint(int waypointIndex,
                                        HeightMapLeastSquaresNormalCalculator surfaceNormalCalculator,
                                        YoGraphicsListRegistry graphicsListRegistry,
                                        YoRegistry parentRegistry)
   {
      collisionBox.getSize().set(boxSizeX, boxSizeY, 0.6);
      this.surfaceNormalCalculator = surfaceNormalCalculator;

      YoRegistry registry = new YoRegistry("Waypoint" + waypointIndex);
      this.waypointIndex = waypointIndex;
      this.headingFromPrevious = new YoDouble("headingPrev" + waypointIndex, registry);
      this.headingToNext = new YoDouble("headingToNext" + waypointIndex, registry);
      this.maxCollision = new YoDouble("maxCollision" + waypointIndex, registry);
      this.alphaRoll = new YoDouble("alphaRoll" + waypointIndex, registry);
      this.rollDelta = new YoVector2D("deltaRoll" + waypointIndex, registry);

      visualize = parentRegistry != null;
      waypoint = new YoFramePoseUsingYawPitchRoll("waypoint" + waypointIndex, ReferenceFrame.getWorldFrame(), registry);

      if (visualize)
      {
         YoGraphicPosition waypointGraphic = new YoGraphicPosition("waypointViz" + waypointIndex, waypoint.getPosition(), 0.02, YoAppearance.Red());
         Graphics3DObject collisionBoxGraphic = new Graphics3DObject();
         collisionBoxColor.setTransparency(0.6);
         collisionBoxGraphic.addCube(collisionBox.getSizeX(), collisionBox.getSizeY(), collisionBox.getSizeZ(), true, collisionBoxColor);
         YoGraphicShape yoCollisionBoxGraphic = new YoGraphicShape("collisionGraphic" + waypointIndex, collisionBoxGraphic, waypoint, 1.0);

         graphicsListRegistry.registerYoGraphic("Waypoints", waypointGraphic);
         graphicsListRegistry.registerYoGraphic("Collisions", yoCollisionBoxGraphic);

         parentRegistry.addChild(registry);
      }
   }

   public void initialize(List<Point3D> bodyPath)
   {
      if (waypointIndex < bodyPath.size())
      {
         waypoint.getPosition().set(bodyPath.get(waypointIndex));

         if (waypointIndex == 0 || waypointIndex == bodyPath.size() - 1)
         {
            // hide collision boxes
            waypoint.setOrientationYawPitchRoll(Double.NaN, Double.NaN, Double.NaN);
         }
      }
      else
      {
         waypoint.setToNaN();
      }
   }

   public void setNeighbors(AStarBodyPathSmootherWaypoint previous, AStarBodyPathSmootherWaypoint next)
   {
      this.previous = previous;
      this.next = next;
   }

   public void setHeading(double headingFromPrev, double headingToNext)
   {
      this.headingFromPrevious.set(headingFromPrev);
      this.headingToNext.set(headingToNext);
      this.waypoint.setYaw(AngleTools.computeAngleAverage(headingFromPrev, headingToNext));
   }

   public double getHeading()
   {
      return waypoint.getYaw();
   }

   public double getHeadingFromPrevious()
   {
      return headingFromPrevious.getDoubleValue();
   }

   public double getHeadingToNext()
   {
      return headingToNext.getDoubleValue();
   }

   public Point3DBasics getPosition()
   {
      return waypoint.getPosition();
   }

   public Vector2D computeCollisionGradient(HeightMapData heightMapData)
   {
      int maxOffset = (int) Math.round(0.5 * EuclidCoreTools.norm(boxSizeX, boxSizeY) / heightMapData.getGridResolutionXY());

      double waypointX = waypoint.getX();
      double waypointY = waypoint.getY();
      double waypointZ = waypoint.getZ();
      double heading = waypoint.getYaw();

      double sH = Math.sin(heading);
      double cH = Math.cos(heading);

      int indexX = HeightMapTools.coordinateToIndex(waypointX, heightMapData.getGridCenter().getX(), heightMapData.getGridResolutionXY(), heightMapData.getCenterIndex());
      int indexY = HeightMapTools.coordinateToIndex(waypointY, heightMapData.getGridCenter().getY(), heightMapData.getGridResolutionXY(), heightMapData.getCenterIndex());

      Vector2D gradient = new Vector2D();
      int numCollisions = 0;
      double heightThreshold = waypointZ + boxGroundOffset;
      maxCollision.set(0.0);

      for (int xi = -maxOffset; xi <= maxOffset; xi++)
      {
         for (int yi = -maxOffset; yi <= maxOffset; yi++)
         {
            int indexXI = indexX + xi;
            int indexYI = indexY + yi;

            if (indexXI < 0 || indexXI >= heightMapData.getCellsPerAxis() || indexYI < 0 || indexYI >= heightMapData.getCellsPerAxis())
            {
               continue;
            }

            double px = HeightMapTools.indexToCoordinate(indexXI, heightMapData.getGridCenter().getX(), heightMapData.getGridResolutionXY(), heightMapData.getCenterIndex());
            double py = HeightMapTools.indexToCoordinate(indexYI, heightMapData.getGridCenter().getY(), heightMapData.getGridResolutionXY(), heightMapData.getCenterIndex());

            double dx = px - waypointX;
            double dy = py - waypointY;

            double dxLocal = cH * dx + sH * dy;
            double dyLocal = -sH * dx + cH * dy;

            if (Math.abs(dxLocal) > 0.5 * boxSizeX || Math.abs(dyLocal) > 0.5 * boxSizeY)
            {
               continue;
            }

            double height = heightMapData.getHeightAt(indexXI, indexYI);
            if (height < heightThreshold)
            {
               continue;
            }

            double lateralPenetration = 0.5 * boxSizeY - Math.abs(dyLocal);
            maxCollision.set(Math.max(maxCollision.getValue(), lateralPenetration));

            gradient.addX(Math.signum(dyLocal) * -lateralPenetration * sH);
            gradient.addY(Math.signum(dyLocal) * lateralPenetration * cH);
            numCollisions++;
         }
      }

      if (numCollisions > 0)
      {
         gradient.scale(collisionWeight / numCollisions);
      }

      return gradient;
   }

   public Vector2DBasics computeRollInclineGradient(HeightMapData heightMapData)
   {
      UnitVector3DBasics surfaceNormal = surfaceNormalCalculator.getSurfaceNormal(HeightMapTools.coordinateToKey(waypoint.getX(),
                                                                                                                 waypoint.getY(),
                                                                                                                 heightMapData.getGridCenter().getX(),
                                                                                                                 heightMapData.getGridCenter().getY(),
                                                                                                                 heightMapData.getGridResolutionXY(),
                                                                                                                 heightMapData.getCenterIndex()));

      if (surfaceNormal != null)
      {
         Vector3D yLocal = new Vector3D();
         yLocal.setX(-Math.sin(waypoint.getYaw()));
         yLocal.setY(Math.cos(waypoint.getYaw()));
         yLocal.scale(yLocal.dot(surfaceNormal));

         double alphaIncline = Math.atan2(next.getPosition().getZ() - previous.getPosition().getZ(), previous.getPosition().distanceXY(next.getPosition()));
         double inclineScale = EuclidCoreTools.clamp((Math.abs(alphaIncline) - Math.toRadians(5.0)) / Math.toRadians(22.0), 0.0, 1.0);
         rollDelta.set(rollWeight * inclineScale * yLocal.getX(), rollWeight * inclineScale * yLocal.getY());
      }
      else
      {
         rollDelta.setToZero();
      }

      return rollDelta;
   }

   public double getMaxCollision()
   {
      return maxCollision.getValue();
   }
}