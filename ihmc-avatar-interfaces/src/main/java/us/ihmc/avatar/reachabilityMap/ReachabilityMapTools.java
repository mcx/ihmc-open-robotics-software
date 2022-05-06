package us.ihmc.avatar.reachabilityMap;

import static us.ihmc.avatar.scs2.YoGraphicDefinitionFactory.newYoGraphicCoordinateSystem3DDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.ejml.data.DMatrixRMaj;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import us.ihmc.avatar.reachabilityMap.ReachabilitySphereMapSimulationHelper.VisualizationType;
import us.ihmc.avatar.reachabilityMap.Voxel3DGrid.Voxel3DData;
import us.ihmc.avatar.reachabilityMap.Voxel3DGrid.VoxelExtraData;
import us.ihmc.avatar.reachabilityMap.voxelPrimitiveShapes.SphereVoxelShape;
import us.ihmc.commons.Conversions;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.mecano.tools.MultiBodySystemTools;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.scs2.definition.robot.RobotDefinition;
import us.ihmc.scs2.definition.visual.ColorDefinition;
import us.ihmc.scs2.definition.visual.ColorDefinitions;
import us.ihmc.scs2.definition.visual.VisualDefinition;
import us.ihmc.scs2.definition.visual.VisualDefinitionFactory;
import us.ihmc.scs2.session.SessionMode;
import us.ihmc.scs2.sessionVisualizer.TriangleMesh3DFactories;
import us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizer;
import us.ihmc.scs2.sessionVisualizer.jfx.SessionVisualizerControls;
import us.ihmc.scs2.simulation.VisualizationSession;
import us.ihmc.scs2.simulation.robot.Robot;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePose3D;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoEnum;

public class ReachabilityMapTools
{
   public static List<VisualDefinition> createBoundingBoxVisuals(Voxel3DGrid voxel3DGrid)
   {
      return createBoundingBoxVisuals(voxel3DGrid.getMinPoint(), voxel3DGrid.getMaxPoint());
   }

