package us.ihmc.robotics.trajectories;

import static us.ihmc.robotics.Assert.*;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
public class MinimumJerkTrajectoryTest
{
   public MinimumJerkTrajectoryTest()
   {
   }


   private double randomBetween(double min, double max)
   {
      return min + Math.random() * (max - min);
   }

	@Test
   public void testRandomInitialFinalConditions()
   {
      MinimumJerkTrajectory minimumJerkTrajectory = new MinimumJerkTrajectory();


      int numberOfTests = 1000000;
      double epsilon = 1e-3;

      for (int i = 0; i < numberOfTests; i++)
      {
         double x0 = randomBetween(-10.0, 10.0);
         double v0 = randomBetween(-10.0, 10.0);
         double a0 = randomBetween(-10.0, 10.0);
         double xf = randomBetween(-10.0, 10.0);
         double vf = randomBetween(-10.0, 10.0);
         double af = randomBetween(-10.0, 10.0);

         double moveDuration = randomBetween(0.1, 10.0);

         minimumJerkTrajectory.setMoveParameters(x0, v0, a0, xf, vf, af, moveDuration);

         minimumJerkTrajectory.computeTrajectory(randomBetween(-10.0, 10.0));

         minimumJerkTrajectory.computeTrajectory(0.0);
         assertEquals(x0, minimumJerkTrajectory.getPosition(), epsilon);
         assertEquals(v0, minimumJerkTrajectory.getVelocity(), epsilon);
         assertEquals(a0, minimumJerkTrajectory.getAcceleration(), epsilon);

         minimumJerkTrajectory.computeTrajectory(moveDuration);
         assertEquals(xf, minimumJerkTrajectory.getPosition(), epsilon);
         assertEquals(vf, minimumJerkTrajectory.getVelocity(), epsilon);
         assertEquals(af, minimumJerkTrajectory.getAcceleration(), epsilon);
      }
   }

	@Test
   public void testCheckVelocityAndAcceleration()
   {
      double x0 = 0.0;
      double v0 = 0.0;
      double a0 = 0.0;
      double xf = 1.0;
      double vf = 0.0;
      double af = 0.0;

      double moveDuration = 2.0;
      MinimumJerkTrajectory minimumJerkTrajectory = new MinimumJerkTrajectory();

      minimumJerkTrajectory.setMoveParameters(x0, v0, a0, xf, vf, af, moveDuration);

      double epsilon = 1e-6;

      minimumJerkTrajectory.computeTrajectory(0.0);
      assertEquals(x0, minimumJerkTrajectory.getPosition(), epsilon);
      assertEquals(v0, minimumJerkTrajectory.getVelocity(), epsilon);
      assertEquals(a0, minimumJerkTrajectory.getAcceleration(), epsilon);

      minimumJerkTrajectory.computeTrajectory(moveDuration);
      assertEquals(xf, minimumJerkTrajectory.getPosition(), epsilon);
      assertEquals(vf, minimumJerkTrajectory.getVelocity(), epsilon);
      assertEquals(af, minimumJerkTrajectory.getAcceleration(), epsilon);
   }
}
