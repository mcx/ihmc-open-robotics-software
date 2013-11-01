package us.ihmc.sensorProcessing.pointClouds.shape;

import bubo.io.serialization.DataDefinition;
import bubo.io.serialization.SerializationDefinitionManager;
import bubo.io.text.ReadCsvObjectSmart;
import bubo.ptcloud.CloudShapeTypes;
import bubo.ptcloud.FactoryPointCloudShape;
import bubo.ptcloud.PointCloudShapeFinder;
import bubo.ptcloud.PointCloudShapeFinder.Shape;
import bubo.ptcloud.alg.ApproximateSurfaceNormals;
import bubo.ptcloud.alg.ConfigSchnabel2007;
import bubo.ptcloud.alg.PointVectorNN;
import bubo.ptcloud.tools.PointCloudShapeTools;
import bubo.ptcloud.wrapper.ConfigRemoveFalseShapes;
import bubo.ptcloud.wrapper.ConfigSurfaceNormals;
import com.jme3.app.SimpleApplication;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.CartoonEdgeFilter;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.BatchNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Box;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import georegression.struct.plane.PlaneGeneral3D_F64;
import georegression.struct.plane.PlaneNormal3D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.point.Vector3D_F64;
import georegression.struct.shapes.Cylinder3D_F64;
import georegression.struct.shapes.Sphere3D_F64;
import org.ddogleg.struct.FastQueue;
import us.ihmc.graphics3DAdapter.jme.util.JMEGeometryUtils;
import us.ihmc.sensorProcessing.pointClouds.shape.ExpectationMaximizationFitter.ScoringFunction;

import java.awt.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Peter Abeles
 */
public class ShapesFromPointCloudFileApp extends SimpleApplication implements RawInputListener
{
   private Random rand = new Random(2134);

   public String fileName;
   private List<Point3D_F64> ransacCloud;

   private Node boundsNode = new Node("meshBounds");
   private float boxExtent = 10.75f;
   private Vector3f initialTranslation = new Vector3f(4.1f, -0.55f, -0.75f);

   private float translateSpeed = 0.1f;
   private ShapeTranslator translator = new ShapeTranslator(this);
   private DirectionalLight primaryLight;
   private List<Point3D_F64> fullCloud;
   private ColorRGBA defaultColor = ColorRGBA.Red;
   private ColorRGBA selectColor = ColorRGBA.Blue;

   private float defaultPointSize = 0.75f;
   private float selectPointSize = 2.0f;

   private Node zUp = new Node();
   private Node shapesNode = new Node();
   private BatchNode normalsNode = new BatchNode();
   private Node allPointCloud = new Node();
   private Node pointCloudNode = new Node();
   private Node selectionNode = new Node();

   private boolean SHADOWS = false;

   ConfigSurfaceNormals configNormal = new ConfigSurfaceNormals(100, 100, .1);

   public static void main(String[] args)
   {
      ShapesFromPointCloudFileApp test1 = new ShapesFromPointCloudFileApp("../SensorProcessing/box_10s_h.txt");
      test1.start();
   }

   public ShapesFromPointCloudFileApp(String fileName)
   {
      this.fileName = fileName;
   }