   public static List<VisualDefinition> createBoundingBoxVisuals(FramePoint3DReadOnly min, FramePoint3DReadOnly max)
   {
      double width = 0.01;
      ColorDefinition color = ColorDefinitions.LightBlue();
      VisualDefinitionFactory boundingBox = new VisualDefinitionFactory();
      FramePoint3D modifiableMin = new FramePoint3D(min);
      modifiableMin.changeFrame(ReferenceFrame.getWorldFrame());
      FramePoint3D modifiableMax = new FramePoint3D(max);
      modifiableMax.changeFrame(ReferenceFrame.getWorldFrame());
      double x0 = modifiableMin.getX();
      double y0 = modifiableMin.getY();
      double z0 = modifiableMin.getZ();
      double x1 = modifiableMax.getX();
      double y1 = modifiableMax.getY();
      double z1 = modifiableMax.getZ();
      // The three segments originating from min
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y0, z0, x1, y0, z0, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y0, z0, x0, y1, z0, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y0, z0, x0, y0, z1, width), color);
      // The three segments originating from min
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x1, y1, z1, x0, y1, z1, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x1, y1, z1, x1, y0, z1, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x1, y1, z1, x1, y1, z0, width), color);

      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x1, y0, z0, x1, y1, z0, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x1, y0, z0, x1, y0, z1, width), color);

      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y1, z0, x1, y1, z0, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y1, z0, x0, y1, z1, width), color);

      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y0, z1, x1, y0, z1, width), color);
      boundingBox.addGeometryDefinition(TriangleMesh3DFactories.Line(x0, y0, z1, x0, y1, z1, width), color);

      return boundingBox.getVisualDefinitions();
   }

   public static List<VisualDefinition> createReachibilityColorScaleVisuals()
   {
      VisualDefinitionFactory voxelViz = new VisualDefinitionFactory();
      double maxReachability = 0.7;
      double resolution = 0.1;
      voxelViz.appendTranslation(-1.0, -1.0, 0.0);

      for (double z = 0; z <= maxReachability; z += maxReachability * resolution)
      {
         ColorDefinition color = ColorDefinitions.hsb(z * 360.0, 1.0, 1.0);
         voxelViz.appendTranslation(0.0, 0.0, resolution);
         voxelViz.addSphere(0.025, color);
      }

      return voxelViz.getVisualDefinitions();
   }

   public static void loadVisualizeReachabilityMap(String robotName, RobotDefinition robotDefinition, FullHumanoidRobotModel fullRobotModel)
   {
      HumanoidReferenceFrames referenceFrames = new HumanoidReferenceFrames(fullRobotModel);
      List<ReferenceFrame> frameList = new ArrayList<>();
      frameList.add(referenceFrames.getPelvisZUpFrame());
      frameList.add(referenceFrames.getMidFeetZUpFrame());
      frameList.add(referenceFrames.getCenterOfMassFrame());

      for (RobotSide robotSide : RobotSide.values)
      {
         frameList.add(referenceFrames.getSoleFrame(robotSide));
         frameList.add(referenceFrames.getAnkleZUpFrame(robotSide));
         frameList.add(referenceFrames.getHandFrame(robotSide));
      }

   }

   public static void loadVisualizeReachabilityMap(String robotName, RobotDefinition robotDefinition)
   {
      loadVisualizeReachabilityMap(robotName, robotDefinition, robotDefinition.newInstance(Robot.createRobotRootFrame(robotDefinition, ReferenceFrame.getWorldFrame())), null);
   }

   public static void loadVisualizeReachabilityMap(String robotName,
                                                   RobotDefinition robotDefinition,
                                                   RigidBodyBasics rootBody,
                                                   Collection<ReferenceFrame> referenceFrames)
   {
      long startTime = System.nanoTime();
      System.out.println("Loading reachability map");
      ReachabilityMapFileLoader reachabilityMapFileLoader = new ReachabilityMapFileLoader(robotName, rootBody, referenceFrames);
      FramePose3D controlFramePose = reachabilityMapFileLoader.getControlFramePose();

      RigidBodyBasics endEffector = MultiBodySystemTools.collectSubtreeEndEffectors(rootBody)[0];
      ReferenceFrame controlFrame = ReferenceFrameTools.constructFrameWithChangingTransformFromParent("controlFrame",
                                                                                                      endEffector.getParentJoint().getFrameAfterJoint(),
                                                                                                      new RigidBodyTransform(controlFramePose.getOrientation(),
                                                                                                                             controlFramePose.getPosition()));

      System.out.println("Done loading reachability map. Took: " + Conversions.nanosecondsToSeconds(System.nanoTime() - startTime) + " seconds.");

      Voxel3DGrid grid = reachabilityMapFileLoader.getLoadedGrid();
      SphereVoxelShape sphereVoxelShape = grid.getSphereVoxelShape();

      Map<VisualizationType, ObservableList<VisualDefinition>> voxelVisualization = new EnumMap<>(VisualizationType.class);

      YoRegistry registry = new YoRegistry(ReachabilityMapTools.class.getSimpleName());
      AtomicReference<VisualizationType> previousVisualizationType = new AtomicReference<>(VisualizationType.RayReach);
      YoEnum<VisualizationType> currentVisualizationType = new YoEnum<>("currentVisualizationType", registry, VisualizationType.class);
      currentVisualizationType.set(VisualizationType.RayReach);

      YoEnum<VisualizationType> currentEvaluation = new YoEnum<>("currentEvaluation", registry, VisualizationType.class);
      YoFramePose3D currentEvaluationPose = new YoFramePose3D("currentEvaluationPose", ReferenceFrame.getWorldFrame(), registry);

      VisualizationSession visualizationSession = new VisualizationSession(robotName + " Reachability Map Visualizer");
      visualizationSession.getRootRegistry().addChild(registry);
      Robot robot = visualizationSession.addRobot(robotDefinition);
      SessionVisualizerControls guiControls = SessionVisualizer.startSessionVisualizer(visualizationSession);
      guiControls.waitUntilFullyUp();

      guiControls.addYoGraphic(newYoGraphicCoordinateSystem3DDefinition("currentEvaluationPose", currentEvaluationPose, 0.15, ColorDefinitions.HotPink()));
      guiControls.addYoGraphic(newYoGraphicCoordinateSystem3DDefinition("controlFrame", controlFramePose, 0.05, ColorDefinitions.parse("#A1887F")));

      for (VisualizationType visualizationType : VisualizationType.values())
      {
         ObservableList<VisualDefinition> visualList = FXCollections.observableArrayList();

         visualList.addListener(new ListChangeListener<VisualDefinition>()
         {
            @Override
            public void onChanged(Change<? extends VisualDefinition> change)
            {
               if (currentVisualizationType.getValue() != visualizationType)
                  return;

               while (change.next())
               {
                  if (change.wasAdded())
                     guiControls.addStaticVisuals(change.getAddedSubList());
                  if (change.wasRemoved())
                     guiControls.removeStaticVisuals(change.getAddedSubList());
               }
            }
         });
         voxelVisualization.put(visualizationType, visualList);
      }

      for (int voxelIndex = 0; voxelIndex < grid.getNumberOfVoxels(); voxelIndex++)
      {
         Voxel3DData voxel = grid.getVoxel(voxelIndex);

         if (voxel == null)
         {
            voxelVisualization.get(VisualizationType.Unreachable).add(sphereVoxelShape.createDReachabilityVisual(grid.getVoxelPosition(voxelIndex), 0.1, -1));
         }
         else
         {
            voxelVisualization.get(VisualizationType.PositionReach).add(sphereVoxelShape.createPositionReachabilityVisual(voxel.getPosition(), 0.2, true));

            if (voxel.getD() > 1e-3)
            {
               voxelVisualization.get(VisualizationType.RayReach).add(sphereVoxelShape.createDReachabilityVisual(voxel.getPosition(), 0.25, voxel.getD()));
               voxelVisualization.get(VisualizationType.PoseReach).add(sphereVoxelShape.createDReachabilityVisual(voxel.getPosition(), 0.25, voxel.getD0()));
            }
            else
            {
               voxelVisualization.get(VisualizationType.Unreachable).add(sphereVoxelShape.createDReachabilityVisual(voxel.getPosition(), 0.1, -1));
            }
         }
      }

      currentEvaluation.set(VisualizationType.PositionReach);

      for (int voxelIndex = 0; voxelIndex < grid.getNumberOfVoxels(); voxelIndex++)
      {
         Voxel3DData voxel = grid.getVoxel(voxelIndex);

         if (voxel == null)
            continue;

         VoxelExtraData positionExtraData = voxel.getPositionExtraData();

         if (positionExtraData == null)
            continue;

         writeVoxelJointData(positionExtraData, robot);
         currentEvaluationPose.getPosition().set(positionExtraData.getDesiredPosition());
         currentEvaluationPose.getOrientation().setFromReferenceFrame(controlFrame);
         visualizationSession.runTick();
      }

      currentEvaluation.set(VisualizationType.RayReach);

      for (int voxelIndex = 0; voxelIndex < grid.getNumberOfVoxels(); voxelIndex++)
      {
         Voxel3DData voxel = grid.getVoxel(voxelIndex);

         if (voxel == null)
            continue;

         VoxelExtraData positionExtraData = voxel.getPositionExtraData();

         if (positionExtraData == null)
            continue;

         for (int rayIndex = 0; rayIndex < sphereVoxelShape.getNumberOfRays(); rayIndex++)
         {
            VoxelExtraData rayExtraData = voxel.getRayExtraData(rayIndex);

            if (rayExtraData != null)
            {
               writeVoxelJointData(rayExtraData, robot);
               currentEvaluationPose.getPosition().set(rayExtraData.getDesiredPosition());
               currentEvaluationPose.getOrientation().set(rayExtraData.getDesiredOrientation());
               visualizationSession.runTick();
            }
         }
      }

      currentEvaluation.set(VisualizationType.PoseReach);

      for (int voxelIndex = 0; voxelIndex < grid.getNumberOfVoxels(); voxelIndex++)
      {
         Voxel3DData voxel = grid.getVoxel(voxelIndex);

         if (voxel == null)
            continue;

         VoxelExtraData positionExtraData = voxel.getPositionExtraData();

         if (positionExtraData == null)
            continue;

         for (int rayIndex = 0; rayIndex < sphereVoxelShape.getNumberOfRays(); rayIndex++)
         {
            for (int rotationIndex = 0; rotationIndex < sphereVoxelShape.getNumberOfRotationsAroundRay(); rotationIndex++)
            {
               VoxelExtraData poseExtraData = voxel.getPoseExtraData(rayIndex, rotationIndex);

               if (poseExtraData != null)
               {
                  writeVoxelJointData(poseExtraData, robot);
                  currentEvaluationPose.getPosition().set(poseExtraData.getDesiredPosition());
                  currentEvaluationPose.getOrientation().set(poseExtraData.getDesiredOrientation());
                  visualizationSession.runTick();
               }
            }
         }
      }

      visualizationSession.setSessionMode(SessionMode.PAUSE);
      guiControls.addStaticVisuals(createReachibilityColorScaleVisuals());
      guiControls.addStaticVisuals(ReachabilityMapTools.createBoundingBoxVisuals(grid));

      currentVisualizationType.addListener(v ->
      {
         guiControls.removeStaticVisuals(voxelVisualization.get(previousVisualizationType.get()));
         guiControls.addStaticVisuals(voxelVisualization.get(currentVisualizationType.getValue()));
         previousVisualizationType.set(currentVisualizationType.getValue());
      });

      guiControls.waitUntilDown();
   }

   public static void writeVoxelJointData(VoxelExtraData voxelExtraData, Robot robot)
   {
      DMatrixRMaj jointPositions = toVectorMatrix(voxelExtraData.getJointPositions());
      DMatrixRMaj jointTorques = toVectorMatrix(voxelExtraData.getJointTorques());

      int positionIndex = 0;
      int torqueIndex = 0;

      for (JointBasics joint : robot.getAllJoints())
      {
         positionIndex = joint.setJointConfiguration(positionIndex, jointPositions);
         torqueIndex = joint.setJointTau(torqueIndex, jointTorques);
      }
      robot.updateFrames();
   }

   public static DMatrixRMaj toVectorMatrix(float[] array)
   {
      DMatrixRMaj vector = new DMatrixRMaj(array.length, 1);
      for (int i = 0; i < array.length; i++)
      {
         vector.set(i, array[i]);
      }
      return vector;
   }
}
