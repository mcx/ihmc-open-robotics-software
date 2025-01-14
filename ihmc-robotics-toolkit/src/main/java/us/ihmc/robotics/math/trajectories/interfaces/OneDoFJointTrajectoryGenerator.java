package us.ihmc.robotics.math.trajectories.interfaces;

import us.ihmc.robotics.math.trajectories.interfaces.DoubleTrajectoryGenerator;

public interface OneDoFJointTrajectoryGenerator extends DoubleTrajectoryGenerator
{
   public abstract void initialize(double initialPosition, double initialVelocity);
}
