package us.ihmc.atlas.behaviors;

import us.ihmc.atlas.AtlasRobotModel;
import us.ihmc.atlas.AtlasRobotVersion;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.humanoidBehaviors.ui.BehaviorUI;
import us.ihmc.humanoidBehaviors.ui.BehaviorUIRegistry;
import us.ihmc.humanoidBehaviors.ui.behaviors.LookAndStepBehaviorUI;

public class AtlasLookAndStepBehaviorUIAndModule
{
   public AtlasLookAndStepBehaviorUIAndModule()
   {
      ThreadTools.startAThread(() -> new AtlasBehaviorModule(), AtlasLookAndStepBehaviorUIAndModule.class.getSimpleName());

      DRCRobotModel drcRobotModel = new AtlasRobotModel(AtlasRobotVersion.ATLAS_UNPLUGGED_V5_NO_HANDS, RobotTarget.REAL_ROBOT, false);

      BehaviorUI.createInterprocess(BehaviorUIRegistry.of(LookAndStepBehaviorUI.DEFINITION), drcRobotModel, "localhost");
   }

   public static void main(String[] args)
   {
      new AtlasLookAndStepBehaviorUIAndModule();
   }
}
