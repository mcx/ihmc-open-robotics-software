package us.ihmc.gdx.vr;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import org.lwjgl.openvr.VRControllerState;
import org.lwjgl.openvr.VRSystem;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.AffineTransform;
import us.ihmc.euclid.yawPitchRoll.YawPitchRoll;
import us.ihmc.gdx.tools.GDXTools;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;

/**
 * Represents a tracked VR device such as the head mounted
 * display, wands etc.
 */
public class GDXVRDevice
{
   private final GDXVRContext gdxVRContext;
   private final GDXVRDevicePose pose;
   private final GDXVRDeviceType type;
   private final GDXVRControllerRole role;
   private long buttons = 0;
   private final VRControllerState state = VRControllerState.create();
   private final ModelInstance modelInstance;
   private final String name;

   // tracker space
   private final Vector3 position = new Vector3();
   private final Vector3 xAxis = new Vector3();
   private final Vector3 yAxis = new Vector3();
   private final Vector3 zAxis = new Vector3();

   // world space
   private final Vector3 positionWorld = new Vector3();
   private final Vector3 xAxisWorld = new Vector3();
   private final Vector3 yAxisWorld = new Vector3();
   private final Vector3 zAxisWorld = new Vector3();

   private final Matrix4 worldTransformGDX = new Matrix4();
   private final AffineTransform worldTransformEuclid = new AffineTransform();
   private final PoseReferenceFrame referenceFrame;
   private final YawPitchRoll toZUpXForward = new YawPitchRoll(Math.toRadians(90.0), Math.toRadians(90.0), Math.toRadians(0.0));

   private final Vector3 vecTmp = new Vector3();
   private final Matrix4 matTmp = new Matrix4();

   GDXVRDevice(GDXVRContext gdxVRContext, GDXVRDevicePose pose, GDXVRDeviceType type, GDXVRControllerRole role)
   {
      this.gdxVRContext = gdxVRContext;
      this.pose = pose;
      this.type = type;
      this.role = role;
      Model model = gdxVRContext.loadRenderModel(getStringProperty(GDXVRDeviceProperty.RenderModelName_String));
      this.modelInstance = model != null ? new ModelInstance(model) : null;
      if (model != null)
         this.modelInstance.transform.set(pose.getTransform());

      String roleName = role == GDXVRControllerRole.LeftHand ? role.name() : "";
      roleName += role == GDXVRControllerRole.RightHand ? role.name() : "";
      name = type.name() + roleName;
      referenceFrame = new PoseReferenceFrame(name, ReferenceFrame.getWorldFrame());
   }

   /**
    * @return the most up-to-date {@link GDXVRDevicePose} in tracker space
    */
   public GDXVRDevicePose getPose()
   {
      return pose;
   }

   public void updateAxesAndPosition()
   {
      Matrix4 matrix = pose.getTransform();
      matrix.getTranslation(position);
      xAxis.set(matrix.val[Matrix4.M00], matrix.val[Matrix4.M10], matrix.val[Matrix4.M20]).nor();
      yAxis.set(matrix.val[Matrix4.M01], matrix.val[Matrix4.M11], matrix.val[Matrix4.M21]).nor();
      zAxis.set(matrix.val[Matrix4.M02], matrix.val[Matrix4.M12], matrix.val[Matrix4.M22]).nor().scl(-1);

      matTmp.set(gdxVRContext.getTrackerSpaceToWorldspaceRotationOffset());
      positionWorld.set(position).mul(matTmp);
      positionWorld.add(gdxVRContext.getTrackerSpaceOriginToWorldSpaceTranslationOffset());

      matTmp.set(gdxVRContext.getTrackerSpaceToWorldspaceRotationOffset());

      xAxisWorld.set(xAxis).mul(matTmp);
      yAxisWorld.set(yAxis).mul(matTmp);
      zAxisWorld.set(zAxis).mul(matTmp);

      worldTransformGDX.idt()
                       .translate(gdxVRContext.getTrackerSpaceOriginToWorldSpaceTranslationOffset())
                       .mul(gdxVRContext.getTrackerSpaceToWorldspaceRotationOffset())
                       .mul(pose.getTransform());
      GDXTools.toEuclid(worldTransformGDX, worldTransformEuclid);
      worldTransformEuclid.appendOrientation(toZUpXForward);
      GDXTools.toGDX(worldTransformEuclid, worldTransformGDX);

      referenceFrame.setX(worldTransformEuclid.getTranslation().getX());
      referenceFrame.setY(worldTransformEuclid.getTranslation().getY());
      referenceFrame.setZ(worldTransformEuclid.getTranslation().getZ());
      referenceFrame.setOrientationAndUpdate(worldTransformEuclid.getRotationView());
   }

   /**
    * @return the position in the given {@link GDXVRSpace}
    */
   public Vector3 getPosition(GDXVRSpace space)
   {
      return space == GDXVRSpace.Tracker ? position : positionWorld;
   }

   /**
    * @return the right vector in the given {@link GDXVRSpace}
    */
   public Vector3 getRight(GDXVRSpace space)
   {
      return space == GDXVRSpace.Tracker ? xAxis : xAxisWorld;
   }

   /**
    * @return the up vector in the given {@link GDXVRSpace}
    */
   public Vector3 getUp(GDXVRSpace space)
   {
      return space == GDXVRSpace.Tracker ? yAxis : yAxisWorld;
   }

   /**
    * @return the direction vector in the given {@link GDXVRSpace}
    */
   public Vector3 getDirection(GDXVRSpace space)
   {
      return space == GDXVRSpace.Tracker ? zAxis : zAxisWorld;
   }

