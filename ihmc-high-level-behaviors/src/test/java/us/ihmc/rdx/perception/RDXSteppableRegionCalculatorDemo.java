package us.ihmc.rdx.perception;

import boofcv.struct.calib.CameraPinholeBrown;
import us.ihmc.communication.CommunicationMode;
import us.ihmc.communication.IHMCROS2Callback;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.ros2.ROS2Helper;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.ihmcPerception.heightMap.RemoteHeightMapUpdater;
import us.ihmc.ihmcPerception.steppableRegions.SteppableRegionsCalculationModule;
import us.ihmc.perception.BytedecoTools;
import us.ihmc.pubsub.DomainFactory;
import us.ihmc.rdx.Lwjgl3ApplicationAdapter;
import us.ihmc.rdx.sceneManager.RDXSceneLevel;
import us.ihmc.rdx.simulation.environment.RDXEnvironmentBuilder;
import us.ihmc.rdx.simulation.sensors.RDXHighLevelDepthSensorSimulator;
import us.ihmc.rdx.simulation.sensors.RDXSimulatedSensorFactory;
import us.ihmc.rdx.ui.RDXBaseUI;
import us.ihmc.rdx.ui.affordances.RDXInteractableReferenceFrame;
import us.ihmc.rdx.ui.gizmo.RDXPose3DGizmo;
import us.ihmc.rdx.ui.graphics.live.RDXHeightMapVisualizer;
import us.ihmc.rdx.ui.graphics.live.RDXROS2RobotVisualizer;
import us.ihmc.rdx.ui.visualizers.RDXGlobalVisualizersPanel;
import us.ihmc.ros2.RealtimeROS2Node;
import us.ihmc.tools.thread.Activator;

public class RDXSteppableRegionCalculatorDemo
{
   private final RDXBaseUI baseUI = new RDXBaseUI(getClass(),
                                                  "ihmc-open-robotics-software",
                                                  "ihmc-high-level-behaviors/src/test/resources");
   private Activator nativesLoadedActivator;
   private RDXHighLevelDepthSensorSimulator ouster;
   private RDXInteractableReferenceFrame robotInteractableReferenceFrame;
   private RDXPose3DGizmo ousterPoseGizmo = new RDXPose3DGizmo();
   private RDXEnvironmentBuilder environmentBuilder;
   private RDXSteppableRegionsCalculatorUI steppableRegionsCalculatorModule;

   private final RemoteHeightMapUpdater heightMap;
   private final RDXRemoteHeightMapPanel heightMapUI;
   private final RDXGlobalVisualizersPanel globalVisualizersUI;

