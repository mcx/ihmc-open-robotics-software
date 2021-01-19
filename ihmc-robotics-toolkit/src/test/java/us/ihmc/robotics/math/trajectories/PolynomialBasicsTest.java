package us.ihmc.robotics.math.trajectories;

import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import org.omg.SendingContext.RunTime;
import us.ihmc.commons.Assertions;
import us.ihmc.commons.RandomNumbers;
import us.ihmc.euclid.tools.EuclidCoreTools;
import us.ihmc.robotics.math.trajectories.core.Polynomial;
import us.ihmc.robotics.math.trajectories.interfaces.PolynomialBasics;
import us.ihmc.robotics.math.trajectories.yoVariables.YoPolynomial;
import us.ihmc.yoVariables.registry.YoRegistry;

import java.util.Random;

import static us.ihmc.robotics.Assert.*;

public abstract class PolynomialBasicsTest
{
   private static final int ITERATIONS = 1000;
   private static final double SMALL_EPSILON = 1.0e-12;

   public abstract PolynomialBasics getPolynomial(int maxNumberOfCoefficients);

   @Test
   public void testLinearSet()
   {
      PolynomialBasics traj = getPolynomial(2);
      assertEquals(2, traj.getMaximumNumberOfCoefficients());

      assertEquals(0, traj.getNumberOfCoefficients());
      traj.setLinear(1, 2, 3, 5);
      assertEquals(1, traj.getInitialTime(), SMALL_EPSILON);
      assertEquals(2, traj.getFinalTime(), SMALL_EPSILON);
      traj.compute(traj.getInitialTime());
      assertEquals(3.0, traj.getValue(), SMALL_EPSILON);
      traj.compute(traj.getFinalTime());
      assertEquals(5.0, traj.getValue(), SMALL_EPSILON);
      assertEquals(2, traj.getCoefficient(1), SMALL_EPSILON);
      assertEquals(1, traj.getCoefficient(0), SMALL_EPSILON);
   }

   @Test
   public void testExceptionThrownAfterShift()
   {
      PolynomialBasics trajectory = getPolynomial(5);
      trajectory.setCubic(0.0, 5.0, 0.5, 1.0, 0.6, 0.84);

      boolean caughtException = false;
      try
      {
         trajectory.initialize();
      }
      catch (RuntimeException e)
      {
         caughtException = true;
      }

      assertFalse(caughtException);

      trajectory.shiftTrajectory(0.5);

      try
      {
         trajectory.initialize();
      }
      catch (RuntimeException e)
      {
         caughtException = true;
      }

      assertTrue(caughtException);
   }

   @Test
   public void testSetConstant() throws Exception
   {
      Random random = new Random(3453);

      for (int i = 0; i < ITERATIONS; i++)
      {
         int maxNumberOfCoefficients = random.nextInt(10);
         PolynomialBasics trajectory = getPolynomial(maxNumberOfCoefficients);
         double t0 = random.nextDouble();
         double tf = t0 + random.nextDouble();
         double z = RandomNumbers.nextDouble(random, 1.0);

         assertEquals(maxNumberOfCoefficients, trajectory.getMaximumNumberOfCoefficients());

         if (maxNumberOfCoefficients < 1)
         {
            Assertions.assertExceptionThrown(RuntimeException.class, () -> trajectory.setConstant(t0, tf, z));
            continue;
         }

         trajectory.setConstant(t0, tf, z);

         for (double t = t0; t <= tf; t += (tf - t0) / 1000)
         {
            trajectory.compute(t);
            assertEquals(z, trajectory.getValue(), SMALL_EPSILON);
            assertEquals(0, trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);
         }
      }
   }

