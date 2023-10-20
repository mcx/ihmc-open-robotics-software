package us.ihmc.rdx.ui.behavior.tree;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.fasterxml.jackson.databind.JsonNode;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.ROS2SyncedRobotModel;
import us.ihmc.behaviors.behaviorTree.BehaviorTreeState;
import us.ihmc.behaviors.behaviorTree.modification.BehaviorTreeStateModificationQueue;
import us.ihmc.communication.ros2.ROS2ControllerPublishSubscribeAPI;
import us.ihmc.rdx.imgui.RDXPanel;
import us.ihmc.rdx.input.ImGui3DViewInput;
import us.ihmc.rdx.sceneManager.RDXSceneLevel;
import us.ihmc.rdx.ui.RDX3DPanel;
import us.ihmc.rdx.ui.RDXBaseUI;
import us.ihmc.rdx.ui.behavior.tree.modification.RDXBehaviorTreeSubtreeDestruction;
import us.ihmc.rdx.ui.behavior.tree.modification.RDXBehaviorTreeNodeAddition;
import us.ihmc.rdx.vr.RDXVRContext;
import us.ihmc.robotics.physics.RobotCollisionModel;
import us.ihmc.robotics.referenceFrames.ReferenceFrameLibrary;
import us.ihmc.tools.io.JSONFileTools;
import us.ihmc.tools.io.JSONTools;
import us.ihmc.tools.io.WorkspaceResourceFile;

public class RDXBehaviorTree
{
   private final RDXPanel panel = new RDXPanel("Behavior Tree", this::renderImGuiWidgets, false, true);
   private final BehaviorTreeState behaviorTreeState = new BehaviorTreeState();
   private final RDXBehaviorTreeNodeBuilder nodeBuilder;
   private RDXBehaviorTreeNode rootNode;
   /**
    * Useful for accessing nodes by ID instead of searching.
    * Also, sometimes, the tree will be disassembled and this is used in putting it
    * back together.
    */
   private transient final TLongObjectMap<RDXBehaviorTreeNode> idToNodeMap = new TLongObjectHashMap<>();

   public RDXBehaviorTree(DRCRobotModel robotModel,
                          ROS2SyncedRobotModel syncedRobot,
                          RobotCollisionModel selectionCollisionModel,
                          RDXBaseUI baseUI,
                          RDX3DPanel panel3D,
                          ReferenceFrameLibrary referenceFrameLibrary,
                          ROS2ControllerPublishSubscribeAPI ros2)
   {
      // TODO: Do we create the publishers and subscribers here?

      nodeBuilder = new RDXBehaviorTreeNodeBuilder(robotModel, syncedRobot, selectionCollisionModel, baseUI, panel3D, referenceFrameLibrary, ros2);
   }

   public void createAndSetupDefault(RDXBaseUI baseUI)
   {
      baseUI.getImGuiPanelManager().addPanel(panel);
      baseUI.getPrimaryScene().addRenderableProvider(this::getRenderables, RDXSceneLevel.VIRTUAL);
      baseUI.getVRManager().getContext().addVRPickCalculator(this::calculateVRPick);
      baseUI.getVRManager().getContext().addVRInputProcessor(this::processVRInput);
      baseUI.getPrimary3DPanel().addImGui3DViewPickCalculator(this::calculate3DViewPick);
      baseUI.getPrimary3DPanel().addImGui3DViewInputProcessor(this::process3DViewInput);
   }

   public void loadFromFile()
   {
      WorkspaceResourceFile file = null; // FIXME

      // Delete the entire tree. We are starting over
      behaviorTreeState.modifyTree(modificationQueue ->
      {
         modificationQueue.accept(new RDXBehaviorTreeSubtreeDestruction(rootNode));

         JSONFileTools.load(file, jsonNode ->
         {
            rootNode = loadFromFile(jsonNode, null, modificationQueue);
         });
      });
   }

   public RDXBehaviorTreeNode loadFromFile(JsonNode jsonNode, RDXBehaviorTreeNode parentNode, BehaviorTreeStateModificationQueue modificationQueue)
   {
      String typeName = jsonNode.get("type").textValue();

      RDXBehaviorTreeNode node = nodeBuilder.createNode(RDXBehaviorTreeTools.getClassFromTypeName(typeName),
                                                        behaviorTreeState.getNextID().getAndIncrement());

      node.getDefinition().loadFromFile(jsonNode);

      if (parentNode != null)
      {
         modificationQueue.accept(new RDXBehaviorTreeNodeAddition(node, parentNode));
      }

      JSONTools.forEachArrayElement(jsonNode, "children", childJsonNode ->
      {
         loadFromFile(childJsonNode, node, modificationQueue);
      });

      return node;
   }

   public void update()
   {
      idToNodeMap.clear();
      updateCaches(rootNode);
   }

   private void updateCaches(RDXBehaviorTreeNode node)
   {
      idToNodeMap.put(node.getState().getID(), node);

      for (RDXBehaviorTreeNode child : node.getChildren())
      {
         updateCaches(child);
      }
   }

   private void calculateVRPick(RDXVRContext vrContext)
   {

   }

   private void processVRInput(RDXVRContext vrContext)
   {

   }

   private void renderImGuiWidgets()
   {

   }

   private void calculate3DViewPick(ImGui3DViewInput input)
   {

   }

   private void process3DViewInput(ImGui3DViewInput input)
   {

   }

   private void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {

   }

   public void destroy()
   {

   }

   public TLongObjectMap<RDXBehaviorTreeNode> getIDToNodeMap()
   {
      return idToNodeMap;
   }
}