   public PoseReferenceFrame getReferenceFrame()
   {
      return referenceFrame;
   }

   public Matrix4 getWorldTransformGDX()
   {
      return worldTransformGDX;
   }

   /**
    * @return the {@link GDXVRDeviceType}
    */
   public GDXVRDeviceType getType()
   {
      return type;
   }

   /**
    * The {@link GDXVRControllerRole}, indicating if the {@link GDXVRDevice} is assigned
    * to the left or right hand.
    *
    * <p>
    * <strong>Note</strong>: the role is not reliable! If one controller is connected on
    * startup, it will have a role of {@link GDXVRControllerRole#Unknown} and retain
    * that role even if a second controller is connected (which will also haven an
    * unknown role). The role is only reliable if two controllers are connected
    * already, and none of the controllers disconnects during the application
    * life-time.</br>
    * At least on the HTC Vive, the first connected controller is always the right hand
    * and the second connected controller is the left hand. The order stays the same
    * even if controllers disconnect/reconnect during the application life-time.
    * </p>
    */
   // FIXME role might change as per API, but never saw it
   public GDXVRControllerRole getControllerRole()
   {
      return role;
   }

   /**
    * @return whether the device is connected
    */
   public boolean isConnected()
   {
      return VRSystem.VRSystem_IsTrackedDeviceConnected(pose.getIndex());
   }

   /**
    * @return whether the button from {@link GDXVRControllerButtons} is pressed
    */
   public boolean isButtonPressed(int button)
   {
      if (button < 0 || button >= 64)
         return false;
      return (buttons & (1l << button)) != 0;
   }

   void setButton(int button, boolean pressed)
   {
      if (pressed)
      {
         buttons |= (1l << button);
      }
      else
      {
         buttons ^= (1l << button);
      }
   }

   /**
    * @return the x-coordinate in the range [-1, 1] of the given axis from {@link GDXVRControllerAxes}
    */
   public float getAxisX(int axis)
   {
      if (axis < 0 || axis >= 5)
         return 0;
      VRSystem.VRSystem_GetControllerState(pose.getIndex(), state);
      return state.rAxis(axis).x();
   }

   /**
    * @return the y-coordinate in the range [-1, 1] of the given axis from {@link GDXVRControllerAxes}
    */
   public float getAxisY(int axis)
   {
      if (axis < 0 || axis >= 5)
         return 0;
      VRSystem.VRSystem_GetControllerState(pose.getIndex(), state);
      return state.rAxis(axis).y();
   }

   /**
    * Trigger a haptic pulse (vibrate) for the duration in microseconds. Subsequent calls
    * to this method within 5ms will be ignored.
    *
    * @param duration pulse duration in microseconds
    */
   public void triggerHapticPulse(short duration)
   {
      VRSystem.VRSystem_TriggerHapticPulse(pose.getIndex(), 0, duration);
   }

   /**
    * @return a boolean property or false if the query failed
    */
   public boolean getBooleanProperty(GDXVRDeviceProperty property)
   {
      gdxVRContext.getScratch().put(0, 0);
      boolean result = VRSystem.VRSystem_GetBoolTrackedDeviceProperty(this.pose.getIndex(), property.value, gdxVRContext.getScratch());
      if (gdxVRContext.getScratch().get(0) != 0)
         return false;
      else
         return result;
   }

   /**
    * @return a float property or 0 if the query failed
    */
   public float getFloatProperty(GDXVRDeviceProperty property)
   {
      gdxVRContext.getScratch().put(0, 0);
      float result = VRSystem.VRSystem_GetFloatTrackedDeviceProperty(this.pose.getIndex(), property.value, gdxVRContext.getScratch());
      if (gdxVRContext.getScratch().get(0) != 0)
         return 0;
      else
         return result;
   }

   /**
    * @return an int property or 0 if the query failed
    */
   public int getInt32Property(GDXVRDeviceProperty property)
   {
      gdxVRContext.getScratch().put(0, 0);
      int result = VRSystem.VRSystem_GetInt32TrackedDeviceProperty(this.pose.getIndex(), property.value, gdxVRContext.getScratch());
      if (gdxVRContext.getScratch().get(0) != 0)
         return 0;
      else
         return result;
   }

   /**
    * @return a long property or 0 if the query failed
    */
   public long getUInt64Property(GDXVRDeviceProperty property)
   {
      gdxVRContext.getScratch().put(0, 0);
      long result = VRSystem.VRSystem_GetUint64TrackedDeviceProperty(this.pose.getIndex(), property.value, gdxVRContext.getScratch());
      if (gdxVRContext.getScratch().get(0) != 0)
         return 0;
      else
         return result;
   }

   /**
    * @return a string property or null if the query failed
    */
   public String getStringProperty(GDXVRDeviceProperty property)
   {
      gdxVRContext.getScratch().put(0, 0);

      String result = VRSystem.VRSystem_GetStringTrackedDeviceProperty(this.pose.getIndex(), property.value, gdxVRContext.getScratch());
      if (gdxVRContext.getScratch().get(0) != 0)
         return null;
      return result;
   }

   /**
    * @return a {@link ModelInstance} with the transform updated to the latest tracked position and orientation in world space for rendering or null
    */
   public ModelInstance getModelInstance()
   {
      return modelInstance;
   }

   @Override
   public String toString()
   {
      return "VRDevice[manufacturer=" + getStringProperty(GDXVRDeviceProperty.ManufacturerName_String) + ", renderModel=" + getStringProperty(
            GDXVRDeviceProperty.RenderModelName_String) + ", index=" + pose.getIndex() + ", type=" + type + ", role=" + role + "]";
   }
}
