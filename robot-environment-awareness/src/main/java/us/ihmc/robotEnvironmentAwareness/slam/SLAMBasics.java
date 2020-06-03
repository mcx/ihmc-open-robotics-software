package us.ihmc.robotEnvironmentAwareness.slam;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.jOctoMap.normalEstimation.NormalEstimationParameters;
import us.ihmc.jOctoMap.ocTree.NormalOcTree;
import us.ihmc.jOctoMap.pointCloud.ScanCollection;
import us.ihmc.robotEnvironmentAwareness.geometry.ConcaveHullFactoryParameters;
import us.ihmc.robotEnvironmentAwareness.planarRegion.CustomRegionMergeParameters;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PlanarRegionPolygonizer;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PlanarRegionSegmentationCalculator;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PlanarRegionSegmentationParameters;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PlanarRegionSegmentationRawData;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PolygonizerParameters;
import us.ihmc.robotEnvironmentAwareness.planarRegion.SurfaceNormalFilterParameters;
import us.ihmc.robotEnvironmentAwareness.slam.tools.SLAMTools;
import us.ihmc.robotics.geometry.PlanarRegionsList;

public class SLAMBasics implements SLAMInterface
{
   private final AtomicReference<SLAMFrame> latestSlamFrame = new AtomicReference<>(null);
   protected final NormalOcTree octree;
   private final AtomicInteger mapSize = new AtomicInteger();

<<<<<<< HEAD
=======
   private final PlanarRegionSegmentationCalculator segmentationCalculator;
   private PlanarRegionsList planarRegionsMap;
   private final ConcaveHullFactoryParameters concaveHullFactoryParameters = new ConcaveHullFactoryParameters();
   private final PolygonizerParameters polygonizerParameters = new PolygonizerParameters();
   private final CustomRegionMergeParameters customRegionMergeParameters = new CustomRegionMergeParameters();
   private final PlanarRegionSegmentationParameters planarRegionSegmentationParameters = new PlanarRegionSegmentationParameters();
>>>>>>> c12a8c7feaf... Tested surfel icp.

   public SLAMBasics(double octreeResolution)
   {
      octree = new NormalOcTree(octreeResolution);
<<<<<<< HEAD
=======

      planarRegionSegmentationParameters.setMaxDistanceFromPlane(0.03);
      planarRegionSegmentationParameters.setMinRegionSize(150);
      
      segmentationCalculator = new PlanarRegionSegmentationCalculator();

      SurfaceNormalFilterParameters surfaceNormalFilterParameters = new SurfaceNormalFilterParameters();
      surfaceNormalFilterParameters.setUseSurfaceNormalFilter(true);
      surfaceNormalFilterParameters.setSurfaceNormalLowerBound(Math.toRadians(-40.0));
      surfaceNormalFilterParameters.setSurfaceNormalUpperBound(Math.toRadians(40.0));

      segmentationCalculator.setParameters(planarRegionSegmentationParameters);
      segmentationCalculator.setSurfaceNormalFilterParameters(surfaceNormalFilterParameters);

      polygonizerParameters.setConcaveHullThreshold(0.15);
   }

   protected void insertNewPointCloud(SLAMFrame frame)
   {
      Point3DReadOnly[] pointCloud = frame.getPointCloud();
      RigidBodyTransformReadOnly sensorPose = frame.getSensorPose();

      ScanCollection scanCollection = new ScanCollection();
      int numberOfPoints = frame.getPointCloud().length;

      scanCollection.setSubSampleSize(numberOfPoints);
      scanCollection.addScan(SLAMTools.toScan(pointCloud, sensorPose.getTranslation()));

      octree.insertScanCollection(scanCollection, false);
      octree.enableParallelComputationForNormals(true);

      NormalEstimationParameters normalEstimationParameters = new NormalEstimationParameters();
      normalEstimationParameters.setNumberOfIterations(7);
      octree.setNormalEstimationParameters(normalEstimationParameters);
>>>>>>> c12a8c7feaf... Tested surfel icp.
   }

   public void updatePlanarRegionsMap()
   {
      octree.updateNormals();
      segmentationCalculator.setSensorPosition(getLatestFrame().getSensorPose().getTranslation());
      segmentationCalculator.compute(octree.getRoot());

      List<PlanarRegionSegmentationRawData> rawData = segmentationCalculator.getSegmentationRawData();
      planarRegionsMap = PlanarRegionPolygonizer.createPlanarRegionsList(rawData, concaveHullFactoryParameters, polygonizerParameters);
   }
   
   @Override
   public void addKeyFrame(StereoVisionPointCloudMessage pointCloudMessage)
   {
      SLAMFrame frame = new SLAMFrame(pointCloudMessage);
      setLatestFrame(frame);
      insertNewPointCloud(frame);
   }

   @Override
   public boolean addFrame(StereoVisionPointCloudMessage pointCloudMessage)
   {
      SLAMFrame frame = new SLAMFrame(getLatestFrame(), pointCloudMessage);

      RigidBodyTransformReadOnly optimizedMultiplier = computeFrameCorrectionTransformer(frame);

      if (optimizedMultiplier == null)
      {
         return false;
      }
      else
      {
         frame.updateOptimizedCorrection(optimizedMultiplier);
         setLatestFrame(frame);
         insertNewPointCloud(frame);

         return true;
      }
   }

   @Override
   public void clear()
   {
      latestSlamFrame.set(null);
      mapSize.set(0);
      octree.clear();
   }

   public boolean isEmpty()
   {
      if (latestSlamFrame.get() == null)
         return true;
      else
         return false;
   }

   public int getMapSize()
   {
      return mapSize.get();
   }

<<<<<<< HEAD
=======
   public PlanarRegionsList getPlanarRegionsMap()
   {
      return planarRegionsMap;
   }
   
   public void setLatestFrame(SLAMFrame frameToSet)
   {
      latestSlamFrame.set(frameToSet);
      mapSize.incrementAndGet();
   }

>>>>>>> c12a8c7feaf... Tested surfel icp.
   public SLAMFrame getLatestFrame()
   {
      return latestSlamFrame.get();
   }

   public double getOctreeResolution()
   {
      return octree.getResolution();
   }

   public NormalOcTree getOctree()
   {
      return octree;
   }
}
