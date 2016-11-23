package us.ihmc.tools.time;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import us.ihmc.tools.continuousIntegration.ContinuousIntegrationAnnotations.ContinuousIntegrationTest;
import us.ihmc.tools.testing.MutationTestingTools;
import us.ihmc.tools.thread.ThreadTools;

public class TimerTest
{
   @ContinuousIntegrationTest(estimatedDuration = 1.8)
   @Test(timeout = 30000)
   public void testTimer()
   {
      Timer timer = new Timer().start();
      
      double lapElapsed = timer.lapElapsed();
      double totalElapsed = timer.totalElapsed();
      double averageLap = timer.averageLap();
      assertEquals("lapElapsed incorrect", 0.0, lapElapsed, 1e-2);
      assertEquals("totalElapsed incorrect", 0.0, totalElapsed, 1e-2);
      assertEquals("averageLap incorrect", Double.NaN, averageLap, 1e-2);
      
      double sleepTime1 = 0.5;
      ThreadTools.sleepSeconds(sleepTime1);
      
      double lap = timer.lap();
      assertEquals("lap incorrect", sleepTime1, lap, 1e-2);
      
      double sleepTime2 = 1.0;
      ThreadTools.sleepSeconds(sleepTime2);
      
      lap = timer.lap();
      averageLap = timer.averageLap();
      assertEquals("lap incorrect", sleepTime2, lap, 1e-2);
      assertEquals("averageLap incorrect", (sleepTime1 + sleepTime2) / 2.0, averageLap, 1e-2);
      
      timer.resetLap();
      lapElapsed = timer.lapElapsed();
      assertEquals("lapElapsed incorrect", 0.0, lapElapsed, 1e-2);
      
      lap = timer.lap();
      averageLap = timer.averageLap();
      assertEquals("lap incorrect", 0.0, lap, 1e-2);
      assertEquals("averageLap incorrect", (sleepTime1 + sleepTime2) / 3.0, averageLap, 1e-2);
      
      double sleepTime3 = 0.3;
      ThreadTools.sleepSeconds(sleepTime3);
      
      lapElapsed = timer.lapElapsed();
      totalElapsed = timer.totalElapsed();
      assertEquals("lapElapsed incorrect", sleepTime3, lapElapsed, 1e-2);
      assertEquals("totalElapsed incorrect", sleepTime1 + sleepTime2 + sleepTime3, totalElapsed, 1e-2);
      
      timer.reset();
      lapElapsed = timer.lapElapsed();
      totalElapsed = timer.totalElapsed();
      averageLap = timer.averageLap();
      assertEquals("lapElapsed incorrect", 0.0, lapElapsed, 1e-2);
      assertEquals("totalElapsed incorrect", 0.0, totalElapsed, 1e-2);
      assertEquals("averageLap incorrect", Double.NaN, averageLap, 1e-2);
   }
   
   public static void main(String[] args)
   {
      MutationTestingTools.doPITMutationTestAndOpenResult(TimerTest.class);
   }
}
