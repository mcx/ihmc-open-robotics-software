package us.ihmc.gdx.ui.affordances;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import us.ihmc.euclid.geometry.interfaces.Line3DReadOnly;
import us.ihmc.euclid.geometry.tools.EuclidGeometryTools;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.shape.primitives.interfaces.*;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.UnitVector3DReadOnly;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.gdx.input.ImGui3DViewInput;
import us.ihmc.gdx.tools.GDXModelPrimitives;
import us.ihmc.gdx.tools.GDXTools;
import us.ihmc.gdx.ui.gizmo.BoxRayIntersection;
import us.ihmc.gdx.ui.gizmo.CapsuleRayIntersection;
import us.ihmc.gdx.ui.gizmo.SphereRayIntersection;
import us.ihmc.log.LogTools;
import us.ihmc.mecano.frames.MovingReferenceFrame;
import us.ihmc.robotics.physics.Collidable;
import us.ihmc.robotics.referenceFrames.ReferenceFrameMissingTools;

public class GDXRobotCollisionLink implements RenderableProvider
{
   private final ModelInstance modelInstance;
   private final RigidBodyTransform transformToJoint;
   private final RigidBodyTransform tempTransform = new RigidBodyTransform();
   private final ReferenceFrame collisionMeshFrame;
   private final FramePose3D boxPose = new FramePose3D();
   private final Shape3DReadOnly shape;
   private SphereRayIntersection sphereRayIntersection;
   private CapsuleRayIntersection capsuleIntersection;
   private BoxRayIntersection boxRayIntersection;
   private ModelInstance coordinateFrame;

   public GDXRobotCollisionLink(Collidable collidable, Color color)
   {
      shape = collidable.getShape();
      ReferenceFrame shapeFrame = collidable.getShape().getReferenceFrame();
      MovingReferenceFrame frameAfterJoint = collidable.getRigidBody().getParentJoint().getFrameAfterJoint();
      // TODO update every frame
      transformToJoint = new RigidBodyTransform(shapeFrame.getTransformToDesiredFrame(frameAfterJoint));
      collisionMeshFrame = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent("collisionMeshFrame" + collidable.getRigidBody().getName(),
                                                                                                  frameAfterJoint,
                                                                                                  transformToJoint);

      modelInstance = GDXModelPrimitives.buildModelInstance(meshBuilder ->
      {
         if (shape instanceof Sphere3DReadOnly)
         {
            Sphere3DReadOnly sphere = (Sphere3DReadOnly) shape;
            meshBuilder.addSphere((float) sphere.getRadius(), sphere.getPosition(), color);
            sphereRayIntersection = new SphereRayIntersection();
         }
         else if (shape instanceof Capsule3DReadOnly)
         {
            Capsule3DReadOnly capsule = (Capsule3DReadOnly) shape;
            Quaternion orientation = new Quaternion();
            EuclidGeometryTools.orientation3DFromZUpToVector3D(capsule.getAxis(), orientation);
            transformToJoint.appendTranslation(capsule.getPosition());
            transformToJoint.appendOrientation(orientation);
            meshBuilder.addCapsule(capsule.getLength(),
                                   capsule.getRadius(),
                                   capsule.getRadius(),
                                   capsule.getRadius(),
                                   50,
                                   50,
                                   color);
            capsuleIntersection = new CapsuleRayIntersection();
         }
         else if (shape instanceof Box3DReadOnly)
         {
            Box3DReadOnly box = (Box3DReadOnly) shape;
            transformToJoint.appendTranslation(box.getPosition());
            transformToJoint.appendOrientation(box.getOrientation());
            meshBuilder.addBox(box.getSizeX(),
                               box.getSizeY(),
                               box.getSizeZ(),
                               color);
            boxRayIntersection = new BoxRayIntersection();
         }
         else if (shape instanceof PointShape3DReadOnly)
         {
            PointShape3DReadOnly pointShape = (PointShape3DReadOnly) shape;
            meshBuilder.addSphere((float) 0.01, pointShape, color);
         }
         else
         {
            LogTools.warn("Shape not handled: {}", shape);
         }
      }, collidable.getRigidBody().getName());
      GDXTools.setTransparency(modelInstance, color.a);

      coordinateFrame = GDXModelPrimitives.createCoordinateFrameInstance(0.15);
   }

   public void update()
   {
      collisionMeshFrame.update();
      GDXTools.toGDX(collisionMeshFrame.getTransformToWorldFrame(), modelInstance.transform);
      GDXTools.toGDX(collisionMeshFrame.getTransformToWorldFrame(), coordinateFrame.transform);
   }

   // Happens after update
   public void process3DViewInput(ImGui3DViewInput input)
   {
      Line3DReadOnly pickRayInWorld = input.getPickRayInWorld();
      RigidBodyTransform transformToWorldFrame = collisionMeshFrame.getTransformToWorldFrame();
      boolean intersects = false;
      if (shape instanceof Sphere3DReadOnly)
      {
         Sphere3DReadOnly sphere = (Sphere3DReadOnly) shape;
         Point3DReadOnly position = sphere.getPosition();
         // TODO: Implement
      }
      else if (shape instanceof Capsule3DReadOnly)
      {
         Capsule3DReadOnly capsule = (Capsule3DReadOnly) shape;
         UnitVector3DReadOnly axis = capsule.getAxis();
         Point3DReadOnly position = capsule.getPosition();
         double length = capsule.getLength();
         double radius = capsule.getRadius();
         capsuleIntersection.setup(radius, length, position, axis, transformToWorldFrame);
         intersects = capsuleIntersection.intersect(pickRayInWorld);
         GDXTools.setTransparency(modelInstance, intersects ? 1.0f : 0.4f);
      }
      else if (shape instanceof Box3DReadOnly)
      {
         Box3DReadOnly box = (Box3DReadOnly) shape;
         boxPose.setToZero(collisionMeshFrame);
         boxPose.changeFrame(ReferenceFrame.getWorldFrame());
         double sizeX = box.getSizeX();
         double sizeY = box.getSizeY();
         double sizeZ = box.getSizeZ();
         intersects = boxRayIntersection.intersect(sizeX, sizeY, sizeZ, boxPose, pickRayInWorld);
      }
      else if (shape instanceof PointShape3DReadOnly)
      {
         PointShape3DReadOnly pointShape = (PointShape3DReadOnly) shape;
      }
      else
      {
         LogTools.warn("Shape not handled: {}", shape);
      }
      GDXTools.setTransparency(modelInstance, intersects ? 1.0f : 0.4f);
   }

   @Override
   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      modelInstance.getRenderables(renderables, pool);
      coordinateFrame.getRenderables(renderables, pool);
   }
}