   @Override
   public void simpleInitApp()
   {
      inputManager.addRawInputListener(this);
      setupLighting();
      zUp.setLocalRotation(JMEGeometryUtils.getRotationFromJMEToZupCoordinates());

      rootNode.attachChild(zUp);
      zUp.attachChild(pointCloudNode);
      zUp.attachChild(allPointCloud);
      zUp.attachChild(shapesNode);
      zUp.attachChild(normalsNode);
      zUp.attachChild(selectionNode);

      fullCloud = new ArrayList<Point3D_F64>();

      /*
       * fullCloud.addAll(createModelCloud(new Cylinder3D_F64(0, 1, 0, 1, 0, 0,
       * .05), 300, .0, 1)); fullCloud.addAll(createModelCloud(new
       * Cylinder3D_F64(0, -2, 0, 1, 0, 0, .5), 1000, .0, 2));
       * fullCloud.addAll(createModelCloud(new PlaneNormal3D_F64(new
       * Point3D_F64(0,-2,0), new Vector3D_F64(1,0,0)), 250, .0, 1));
       * fullCloud.addAll(SyntheticCalibrationTestApp.createBoxCloud(new
       * Point3D_F64(0, 0, 0), 750, 1, 0.0));
       */

      fullCloud.addAll(readPointCloud(10000000, new Vector3f(-99999.0f, -99999.0f, -99999.0f), new Vector3f(99999.0f, 99999.0f, 99999.0f)));

      displayView(fullCloud, defaultColor, defaultPointSize, allPointCloud);

      cam.setFrustumPerspective(45.0f, ((float) cam.getWidth()) / ((float) cam.getHeight()), 0.05f, 100.0f);
      cam.setLocation(new Vector3f(0, 0, -5));
      cam.lookAtDirection(Vector3f.UNIT_Z, Vector3f.UNIT_Y);
      cam.update();
      flyCam.setMoveSpeed(15);
      flyCam.setDragToRotate(true);

      FilterPostProcessor fpp = new FilterPostProcessor(assetManager);

      CartoonEdgeFilter f = new CartoonEdgeFilter();
      fpp.addFilter(f);

      //getViewPort().addProcessor(fpp);
      setupBoxNode();

   }

   private List<Point3D_F64> createModelCloud(Object model, int N, double sigma, double size)
   {
      List<Point3D_F64> cloud = new ArrayList<Point3D_F64>();

      for (int i = 0; i < N; i++)
      {

         Point3D_F64 p;
         if (model instanceof Cylinder3D_F64)
         {
            double z = size * rand.nextDouble();
            double theta = 2.0 * Math.PI * rand.nextDouble();
            p = PointCloudShapeTools.createPt((Cylinder3D_F64) model, z, theta);
         }
         else if (model instanceof PlaneNormal3D_F64)
         {
            double x = size * (rand.nextDouble() - 0.5);
            double y = size * (rand.nextDouble() - 0.5);

            p = PointCloudShapeTools.createPt((PlaneNormal3D_F64) model, x, y);
         }
         else if (model instanceof Sphere3D_F64)
         {
            double phi = 2.0 * Math.PI * rand.nextDouble();
            double theta = 2.0 * Math.PI * rand.nextDouble();

            p = PointCloudShapeTools.createPt((Sphere3D_F64) model, phi, theta);
         }
         else
            return cloud;

         p.x += rand.nextGaussian() * sigma;
         p.y += rand.nextGaussian() * sigma;
         p.z += rand.nextGaussian() * sigma;

         cloud.add(p);
      }
      return cloud;
   }

   private void setupLighting()
   {

      AmbientLight a1 = new AmbientLight();
      a1.setColor(ColorRGBA.White.mult(.4f));
      rootNode.addLight(a1);

      if (SHADOWS)
      {
         primaryLight = setupDirectionalLight(new Vector3f(-0.5f, -8, -2));
         rootNode.addLight(primaryLight);

         DirectionalLight d2 = setupDirectionalLight(new Vector3f(1, -1, 1));
         rootNode.addLight(d2);

         DirectionalLight d3 = setupDirectionalLight(new Vector3f(1, -1, -1));
         rootNode.addLight(d3);

         DirectionalLight d4 = setupDirectionalLight(new Vector3f(-1, -1, 1));
         rootNode.addLight(d4);

         DirectionalLightShadowRenderer dlsr = new DirectionalLightShadowRenderer(assetManager, 2048, 2);
         dlsr.setLight(primaryLight);
         dlsr.setLambda(0.3f);
         dlsr.setShadowIntensity(0.4f);
         dlsr.setEdgeFilteringMode(EdgeFilteringMode.Dither);

         // dlsr.displayFrustum();
         viewPort.addProcessor(dlsr);

         rootNode.setShadowMode(ShadowMode.Off);
         zUp.setShadowMode(ShadowMode.Off);
      }

   }

   private DirectionalLight setupDirectionalLight(Vector3f direction)
   {
      DirectionalLight d2 = new DirectionalLight();
      d2.setColor(ColorRGBA.White.mult(0.6f));
      Vector3f lightDirection2 = direction.normalizeLocal();
      d2.setDirection(lightDirection2);

      return d2;
   }

