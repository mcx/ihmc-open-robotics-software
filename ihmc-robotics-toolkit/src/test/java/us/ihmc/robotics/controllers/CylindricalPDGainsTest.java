package us.ihmc.robotics.controllers;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
public class CylindricalPDGainsTest
{
   @Test// timeout = 30000
   public void test()
   {
      Random random = new Random();
      for (int i = 0; i < 1000; i++)
      {
         double kpRadius = random.nextDouble() * 100;
         double kpAngle = random.nextDouble() * Math.PI * 2;
         double kpZ = random.nextDouble() * 100;
         double zeta = random.nextDouble() * 100;

         CylindricalPDGains cylindricalPDGains = new CylindricalPDGains(kpRadius, kpAngle, kpZ, zeta);
         
         assertEquals(kpRadius, cylindricalPDGains.getKpRadius(), 1e-6);
         assertEquals(kpAngle, cylindricalPDGains.getKpAngle(), 1e-6);
         assertEquals(kpZ, cylindricalPDGains.getKpZ(), 1e-6);
      }

   }

}
