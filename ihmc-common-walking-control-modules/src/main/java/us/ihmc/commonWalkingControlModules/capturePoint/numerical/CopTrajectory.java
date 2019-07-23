package us.ihmc.commonWalkingControlModules.capturePoint.numerical;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjDoubleConsumer;

import org.apache.commons.math3.util.Precision;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import us.ihmc.commons.lists.RecyclingArrayList;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DReadOnly;
import us.ihmc.euclid.geometry.tools.EuclidGeometryTools;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DBasics;
import us.ihmc.euclid.tuple2D.interfaces.Point2DReadOnly;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition.GraphicType;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPosition;
import us.ihmc.log.LogTools;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoFramePoint2D;

public class CopTrajectory implements ObjDoubleConsumer<Point2DBasics>
{
   /**
    * Distance for checks whether CoP waypoint is within a support. This should be small but positive to make sure that
    * a toe off polygon for example is considered connected to the full foot polygon causing the CoP to move there
    * instead of jump.
    */
   private static final double EPSILON = 0.001;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final RecyclingArrayList<Point2D> waypoints = new RecyclingArrayList<>(50, Point2D.class);
   private final TDoubleArrayList waypointTimes = new TDoubleArrayList();

   private final List<YoFramePoint2D> yoWaypoints = new ArrayList<>();

   public CopTrajectory()
   {
      this(null, null);
   }

   public CopTrajectory(YoVariableRegistry parentRegistry, YoGraphicsListRegistry graphicsRegistry)
   {
      for (int i = 0; i < 10; i++)
         yoWaypoints.add(new YoFramePoint2D("CopWaypoint" + i, ReferenceFrame.getWorldFrame(), registry));

      if (graphicsRegistry != null)
      {
         for (int i = 0; i < yoWaypoints.size(); i++)
         {
            YoArtifactPosition artifact = new YoArtifactPosition("CopWaypoint" + i, yoWaypoints.get(i), GraphicType.DIAMOND, Color.CYAN, 0.002);
            graphicsRegistry.registerArtifact("CopWaypoints", artifact);
         }
      }

      if (parentRegistry != null)
         parentRegistry.addChild(registry);
   }

   public void set(Point2DReadOnly constantCop)
   {
      clear();

      waypoints.add().set(constantCop);
      waypointTimes.add(0.0);

      updateViz();
   }

   public void set(List<ConvexPolygon2D> supportPolygons, TDoubleList supportTimes, double finalTransferDuration)
   {
      update(supportPolygons, supportTimes, finalTransferDuration, 0.0, supportPolygons.get(0).getCentroid());
   }

   public void update(SupportSeqence supportSeqence, double finalTransferDuration, double timeInSequence, Point2DReadOnly initialCop)
   {
      update(supportSeqence.getSupportPolygons(), supportSeqence.getSupportTimes(), finalTransferDuration, timeInSequence, initialCop);
   }

   public void update(List<? extends ConvexPolygon2DReadOnly> supportPolygons, TDoubleList supportTimes, double finalTransferDuration, double timeInSequence,
                      Point2DReadOnly initialCop)
   {
      clear();

      waypoints.add().set(supportPolygons.get(0).getCentroid());
      waypointTimes.add(0.0);

      // Waypoint at end of support is as close to next support center as possible.
      for (int i = 1; i < supportPolygons.size(); i++)
      {
         ConvexPolygon2DReadOnly previousPolygon = supportPolygons.get(i - 1);
         ConvexPolygon2DReadOnly polygon = supportPolygons.get(i);
         Point2DReadOnly centroid = polygon.getCentroid();
         Point2DReadOnly lastWaypoint = waypoints.get(waypoints.size() - 1);

         if (previousPolygon.isPointInside(centroid, EPSILON))
         {
            waypoints.add().set(centroid);
            waypointTimes.add(supportTimes.get(i));
         }
         else if (polygon.isPointInside(lastWaypoint, EPSILON))
         {
            waypoints.add().set(lastWaypoint);
            waypointTimes.add(supportTimes.get(i));

            // Check if the next polygon needs to be added. Consider going from heel to foot to heel. In that case we would like to add the
            // foot centroid. However, when going from heel to foot to toe we do not.
            if (i < supportPolygons.size() - 1)
            {
               ConvexPolygon2DReadOnly nextPolygon = supportPolygons.get(i + 1);
               Point2DReadOnly nextCentroid = nextPolygon.getCentroid();

               // If this distance is small the trajectory will pass by the centroid of the polygon we are looking at.
               double distance = EuclidGeometryTools.distanceFromPoint2DToLineSegment2D(centroid, lastWaypoint, nextCentroid);
               if (distance * distance > 0.1 * polygon.getArea())
               {
                  // We are not passing close by the centroid. This means we should add it halfway through the next support phase
                  double supportDuration = i < supportTimes.size() - 1 ? supportTimes.get(i + 1) - supportTimes.get(i) : finalTransferDuration;
                  waypoints.add().set(centroid);
                  waypointTimes.add(supportTimes.get(i) + supportDuration / 2.0);
               }
            }
         }
         else
         {
            LogTools.warn("Discontinuous support sequence! Expect bad plans.");
            waypoints.add().set(lastWaypoint);
            waypointTimes.add(supportTimes.get(i));
            waypoints.add().set(centroid);
            waypointTimes.add(supportTimes.get(i));
         }
      }

      // Last waypoint is at center of final support.
      waypoints.add().set(supportPolygons.get(supportPolygons.size() - 1).getCentroid());
      waypointTimes.add(supportTimes.get(supportTimes.size() - 1) + finalTransferDuration);

      // Add in the initial CoP at the specified time:
      int index = 0;
      while (index < waypointTimes.size() && waypointTimes.get(index) < timeInSequence)
         index++;

      if (index == waypointTimes.size())
      {
         waypoints.add().set(initialCop);
         waypointTimes.add(timeInSequence);
      }
      else if (Precision.equals(timeInSequence, waypointTimes.get(index)))
      {
         waypoints.get(index).set(initialCop);
         waypointTimes.replace(index, timeInSequence);
      }
      else
      {
         waypoints.insertAtIndex(index).set(initialCop);
         waypointTimes.insert(index, timeInSequence);
      }

      updateViz();
   }

   private void updateViz()
   {
      int max = Math.min(yoWaypoints.size(), waypoints.size());
      for (int i = 0; i < max; i++)
         yoWaypoints.get(i).set(waypoints.get(i));
      for (int i = max; i < yoWaypoints.size(); i++)
         yoWaypoints.get(i).setToNaN();
   }

   private void clear()
   {
      waypoints.clear();
      waypointTimes.reset();
   }

   @Override
   public void accept(Point2DBasics copToPack, double time)
   {
      if (time < waypointTimes.get(0))
         throw new RuntimeException("Requested at time " + time + " but first waypoint was at " + waypointTimes.get(0));

      if (time >= waypointTimes.get(waypoints.size() - 1))
      {
         copToPack.set(waypoints.get(waypoints.size() - 1));
         return;
      }

      int segmentIndex = 0;
      while (waypointTimes.get(segmentIndex + 1) < time)
         segmentIndex++;

      double timeInSegment = time - waypointTimes.get(segmentIndex);
      double segmentDuration = waypointTimes.get(segmentIndex + 1) - waypointTimes.get(segmentIndex);
      double percentInSegment = timeInSegment / segmentDuration;
      copToPack.interpolate(waypoints.get(segmentIndex), waypoints.get(segmentIndex + 1), percentInSegment);
   }
}