   private void emFitRun(final List<Point3D_F64> cloud)
   {
      System.err.println("RUNNING EM FITTER PLEASE HOLD");
      enqueue(new Callable<Object>()
      {
         public Object call() throws Exception
         {
            shapesNode.detachAllChildren();
            return null;
         }
      });
      Thread emFit = new Thread(new Runnable()
      {
         public void run()
         {
            long time = System.currentTimeMillis();
            PointCloudShapeFinder shapeFinder = applyRansac(cloud);
            System.out.println("Ransac time: " + (System.currentTimeMillis() - time));
            time = System.currentTimeMillis();

            List<Shape> found = new ArrayList<Shape>(shapeFinder.getFound());

            List<Point3D_F64> unmatched = new ArrayList<Point3D_F64>();
            shapeFinder.getUnmatched(unmatched);

            filter(found, .25, 4);

            List<PlaneGeneral3D_F64> planes = new ArrayList<PlaneGeneral3D_F64>();
            for (Shape s : found)
            {
               planes.add((PlaneGeneral3D_F64) s.parameters);
            }

            ScoringFunction<PlaneGeneral3D_F64, Point3D_F64> scorer = ExpectationMaximizationFitter.getGaussianSqauresMixedError(.01 / 2);

            time = System.currentTimeMillis();

            //List<Shape> emFitShapes = getEMFitShapes(planes, cloud, scorer, 25, .999);
            List<Point3D_F64> allPoints = new ArrayList<Point3D_F64>();
            for (Shape s : found)
               allPoints.addAll(s.points);
            List<Shape> emFitShapes = getEMFitShapes(planes, cloud, scorer, 25, .9999);

            System.out.println("EM Fit time: " + (System.currentTimeMillis() - time));

            List<Shape> allShapes = new ArrayList<PointCloudShapeFinder.Shape>();
            for (int i = 0; i < emFitShapes.size(); i++)
            {
               allShapes.add(found.get(i));
               allShapes.add(emFitShapes.get(i));
            }

            double minZ = Double.POSITIVE_INFINITY;
            Shape minShape = null;
            for (Shape s : emFitShapes)
            {
               double avg = 0;
               for (Point3D_F64 p : s.points)
                  avg += p.z;
               avg /= s.points.size();
               if (avg < minZ)
               {
                  minShape = s;
                  minZ = avg;
               }
            }

            List<PlaneGeneral3D_F64> orientPlanes = new ArrayList<PlaneGeneral3D_F64>();
            for (Shape s : emFitShapes)
               if (s != minShape)
                  orientPlanes.add((PlaneGeneral3D_F64) s.parameters);

            CubeCalibration.orient(orientPlanes);
            renderShapes(emFitShapes);
         }
      });
      emFit.start();
   }