   public RDXSteppableRegionCalculatorDemo()
   {
      CommunicationMode ros2CommunicationMode = CommunicationMode.INTERPROCESS;

      RealtimeROS2Node realtimeRos2Node = ROS2Tools.createRealtimeROS2Node(ros2CommunicationMode.getPubSubImplementation(), "simulation_ui_realtime");
      heightMap = new RemoteHeightMapUpdater(ReferenceFrame::getWorldFrame, realtimeRos2Node);
      heightMapUI = new RDXRemoteHeightMapPanel(new ROS2Helper(realtimeRos2Node));

      baseUI.getImGuiPanelManager().addPanel(heightMapUI.getPanel());

      // Configure the height map visualizer
      globalVisualizersUI = new RDXGlobalVisualizersPanel();

      RDXHeightMapVisualizer heightMapVisualizer = new RDXHeightMapVisualizer("Height Map");
      heightMapVisualizer.setActive(true);

      baseUI.getImGuiPanelManager().addPanel(globalVisualizersUI);
      baseUI.getPrimaryScene().addRenderableProvider(globalVisualizersUI, RDXSceneLevel.MODEL);
      baseUI.getPrimaryScene().addRenderableProvider(globalVisualizersUI, RDXSceneLevel.VIRTUAL);

      baseUI.launchRDXApplication(new Lwjgl3ApplicationAdapter()
      {
         @Override
         public void create()
         {
            nativesLoadedActivator = BytedecoTools.loadNativesOnAThread();

            heightMapUI.create();
            globalVisualizersUI.create();
            baseUI.create();

            environmentBuilder = new RDXEnvironmentBuilder(baseUI.getPrimary3DPanel());
            environmentBuilder.create();
            baseUI.getImGuiPanelManager().addPanel(environmentBuilder.getPanelName(), environmentBuilder::renderImGuiWidgets);
            environmentBuilder.loadEnvironment("DemoPullDoor.json");

            heightMapVisualizer.create();
//            baseUI.getImGuiPanelManager().addPanel(heightMapVisualizer.getPanel());
            globalVisualizersUI.addVisualizer(heightMapVisualizer);

            new IHMCROS2Callback<>(realtimeRos2Node, ROS2Tools.HEIGHT_MAP_OUTPUT, message ->
                                   {
                                      heightMapVisualizer.acceptHeightMapMessage(message);
                                      heightMapUI.acceptHeightMapMessage(message);
                                   });

            robotInteractableReferenceFrame = new RDXInteractableReferenceFrame();
            robotInteractableReferenceFrame.create(ReferenceFrame.getWorldFrame(), 0.15, baseUI.getPrimary3DPanel());
            robotInteractableReferenceFrame.getTransformToParent().getTranslation().add(2.2, 1.25, 1.0);
            baseUI.getPrimary3DPanel().addImGui3DViewInputProcessor(robotInteractableReferenceFrame::process3DViewInput);
            baseUI.getPrimaryScene().addRenderableProvider(robotInteractableReferenceFrame::getVirtualRenderables, RDXSceneLevel.VIRTUAL);
            ousterPoseGizmo = new RDXPose3DGizmo(robotInteractableReferenceFrame.getRepresentativeReferenceFrame());
            ousterPoseGizmo.create(baseUI.getPrimary3DPanel());
            ousterPoseGizmo.setResizeAutomatically(false);
            baseUI.getPrimary3DPanel().addImGui3DViewPickCalculator(ousterPoseGizmo::calculate3DViewPick);
            baseUI.getPrimary3DPanel().addImGui3DViewInputProcessor(ousterPoseGizmo::process3DViewInput);
            baseUI.getPrimaryScene().addRenderableProvider(ousterPoseGizmo, RDXSceneLevel.VIRTUAL);
            ousterPoseGizmo.getTransformToParent().appendPitchRotation(Math.toRadians(60.0));

         }

         @Override
         public void render()
         {
            if (nativesLoadedActivator.poll())
            {
               if (nativesLoadedActivator.isNewlyActivated())
               {
                  ouster = RDXSimulatedSensorFactory.createOusterLidar(ousterPoseGizmo.getGizmoFrame(), () -> 0L);
                  ouster.setupForROS2PointCloud(realtimeRos2Node, ROS2Tools.OUSTER_LIDAR_SCAN);
                  ouster.setSensorEnabled(true);
                  ouster.setRenderPointCloudDirectly(true);
                  ouster.setPublishPointCloudROS2(true);
                  ouster.setDebugCoordinateFrame(false);

                  baseUI.getImGuiPanelManager().addPanel(ouster);
                  baseUI.getPrimaryScene().addRenderableProvider(ouster::getRenderables);

                  steppableRegionsCalculatorModule = new RDXSteppableRegionsCalculatorUI();
                  steppableRegionsCalculatorModule.create(heightMap::getLatestHeightMap);
                  steppableRegionsCalculatorModule.getEnabled().set(true);
                  baseUI.getImGuiPanelManager().addPanel(steppableRegionsCalculatorModule.getPanel());


                  baseUI.getPrimaryScene().addRenderableProvider(steppableRegionsCalculatorModule::getVirtualRenderables, RDXSceneLevel.VIRTUAL);

                  baseUI.getPerspectiveManager().reloadPerspective();
                  realtimeRos2Node.spin();
                  heightMap.start();
               }

               ouster.render(baseUI.getPrimaryScene());

               heightMap.update();
               heightMapVisualizer.update();
               globalVisualizersUI.update();
               steppableRegionsCalculatorModule.extractSteppableRegions();

               heightMapUI.update();
            }

            baseUI.renderBeforeOnScreenUI();
            baseUI.renderEnd();
         }

         @Override
         public void dispose()
         {
            baseUI.dispose();
            environmentBuilder.destroy();
            realtimeRos2Node.destroy();
            globalVisualizersUI.destroy();
            steppableRegionsCalculatorModule.destroy();
            ouster.dispose();
         }
      });
   }

   public static void main(String[] args)
   {
      new RDXSteppableRegionCalculatorDemo();
   }
}
