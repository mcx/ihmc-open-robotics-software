package us.ihmc.rdx.simulation.environment.object.objects.door;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.rdx.tools.RDXModelInstance;
import us.ihmc.rdx.tools.RDXModelLoader;
import us.ihmc.rdx.ui.graphics.RDXReferenceFrameGraphic;
import us.ihmc.robotics.referenceFrames.ReferenceFrameMissingTools;
import us.ihmc.scs2.definition.visual.ColorDefinition;
import us.ihmc.scs2.definition.visual.ColorDefinitions;

public class RDXVirtualGhostObject extends RDXModelInstance
{
   private static final ColorDefinition GHOST_COLOR = ColorDefinitions.parse("0x4B61D1").derive(0.0, 1.0, 1.0, 0.5);
   private final RDXReferenceFrameGraphic referenceFrameGraphic;
   private RigidBodyTransform transformToParent = new RigidBodyTransform();
   private final ReferenceFrame referenceFrame = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent(ReferenceFrame.getWorldFrame(),
                                                                                                                        transformToParent);
   public RDXVirtualGhostObject(String modelName)
   {
      super(RDXModelLoader.load(modelName));
      setColor(GHOST_COLOR);

      referenceFrameGraphic = new RDXReferenceFrameGraphic(0.05, Color.BLUE);
   }

   public void update()
   {
      referenceFrame.update();
      referenceFrameGraphic.setToReferenceFrame(referenceFrame);
      setTransformToWorldFrame(transformToParent);
   }

   @Override
   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      super.getRenderables(renderables, pool);

      referenceFrameGraphic.getRenderables(renderables, pool);
   }

   public RigidBodyTransform getTransformToParent()
   {
      return transformToParent;
   }

   public void setTransformToParent(RigidBodyTransform transform)
   {
      transformToParent = transform;
   }

   public ReferenceFrame getReferenceFrame()
   {
      return referenceFrame;
   }
}