   private void normalsRun(final List<Point3D_F64> cloud)
   {
      System.err.println("RUNNING NORMAL VISUALIZER PLEASE HOLD");
      enqueue(new Callable<Object>()
      {
         public Object call() throws Exception
         {
            return null;
         }
      });
      Thread normals = new Thread(new Runnable()
      {
         public void run()
         {
            ApproximateSurfaceNormals surface = new ApproximateSurfaceNormals(configNormal.numPlane,configNormal.maxDistancePlane, configNormal.numNeighbors,
                  configNormal.maxDistanceNeighbor);

            FastQueue<PointVectorNN> normalVectors = new FastQueue<PointVectorNN>(PointVectorNN.class, false);
            surface.process(cloud, normalVectors);
            //renderNormals(new ArrayList<PointVectorNN>(normalVectors.toList()), .04f, .0015f, ColorRGBA.White);

            ColorRGBA[] colors = new ColorRGBA[normalVectors.size()];
            double[] scores = new double[normalVectors.size()];
            for (int i = 0; i < normalVectors.size(); i++)
            {
               PointVectorNN p = normalVectors.get(i);
               //CubeCalibration.nonLinearFitNormal(p);
               //CubeCalibration.weightedLinearFit(p, ExpectationMaximizationFitter.getNormalSingularityError(), 5);
               //scores[i] = CubeCalibration.score(p);
               scores[i] = CubeCalibration.getAverageError(p);

            }

            double maxScore = 0;
            for (int i = 0; i < scores.length; i++)
               if (scores[i] > maxScore)
                  maxScore = scores[i];

            maxScore = .04;

            for (int i = 0; i < scores.length; i++)
               scores[i] /= maxScore;

            ArrayList<PointVectorNN> subset = new ArrayList<PointVectorNN>();
            ArrayList<ColorRGBA> colorSubset = new ArrayList<ColorRGBA>();
            for (int i = 0; i < scores.length; i++)
            {
               int c = Color.HSBtoRGB(.7f - .7f * (float) scores[i], 1, 1.0f);
               //int c = Color.HSBtoRGB(.7f - .7f*(float)CubeCalibration.filter(normalVectors.get(i), maxScore, .15), 1, 1.0f);
               //int c = Color.HSBtoRGB(CubeCalibration.filter(normalVectors.get(i), maxScore, .1) < .5 ? .7f : 0, 1, 1.0f);
               //int c = Color.HSBtoRGB(scores[i] < .1 ? .7f : 0, 1, 1.0f);
               colors[i] = new ColorRGBA(((c >> 16) & 0xFF) / 256.0f, ((c >> 8) & 0xFF) / 256.0f, ((c >> 0) & 0xFF) / 256.0f, 1.0f);

               if (scores[i] < .01)
               {
                  subset.add(normalVectors.get(i));
                  colorSubset.add(new ColorRGBA(1, 1, 1, 1));
                  //colorSubset.add(colors[i]);
               }
            }

            renderNormals(new ArrayList<PointVectorNN>(normalVectors.toList()), colors, .06f, .001f);
            //renderNormals(subset, colorSubset.toArray(new ColorRGBA[subset.size()]), .06f, .001f);

         }
      });
      normals.start();
   }

   private List<Shape> getEMFitShapes(List<PlaneGeneral3D_F64> startPlanes, List<Point3D_F64> cloud, ScoringFunction<PlaneGeneral3D_F64, Point3D_F64> scorer,
         int rounds, double filter)
   {
      List<PlaneGeneral3D_F64> planes = ExpectationMaximizationFitter.fit(startPlanes, cloud, scorer, rounds);
      double[][] weights = ExpectationMaximizationFitter.getWeights(null, planes, cloud, scorer);

      List<Shape> emFitShapes = new ArrayList<PointCloudShapeFinder.Shape>();

      for (int i = 0; i < planes.size(); i++)
      {
         Shape s = new Shape();
         s.type = CloudShapeTypes.PLANE;
         s.points = new ArrayList<Point3D_F64>();
         s.parameters = planes.get(i);
         emFitShapes.add(s);
         for (int j = 0; j < cloud.size(); j++)
         {
            if (weights[i][j] > filter)
               s.points.add(cloud.get(j));
         }
      }

      return emFitShapes;
   }

   private void filter(List<Shape> shapes, double alpha, double max)
   {
      Collections.sort(shapes, new Comparator<Shape>()
      {
         public int compare(Shape o1, Shape o2)
         {
            return o1.points.size() - o2.points.size();
         }
      });

      double eps = 3e-2;
      for (int i = 0; i < shapes.size(); i++)
      {
         List<Point3D_F64> s1 = shapes.get(i).points;
         for (int j = i + 1; j < shapes.size(); j++)
         {
            List<Point3D_F64> s2 = shapes.get(j).points;
            int overlap = 0;
            for (int k = 0; k < s2.size(); k++)
            {
               for (int l = 0; l < s1.size(); l++)
               {
                  if (s1.get(l).distance(s2.get(k)) < eps)
                     overlap++;
               }
            }

            System.out.println("overlap: " + overlap + " of " + s2.size() + " with limit:" + s2.size() * alpha);
            if (overlap > s2.size() * alpha)
            {
               shapes.remove(j);
               j--;
            }
         }

         while (shapes.size() > max)
         {
            shapes.remove(0);
         }
      }
   }

