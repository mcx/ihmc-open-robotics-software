package us.ihmc.robotics.geometry.concaveHull;

import org.junit.jupiter.api.Test;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.tools.EuclidGeometryPolygonTools;
import us.ihmc.euclid.tools.EuclidCoreTestTools;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DReadOnly;
import us.ihmc.robotics.geometry.concavePolygon2D.ConcavePolygon2D;

import java.util.ArrayList;
import java.util.List;

import static us.ihmc.robotics.Assert.assertEquals;

public class ConcavePolygon2DToolsTest
{
   @Test
   public void testConcavePolygonAreaAndCentroid()
   {
      ConvexPolygon2D polygon1 = new ConvexPolygon2D();
      ConvexPolygon2D polygon2 = new ConvexPolygon2D();

      polygon1.addVertex(-1.0, 1.0);
      polygon1.addVertex(-1.0, -1.0);
      polygon1.addVertex(1.0, 1.0);
      polygon1.addVertex(1.0, -1.0);
      polygon1.update();

      polygon2.addVertex(1.0, 0.5);
      polygon2.addVertex(1.0, -0.5);
      polygon2.addVertex(2.0, -0.5);
      polygon2.addVertex(2.0, 0.5);
      polygon2.update();

      ConcavePolygon2D concaveHull = new ConcavePolygon2D();
      concaveHull.addVertex(-1.0, 1.0);
      concaveHull.addVertex(1.0, 1.0);
      concaveHull.addVertex(1.0, 0.5);
      concaveHull.addVertex(2.0, 0.5);
      concaveHull.addVertex(2.0, -0.5);
      concaveHull.addVertex(1.0, -0.5);
      concaveHull.addVertex(1.0, -1.0);
      concaveHull.addVertex(-1.0, -1.0);
      concaveHull.update();

      double totalArea = polygon1.getArea() + polygon2.getArea();
      Point2D totalCentroid = new Point2D();
      Point2D scaledCentroid1 = new Point2D();
      Point2D scaledCentroid2 = new Point2D();
      scaledCentroid1.set(polygon1.getCentroid());
      scaledCentroid1.scale(polygon1.getArea() / totalArea);
      scaledCentroid2.set(polygon2.getCentroid());
      scaledCentroid2.scale(polygon2.getArea() / totalArea);
      totalCentroid.add(scaledCentroid1, scaledCentroid2);

      assertEquals(totalArea, concaveHull.getArea(), 1e-7);
      EuclidCoreTestTools.assertPoint2DGeometricallyEquals(totalCentroid, concaveHull.getCentroid(), 1e-7);
   }
}
