package us.ihmc.valkyrie.jfxvisualizer;

import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.humanoidBehaviors.BehaviorRegistry;
import us.ihmc.humanoidBehaviors.ui.BehaviorUI;
import us.ihmc.valkyrie.ValkyrieRobotModel;

/**
 * Not working yet. Placeholder only.
 */
public class ValkyrieBehaviorUI
{
   public ValkyrieBehaviorUI()
   {
      DRCRobotModel drcRobotModel = new ValkyrieRobotModel(RobotTarget.REAL_ROBOT);

      BehaviorUI.createInterprocess(BehaviorRegistry.DEFAULT_BEHAVIORS, drcRobotModel, "localhost");
   }

   public static void main(String[] args)
   {
      new ValkyrieBehaviorUI();
   }
}