   private void ransacRun(final List<Point3D_F64> cloud)
   {
      System.err.println("RUNNING RANSAC PLEASE HOLD");
      enqueue(new Callable<Object>()
      {
         public Object call() throws Exception
         {
            shapesNode.detachAllChildren();
            return null;
         }
      });
      Thread ransac = new Thread(new Runnable()
      {
         public void run()
         {
            PointCloudShapeFinder shapeFinder = applyRansac(cloud);

            List<PointCloudShapeFinder.Shape> found = new ArrayList<Shape>(shapeFinder.getFound());

            List<Point3D_F64> unmatched = new ArrayList<Point3D_F64>();
            shapeFinder.getUnmatched(unmatched);

            System.out.println("Unmatched points " + unmatched.size());
            System.out.println("total shapes found: " + found.size());

            //filter(found, .25, 200);

            renderShapes(found);

            System.out.println("RANSAC COMPLETE THANK YOU FOR YOUR PATIENCE");

         }
      });
      ransac.start();
   }

   private PointCloudShapeFinder applyRansac(List<Point3D_F64> cloud)
   {
      CloudShapeTypes shapeTypes[] = new CloudShapeTypes[] { CloudShapeTypes.PLANE };//, CloudShapeTypes.CYLINDER, CloudShapeTypes.SPHERE};

      ConfigSchnabel2007 configRansac = ConfigSchnabel2007.createDefault(20, 0.8, 0.02, shapeTypes);
      configRansac.randomSeed = rand.nextLong();

      configRansac.minModelAccept = 50;
      configRansac.octreeSplit = 25;

      configRansac.maximumAllowedIterations = 30000;
      configRansac.ransacExtension = 100;

      //configRansac.models.get(1).modelCheck = new CheckShapeCylinderRadius(0.2);
      //configRansac.models.get(2).modelCheck = new CheckShapeSphere3DRadius(0.2);

      ConfigRemoveFalseShapes configMerge = new ConfigRemoveFalseShapes(0.25);

      PointCloudShapeFinder shapeFinder = FactoryPointCloudShape.ransacOctree(configNormal, configRansac, configMerge);

      shapeFinder.process(cloud, null);
      return shapeFinder;
   }

