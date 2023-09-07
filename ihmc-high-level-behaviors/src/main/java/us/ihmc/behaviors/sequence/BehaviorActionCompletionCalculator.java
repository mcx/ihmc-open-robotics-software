package us.ihmc.behaviors.sequence;

import us.ihmc.euclid.referenceFrame.interfaces.FramePose3DReadOnly;
import us.ihmc.tools.Timer;

/**
 * This class will probably change a lot as actions have
 * more complex conditions and decision making logic. It'll
 * morph into something else entirely, probably.
 */
public class BehaviorActionCompletionCalculator
{
   private double translationError;
   private double rotationError;
   private boolean desiredTranslationAchieved;
   private boolean desiredRotationAchieved;
   private boolean desiredPoseAchieved;

   public boolean isComplete(FramePose3DReadOnly desired,
                             FramePose3DReadOnly actual,
                             double translationTolerance,
                             double rotationTolerance,
                             double actionNominalDuration,
                             Timer executionTimer)
   {
      boolean timeIsUp = !executionTimer.isRunning(actionNominalDuration);

      translationError = actual.getTranslation().differenceNorm(desired.getTranslation());
      desiredTranslationAchieved = translationError <= translationTolerance;

      rotationError = actual.getRotation().distance(desired.getRotation(), true);
      desiredRotationAchieved = rotationError <= rotationTolerance;

      desiredPoseAchieved = desiredTranslationAchieved && desiredRotationAchieved;

      return timeIsUp && desiredPoseAchieved;
   }

   public boolean isComplete(FramePose3DReadOnly desired,
                             FramePose3DReadOnly actual,
                             double translationTolerance,
                             double actionNominalDuration,
                             Timer executionTimer)
   {
      boolean timeIsUp = !executionTimer.isRunning(actionNominalDuration);

      translationError = actual.getTranslation().differenceNorm(desired.getTranslation());
      desiredTranslationAchieved = translationError <= translationTolerance;

      return timeIsUp && desiredTranslationAchieved;
   }

   public double getTranslationError()
   {
      return translationError;
   }

   public double getRotationError()
   {
      return rotationError;
   }
}
