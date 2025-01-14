package us.ihmc.rdx.ui.graphics;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import std_msgs.msg.dds.Empty;
import us.ihmc.communication.ros2.ROS2Heartbeat;
import us.ihmc.rdx.imgui.RDXPanel;
import us.ihmc.rdx.sceneManager.RDXSceneLevel;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2Topic;

import javax.annotation.Nullable;
import java.util.Set;

public class RDXVisualizerWithHeartbeat
{
   private final RDXVisualizer visualizer;
   @Nullable
   private ROS2Heartbeat heartbeat = null;
   // List of heartbeats that should be set alive when this class's heartbeat is alive
   private final RDXVisualizerWithHeartbeat[] dependentVisualizers;

   public RDXVisualizerWithHeartbeat(@Nullable ROS2Node node,
                                     @Nullable ROS2Topic<Empty> heartbeatTopic,
                                     RDXVisualizer visualizer,
                                     RDXVisualizerWithHeartbeat... dependentVisualizers)
   {
      this.visualizer = visualizer;
      this.dependentVisualizers = dependentVisualizers;
      if (heartbeatTopic != null)
      {
         heartbeat = new ROS2Heartbeat(node, heartbeatTopic);
         heartbeat.setAlive(visualizer.isActive());
      }
   }

   public void create()
   {
      visualizer.create();
   }

   public void renderImGuiWidgets()
   {
      visualizer.renderImGuiWidgets();

      if (heartbeat != null)
      {
         heartbeat.setAlive(visualizer.isActive());

         for (RDXVisualizerWithHeartbeat dependentVisualizer : dependentVisualizers)
         {
            if (dependentVisualizer.getHeartbeat() != null && !dependentVisualizer.isActive())
            {
               dependentVisualizer.getHeartbeat().setAlive(visualizer.isActive());
            }
         }
      }
   }

   public void update()
   {
      visualizer.update();
   }

   public RDXPanel getPanel()
   {
      return visualizer.getPanel();
   }

   public boolean isActive()
   {
      return visualizer.isActive();
   }

   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool, Set<RDXSceneLevel> sceneLevels)
   {
      visualizer.getRenderables(renderables, pool, sceneLevels);
   }

   public ROS2Heartbeat getHeartbeat()
   {
      return heartbeat;
   }

   public void destroy()
   {
      visualizer.destroy();
      if (heartbeat != null)
         heartbeat.destroy();
   }
}
