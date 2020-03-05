package us.ihmc.robotics.math.trajectories;

import us.ihmc.euclid.referenceFrame.FrameQuaternion;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.commons.MathTools;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFrameQuaternionBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFrameVector3DBasics;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoFrameQuaternion;
import us.ihmc.robotics.trajectories.providers.OrientationProvider;

public class ConstantOrientationTrajectoryGenerator implements OrientationTrajectoryGenerator
{
   private final YoVariableRegistry registry;
   private final YoDouble finalTime;
   private final YoDouble time;
   private final OrientationProvider orientationProvider;
   private final YoFrameQuaternion orientation;

   public ConstantOrientationTrajectoryGenerator(String namePrefix, ReferenceFrame referenceFrame, OrientationProvider orientationProvider, double finalTime,
                                                 YoVariableRegistry parentRegistry)
   {
      MathTools.checkIntervalContains(finalTime, 0.0, Double.POSITIVE_INFINITY);

      this.registry = new YoVariableRegistry(namePrefix + getClass().getSimpleName());
      this.orientationProvider = orientationProvider;
      this.orientation = new YoFrameQuaternion("orientation", referenceFrame, registry);
      this.finalTime = new YoDouble("finalTime", registry);
      this.time = new YoDouble("time", registry);
      this.finalTime.set(finalTime);

      parentRegistry.addChild(registry);
   }

   public void initialize()
   {
      time.set(0.0);
      FrameQuaternion orientationToPack = new FrameQuaternion(ReferenceFrame.getWorldFrame());
      orientationProvider.getOrientation(orientationToPack);
      orientationToPack.changeFrame(orientation.getReferenceFrame());
      orientation.set(orientationToPack);
   }

   public void compute(double time)
   {
      this.time.set(time);
   }

   public boolean isDone()
   {
      return time.getDoubleValue() > finalTime.getDoubleValue();
   }

   public void getOrientation(FixedFrameQuaternionBasics orientationToPack)
   {
      orientationToPack.set(orientation);
   }

   public void getAngularVelocity(FixedFrameVector3DBasics angularVelocityToPack)
   {
      angularVelocityToPack.checkReferenceFrameMatch(orientation);
      angularVelocityToPack.setToZero();
   }

   public void getAngularAcceleration(FixedFrameVector3DBasics angularAccelerationToPack)
   {
      angularAccelerationToPack.checkReferenceFrameMatch(orientation);
      angularAccelerationToPack.setToZero();
   }
}