   private void renderShapes(List<Shape> found)
   {
      int total = 0;
      for (PointCloudShapeFinder.Shape s : found)
      {
         total += s.points.size();
      }

      pointCloudNode.detachAllChildren();
      Vector3f[] points = new Vector3f[total];
      ColorRGBA[] colors = new ColorRGBA[total];

      int index = 0;
      float hue = 0;
      for (final PointCloudShapeFinder.Shape s : found)
      {
         int c = Color.HSBtoRGB(hue, 1.0f, 1.0f);
         hue += (1.0 / found.size());
         final ColorRGBA color = new ColorRGBA(((c >> 16) & 0xFF) / 256.0f, ((c >> 8) & 0xFF) / 256.0f, ((c >> 0) & 0xFF) / 256.0f, 1.0f);
         //System.out.println("Shape " + s.type + " hue: " + hue);

         enqueue(new Callable<Object>()
         {
            public Object call() throws Exception
            {
               translator.translateShape(s, color, shapesNode);
               return null;
            }
         });

         for (Point3D_F64 p : s.points)
         {
            points[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
            colors[index] = color;
            index++;
         }
      }

      PointCloud generator = new PointCloud(assetManager);

      try
      {
         final Node pointCloudNodeToAdd = generator.generatePointCloudGraph(points, colors, defaultPointSize);
         enqueue(new Callable<Object>()
         {
            public Object call() throws Exception
            {
               selectionNode.detachAllChildren();
               pointCloudNode.attachChild(pointCloudNodeToAdd);
               return null;
            }
         });
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   private void renderNormals(final List<PointVectorNN> normals, final ColorRGBA[] colors, final float length, final float width)
   {
      final ShapeTranslator shapeTranslator = new ShapeTranslator(this);

      try
      {
         PointCloud generator = new PointCloud(assetManager);
         Vector3f[] points = new Vector3f[normals.size()];
         //ColorRGBA[] colors = new ColorRGBA[normals.size()];
         for (int i = 0; i < normals.size(); i++)
         {
            Point3D_F64 p = normals.get(i).p;
            points[i] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
            //colors[i] = color;
         }

         //final Node pointCloudNodeToAdd = generator.generatePointCloudGraph(points, colors, defaultPointSize);

         enqueue(new Callable<Object>()
         {
            public Object call() throws Exception
            {

               normalsNode.detachAllChildren();

               for (int i = 1; i < normals.size(); i++)
               {
                  Point3D_F64 p = normals.get(i).p;
                  Vector3D_F64 n = normals.get(i).normal;
                  Vector3f start = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                  Vector3f end = new Vector3f((float) n.x, (float) n.y, (float) n.z);
                  end.scaleAdd(length, start);
                  normalsNode.attachChild(shapeTranslator.generateCylinder(start, end, width, colors[i]));

               }

               //pointCloudNode.attachChild(pointCloudNodeToAdd);
               normalsNode.batch();
               return null;
            }
         });
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   private void clearView()
   {
      pointCloudNode.detachAllChildren();

   }

   private void displayView(List<Point3D_F64> cloud, ColorRGBA color, float size, Node nodeToAddTo)
   {
      System.out.println("total points: " + cloud.size());

      Vector3f[] points = new Vector3f[cloud.size()];
      ColorRGBA[] colors = new ColorRGBA[cloud.size()];

      int index = 0;
      for (Point3D_F64 p : cloud)
      {
         points[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
         colors[index] = color;
         index++;
      }

      PointCloud generator = new PointCloud(assetManager);

      try
      {
         nodeToAddTo.attachChild(generator.generatePointCloudGraph(points, colors, size));

      }
      catch (Exception e)
      {
         this.handleError(e.getMessage(), e);
      }

   }

   private void setupBoxNode()
   {
      Box box = new Box(boxExtent, boxExtent, boxExtent);
      Geometry geometryBox = new Geometry("boxMesh", box);

      Material objectMaterial = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
      objectMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
      objectMaterial.setColor("Color", new ColorRGBA(0, 0, 1, 0.25f));
      geometryBox.setMaterial(objectMaterial);
      geometryBox.setQueueBucket(Bucket.Transparent);
      geometryBox.setShadowMode(ShadowMode.CastAndReceive);

      boundsNode.attachChild(geometryBox);
      boundsNode.setLocalTranslation(initialTranslation);
      zUp.attachChild(boundsNode);

   }

   private List<Point3D_F64> readPointCloud(int maxLines, Vector3f min, Vector3f max)
   {
      List<Point3D_F64> cloud = new ArrayList<Point3D_F64>();
      SerializationDefinitionManager manager = new SerializationDefinitionManager();
      manager.addDefinition(new DataDefinition("point3d", Point3D_F64.class, "x", "y", "z"));

      try
      {
         FileInputStream in = new FileInputStream(fileName);
         ReadCsvObjectSmart<Point3D_F64> reader = new ReadCsvObjectSmart<Point3D_F64>(in, manager, "point3d");

         Point3D_F64 pt = new Point3D_F64();
         int count = 0;
         while ((reader.nextObject(pt) != null) && (count++ < maxLines))
         {
            if ((pt.x >= min.x) && (pt.y >= min.y) && (pt.z >= min.z) && (pt.x <= max.x) && (pt.y <= max.y) && (pt.z <= max.z))
               cloud.add(pt.copy());
         }

      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }

      return cloud;
   }

   private List<Point3D_F64> getBoundedCloud()
   {
      Vector3f current = boundsNode.getLocalTranslation();
      Vector3f max = new Vector3f(current.x + boxExtent, current.y + boxExtent, current.z + boxExtent);
      Vector3f min = new Vector3f(current.x - boxExtent, current.y - boxExtent, current.z - boxExtent);

      List<Point3D_F64> cloud = new ArrayList<Point3D_F64>();
      SerializationDefinitionManager manager = new SerializationDefinitionManager();
      manager.addDefinition(new DataDefinition("point3d", Point3D_F64.class, "x", "y", "z"));

      for (Point3D_F64 pt : fullCloud)
      {
         if ((pt.x >= min.x) && (pt.y >= min.y) && (pt.z >= min.z) && (pt.x <= max.x) && (pt.y <= max.y) && (pt.z <= max.z))
         {
            cloud.add(pt);
         }
      }
      return cloud;
   }

   private List<Point3D_F64> getSphere(double radius)
   {
      Vector3f p = boundsNode.getLocalTranslation();
      Point3D_F64 center = new Point3D_F64(p.x, p.y, p.z);
      System.out.println(center);

      List<Point3D_F64> cloud = new ArrayList<Point3D_F64>();
      SerializationDefinitionManager manager = new SerializationDefinitionManager();
      manager.addDefinition(new DataDefinition("point3d", Point3D_F64.class, "x", "y", "z"));

      for (Point3D_F64 pt : fullCloud)
      {
         if (pt.distance(center) < radius)
            cloud.add(pt.copy());
      }
      return cloud;
   }

   public void beginInput()
   {
      // TODO Auto-generated method stub

   }

   public void endInput()
   {
      // TODO Auto-generated method stub

   }

   public void onJoyAxisEvent(JoyAxisEvent evt)
   {
      // TODO Auto-generated method stub

   }

   public void onJoyButtonEvent(JoyButtonEvent evt)
   {
      // TODO Auto-generated method stub

   }

   public void onMouseMotionEvent(MouseMotionEvent evt)
   {
      // TODO Auto-generated method stub

   }

   public void onMouseButtonEvent(MouseButtonEvent evt)
   {
      // TODO Auto-generated method stub

   }

   private boolean ctrlPressed = false;
   private boolean spacePressed = false;
   private boolean boxHidden = false;
   private FilterPostProcessor fpp;

   public void onKeyEvent(KeyInputEvent evt)
   {
      Material objectMaterial = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
      objectMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
      objectMaterial.setColor("Color", new ColorRGBA(0, 0, 1, 0.5f));
      boundsNode.setMaterial(objectMaterial);
      boundsNode.setQueueBucket(Bucket.Transparent);
      boundsNode.setShadowMode(ShadowMode.CastAndReceive);

      translateSpeed = 0.05f;

      // up 200 left 203 right 205 down 208
      if ((evt.getKeyCode() == 29) || (evt.getKeyCode() == 157))
      {
         ctrlPressed = evt.isPressed();
      }

      if (evt.getKeyCode() == 57)
      {
         spacePressed = evt.isPressed();
      }

      float flyCamSpeed = 15;
      flyCamSpeed /= ctrlPressed ? 16 : 1;
      flyCamSpeed /= spacePressed ? 4 : 1;
      flyCam.setMoveSpeed(flyCamSpeed);

      if (ctrlPressed)
      {
         if ((evt.getKeyCode() == 23) && evt.isPressed())
         {
            Vector3f current = boundsNode.getLocalTranslation().clone();
            current.z += translateSpeed;
            boundsNode.setLocalTranslation(current);
            updateAfterMoveIfRequired();
         }
         else if ((evt.getKeyCode() == 37) && evt.isPressed())
         {
            Vector3f current = boundsNode.getLocalTranslation().clone();
            current.z -= translateSpeed;
            boundsNode.setLocalTranslation(current);
            updateAfterMoveIfRequired();
         }
      }
      else
      {
         if ((evt.getKeyCode() == 23) && evt.isPressed())
         {
            Vector3f current = boundsNode.getLocalTranslation().clone();
            current.x += translateSpeed;
            boundsNode.setLocalTranslation(current);
            updateAfterMoveIfRequired();
         }
         else if ((evt.getKeyCode() == 37) && evt.isPressed())
         {
            Vector3f current = boundsNode.getLocalTranslation().clone();
            current.x -= translateSpeed;
            boundsNode.setLocalTranslation(current);
            updateAfterMoveIfRequired();
         }
      }

      if ((evt.getKeyCode() == 36) && evt.isPressed())
      {
         Vector3f current = boundsNode.getLocalTranslation().clone();
         current.y += translateSpeed;
         boundsNode.setLocalTranslation(current);
         updateAfterMoveIfRequired();
      }
      else if ((evt.getKeyCode() == 38) && evt.isPressed())
      {
         Vector3f current = boundsNode.getLocalTranslation().clone();
         current.y -= translateSpeed;
         boundsNode.setLocalTranslation(current);
         updateAfterMoveIfRequired();
      }

      if (evt.getKeyCode() == 13)
      {
         if (evt.isPressed())
         {
            if (boxHidden)
            {
               boxHidden = false;
               shapesNode.setCullHint(CullHint.Dynamic);
            }
            else
            {
               boxHidden = true;
               shapesNode.setCullHint(CullHint.Always);
            }
         }
      }

      if ((evt.getKeyCode() == 28) && evt.isPressed())
      {
         // hide the box
         boundsNode.setCullHint(CullHint.Always);
         clearView();
         showBounds();

      }

      if ((evt.getKeyChar() == 'r') && evt.isPressed())
      {
         ransacRun(ransacCloud);
      }

      if (evt.getKeyChar() == 'e' && evt.isPressed())
      {
         emFitRun(ransacCloud);
      }

      if (evt.getKeyChar() == 'n' && evt.isPressed())
      {
         normalsRun(ransacCloud);
      }

      if (evt.getKeyChar() == 'f' && evt.isPressed())
      {
         Thread boundsThread = new Thread(new Runnable()
         {
            public void run()
            {
               List<Point3D_F64> filterCloud = CubeCalibration.thin(fullCloud, .02);
               filterCloud = CubeCalibration.filterByResidual(filterCloud, configNormal, .005);
               final List<Point3D_F64> finalCloud = filterCloud;
               
               System.out.println("Full: " + fullCloud.size() + " Filtered: " + filterCloud.size());
               fullCloud = filterCloud;

               if (true)
               {
                  allPointCloud.detachAllChildren();
                  showBounds();
                  return;
               }

               enqueue(new Callable<Object>()
               {
                  public Object call() throws Exception
                  {
                     displayView(finalCloud, ColorRGBA.White, defaultPointSize, allPointCloud);

                     return null;
                  }
               });

            }
         });
         boundsThread.start();
      }

      if (evt.getKeyChar() == 'c' && evt.isPressed())
      {
         shapesNode.detachAllChildren();
         normalsNode.detachAllChildren();
         pointCloudNode.detachAllChildren();
         allPointCloud.detachAllChildren();
         showBounds();
      }
   }

   private void showBounds()
   {
      calculatingBounds = true;
      Thread boundsThread = new Thread(new Runnable()
      {
         public void run()
         {
            ransacCloud = getBoundedCloud();

            final List<Point3D_F64> fullCloudCopy = new ArrayList<Point3D_F64>(fullCloud);
            for (Point3D_F64 p : ransacCloud)
            {
               fullCloudCopy.remove(p);
            }

            //ransacCloud = getSphere(1);
            enqueue(new Callable<Object>()
            {
               public Object call() throws Exception
               {
                  selectionNode.detachAllChildren();
                  displayView(ransacCloud, selectColor, selectPointSize, selectionNode);
                  calculatingBounds = false;

                  allPointCloud.detachAllChildren();
                  displayView(fullCloudCopy, defaultColor, defaultPointSize, allPointCloud);

                  return null;
               }
            });

         }
      });
      boundsThread.start();
   }

   private boolean calculatingBounds = false;

   private void updateAfterMoveIfRequired()
   {
      if (!calculatingBounds)
      {
         showBounds();
      }

      if (boundsNode.getCullHint().equals(CullHint.Always))
      {
         enqueue(new Callable<Object>()
         {
            public Object call() throws Exception
            {
               shapesNode.detachAllChildren();
               displayView(fullCloud, defaultColor, defaultPointSize, pointCloudNode);

               boundsNode.setCullHint(CullHint.Dynamic);

               return null;
            }
         });

      }
   }

   public void onTouchEvent(TouchEvent evt)
   {
      // TODO Auto-generated method stub

   }
}
