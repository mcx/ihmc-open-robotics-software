package us.ihmc.gdx;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.gdx.sceneManager.GDXSceneLevel;
import us.ihmc.gdx.ui.graphics.live.GDXROS2PointCloudVisualizer;
import us.ihmc.gdx.vr.GDXVRApplication;
import us.ihmc.gdx.vr.GDXVRContext;
import us.ihmc.gdx.vr.GDXVRControllerButtons;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.ROS2Node;

import static us.ihmc.pubsub.DomainFactory.PubSubImplementation.*;

public class GDXVROnlyPointCloudDemo
{
   private final GDXVRApplication vrApplication = new GDXVRApplication();
   private final GDXROS2PointCloudVisualizer fusedPointCloud;

   public GDXVROnlyPointCloudDemo()
   {
      vrApplication.create();

      vrApplication.getSceneBasics().addDefaultLighting();
      vrApplication.getSceneBasics().addCoordinateFrame(1.0);
      vrApplication.getSceneBasics().addRenderableProvider(this::getVirtualRenderables, GDXSceneLevel.VIRTUAL);
      vrApplication.getVRContext().addVRInputProcessor(this::processVRInput);

      ROS2Node ros2Node = ROS2Tools.createROS2Node(FAST_RTPS, "vr_viewer");
      fusedPointCloud = new GDXROS2PointCloudVisualizer("Fused Point Cloud", ros2Node, ROS2Tools.MULTISENSE_LIDAR_SCAN);
      fusedPointCloud.create();
      fusedPointCloud.setActive(true);

      vrApplication.run();
   }

   private void getVirtualRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      fusedPointCloud.update();
      fusedPointCloud.getRenderables(renderables, pool);
      for (RobotSide side : RobotSide.values)
      {
         vrApplication.getVRContext().getController(side, controller -> controller.getModelInstance().getRenderables(renderables, pool));
         vrApplication.getVRContext().getEyes().get(side).getCoordinateFrameInstance().getRenderables(renderables, pool);
      }
      vrApplication.getVRContext().getBaseStations(baseStation -> baseStation.getModelInstance().getRenderables(renderables, pool));
      vrApplication.getVRContext().getGenericDevices(genericDevice -> genericDevice.getModelInstance().getRenderables(renderables, pool));
   }

   private void processVRInput(GDXVRContext vrContext)
   {
      vrContext.getController(RobotSide.RIGHT, controller ->
      {
         if (controller.isButtonNewlyPressed(GDXVRControllerButtons.INDEX_A))
         {
            vrApplication.exit();
         }
      });
   }

   public static void main(String[] args)
   {
      new GDXVROnlyPointCloudDemo();
   }
}