   @Test
   public void testSetLinear() throws Exception
   {
      Random random = new Random(3453);

      for (int i = 0; i < ITERATIONS; i++)
      {
         int maxNumberOfCoefficients = random.nextInt(10);
         PolynomialBasics trajectory = getPolynomial(maxNumberOfCoefficients);
         double t0 = random.nextDouble();
         double tf = t0 + random.nextDouble();
         double z0 = RandomNumbers.nextDouble(random, 1.0);
         double zf = RandomNumbers.nextDouble(random, 1.0);

         assertEquals(maxNumberOfCoefficients, trajectory.getMaximumNumberOfCoefficients());


         if (maxNumberOfCoefficients < 2)
         {
            Assertions.assertExceptionThrown(RuntimeException.class, () -> trajectory.setLinear(t0, tf, z0, zf));
            continue;
         }

         trajectory.setLinear(t0, tf, z0, zf);

         double zDot = (zf - z0) / (tf - t0);
         PolynomialBasics derivative = getPolynomial(1);
         derivative.setConstant(t0, tf, zDot);

         trajectory.compute(t0);
         assertEquals(z0, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
         assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

         trajectory.compute(tf);
         assertEquals(zf, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
         assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

         for (double t = t0; t <= tf; t += (tf - t0) / 1000)
         {
            trajectory.compute(t);
            assertEquals(EuclidCoreTools.interpolate(z0, zf, (t - t0) / (tf - t0)), trajectory.getValue(), SMALL_EPSILON);
            assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

            derivative.compute(t);
            assertEquals(derivative.getValue(), trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(derivative.getVelocity(), trajectory.getAcceleration(), SMALL_EPSILON);
         }

         trajectory.setLinear(t0, z0, zDot);

         derivative = getPolynomial(1);
         derivative.setConstant(t0, tf, zDot);

         trajectory.compute(t0);
         assertEquals(z0, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
         assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

         trajectory.compute(tf);
         assertEquals(zf, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
         assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

         for (double t = t0; t <= tf; t += (tf - t0) / 1000)
         {
            trajectory.compute(t);
            assertEquals(EuclidCoreTools.interpolate(z0, zf, (t - t0) / (tf - t0)), trajectory.getValue(), SMALL_EPSILON);
            assertEquals(zDot, trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(0, trajectory.getAcceleration(), SMALL_EPSILON);

            derivative.compute(t);
            assertEquals(derivative.getValue(), trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(derivative.getVelocity(), trajectory.getAcceleration(), SMALL_EPSILON);
         }
      }
   }

   @Test
   public void testSetQuadratic() throws Exception
   {
      Random random = new Random(3453);

      for (int i = 0; i < ITERATIONS; i++)
      {
         int maxNumberOfCoefficients = random.nextInt(10);
         PolynomialBasics trajectory = getPolynomial(maxNumberOfCoefficients);
         double t0 = random.nextDouble();
         double tf = t0 + 0.5;
         double z0 = RandomNumbers.nextDouble(random, 1.0);
         double zd0 = RandomNumbers.nextDouble(random, 1.0);
         double zf = RandomNumbers.nextDouble(random, 1.0);

         if (maxNumberOfCoefficients < 3)
         {
            Assertions.assertExceptionThrown(RuntimeException.class, () -> trajectory.setQuadratic(t0, tf, z0, zd0, zf));
            continue;
         }

         assertEquals(maxNumberOfCoefficients, trajectory.getMaximumNumberOfCoefficients());


         trajectory.setQuadratic(t0, tf, z0, zd0, zf);

         trajectory.compute(t0);
         assertEquals(z0, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zd0, trajectory.getVelocity(), SMALL_EPSILON);

         trajectory.compute(tf);
         assertEquals(zf, trajectory.getValue(), SMALL_EPSILON);

         PolynomialBasics derivative = getPolynomial(2);
         derivative.setLinear(t0, tf, zd0, trajectory.getVelocity());

         double dt = 1.0e-8;

         for (double t = t0; t <= tf; t += (tf - t0) / 1000)
         {
            trajectory.compute(t);
            derivative.compute(t);

            assertEquals(derivative.getValue(), trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(derivative.getVelocity(), trajectory.getAcceleration(), SMALL_EPSILON);

            trajectory.compute(t + dt);
            double nextPosition = trajectory.getValue();
            trajectory.compute(t - dt);
            double lastPosition = trajectory.getValue();
            assertEquals(0.5 * (nextPosition - lastPosition) / dt, trajectory.getVelocity(), 1.0e-6);

         }
      }
   }

   @Test
   public void testSetCubic() throws Exception
   {
      Random random = new Random(3453);

      for (int i = 0; i < ITERATIONS; i++)
      {
         int maxNumberOfCoefficients = random.nextInt(10);
         PolynomialBasics trajectory = getPolynomial(maxNumberOfCoefficients);
         double t0 = random.nextDouble();
         double tf = t0 + 0.5;
         double z0 = RandomNumbers.nextDouble(random, 1.0);
         double zd0 = RandomNumbers.nextDouble(random, 1.0);
         double zf = RandomNumbers.nextDouble(random, 1.0);
         double zdf = RandomNumbers.nextDouble(random, 1.0);

         if (maxNumberOfCoefficients < 4)
         {
            Assertions.assertExceptionThrown(RuntimeException.class, () -> trajectory.setCubic(t0, tf, z0, zd0, zf, zdf));
            continue;
         }

         assertEquals(maxNumberOfCoefficients, trajectory.getMaximumNumberOfCoefficients());


         trajectory.setCubic(t0, tf, z0, zd0, zf, zdf);

         trajectory.compute(t0);
         assertEquals(z0, trajectory.getValue(), SMALL_EPSILON);
         assertEquals(zd0, trajectory.getVelocity(), SMALL_EPSILON);

         PolynomialBasics derivative = getPolynomial(3);
         derivative.setQuadratic(t0, tf, zd0, trajectory.getAcceleration(), zdf);

         trajectory.compute(tf);
         assertEquals(zf, trajectory.getValue(), SMALL_EPSILON);

         double dt = 1.0e-8;

         for (double t = t0; t <= tf; t += (tf - t0) / 1000)
         {
            trajectory.compute(t);
            derivative.compute(t);

            assertEquals(derivative.getValue(), trajectory.getVelocity(), SMALL_EPSILON);
            assertEquals(derivative.getVelocity(), trajectory.getAcceleration(), SMALL_EPSILON);

            trajectory.compute(t + dt);
            double nextPosition = trajectory.getValue();
            trajectory.compute(t - dt);
            double lastPosition = trajectory.getValue();
            assertEquals(0.5 * (nextPosition - lastPosition) / dt, trajectory.getVelocity(), 5.0e-6);

         }
      }
   }


}