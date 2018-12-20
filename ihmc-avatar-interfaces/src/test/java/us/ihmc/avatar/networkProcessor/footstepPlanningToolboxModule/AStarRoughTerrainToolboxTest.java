package us.ihmc.avatar.networkProcessor.footstepPlanningToolboxModule;

import org.junit.jupiter.api.Test;
import us.ihmc.commons.PrintTools;
import us.ihmc.commons.thread.ThreadTools;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import us.ihmc.footstepPlanning.FootstepPlannerType;
import us.ihmc.pubsub.DomainFactory;

import static us.ihmc.footstepPlanning.testTools.PlannerTestEnvironments.bollards;
import static us.ihmc.footstepPlanning.testTools.PlannerTestEnvironments.corridor;
import static us.ihmc.footstepPlanning.testTools.PlannerTestEnvironments.getTestData;

public class AStarRoughTerrainToolboxTest extends RoughTerrainDataSetTest
{
   @Override
   public FootstepPlannerType getPlannerType()
   {
      return FootstepPlannerType.A_STAR;
   }

   @Override
   @Test// timeout = 1000000
   public void testDownCorridor()
   {
      setCheckForBodyBoxCollision(true);
      super.testDownCorridor();
   }

   @Override
   @Test// timeout = 1000000
   public void testBetweenTwoBollards()
   {
      setCheckForBodyBoxCollision(true);
      setBodyBoxDepth(0.45);
      setBodyBoxWidth(0.9);
      setBodyBoxOffsetX(0.1);
      super.testBetweenTwoBollards();
   }

   public static void main(String[] args) throws Exception
   {
      String testName = corridor;
      AStarRoughTerrainToolboxTest test = new AStarRoughTerrainToolboxTest();
      test.pubSubImplementation = DomainFactory.PubSubImplementation.INTRAPROCESS;
      VISUALIZE = true;
      test.setup();
      test.setCheckForBodyBoxCollision(true);

      if (testName.equals(bollards))
      {
         test.setBodyBoxDepth(0.45);
         test.setBodyBoxWidth(0.9);
         test.setBodyBoxOffsetX(0.1);
      }


      test.runAssertions(getTestData(testName));

      ThreadTools.sleepForever();
      PrintTools.info("Test passed.");
   }
}
