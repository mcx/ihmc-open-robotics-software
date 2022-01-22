package us.ihmc.gdx.simulation.environment.object.objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import us.ihmc.euclid.shape.primitives.Box3D;
import us.ihmc.euclid.shape.primitives.Sphere3D;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.gdx.simulation.environment.object.GDXEnvironmentObject;
import us.ihmc.gdx.simulation.environment.object.GDXEnvironmentObjectFactory;
import us.ihmc.gdx.tools.GDXModelLoader;
import us.ihmc.gdx.tools.GDXModelPrimitives;
import us.ihmc.gdx.tools.GDXTools;
import us.ihmc.graphicsDescription.appearance.YoAppearance;

public class GDXL515SensorObject extends GDXEnvironmentObject
{
   public static final String NAME = "L515 Sensor";
   public static final GDXEnvironmentObjectFactory FACTORY = new GDXEnvironmentObjectFactory(NAME, GDXL515SensorObject.class);

   public GDXL515SensorObject()
   {
      super(NAME, FACTORY);
      Model realisticModel = GDXModelLoader.loadG3DModel("environmentObjects/l515Sensor/L515Sensor.g3dj");

      RigidBodyTransform collisionShapeOffset = new RigidBodyTransform();
      RigidBodyTransform wholeThingOffset = new RigidBodyTransform();
      Sphere3D boundingSphere = new Sphere3D(0.7);
      double sizeX = 0.3;
      double sizeY = 0.3;
      double sizeZ = 0.01;
      Box3D collisionBox = new Box3D(sizeX, sizeY, sizeZ);

      Model collisionGraphic = GDXModelPrimitives.buildModel(meshBuilder ->
      {
         Color color = GDXTools.toGDX(YoAppearance.LightSkyBlue());
         meshBuilder.addBox((float) sizeX, (float) sizeY, (float) sizeZ, color);
         meshBuilder.addMultiLineBox(collisionBox.getVertices(), 0.01, color); // some can see it better
      }, getPascalCasedName() + "CollisionModel" + getObjectIndex());
      collisionGraphic.materials.get(0).set(new BlendingAttribute(true, 0.4f));

      create(realisticModel, collisionGraphic, collisionShapeOffset,
             wholeThingOffset,
             boundingSphere,
             collisionBox,
             collisionBox::isPointInside);
   }
}
