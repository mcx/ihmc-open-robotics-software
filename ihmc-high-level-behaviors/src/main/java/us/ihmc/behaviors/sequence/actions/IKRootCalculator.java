package us.ihmc.behaviors.sequence.actions;

import behavior_msgs.msg.dds.BodyPartPoseStatusMessage;
import us.ihmc.behaviors.sequence.BehaviorActionSequence;
import us.ihmc.communication.IHMCROS2Input;
import us.ihmc.communication.packets.MessageTools;
import us.ihmc.communication.ros2.ROS2PublishSubscribeAPI;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.referenceFrames.ModifiableReferenceFrame;
import us.ihmc.robotics.referenceFrames.ReferenceFrameLibrary;

/**
 * The IKRootCalculator class is responsible for computing and setting the root reference frame
 * for an inverse kinematics solver based on input pose status messages for the chest and pelvis.
 */
public class IKRootCalculator
{
   private final IHMCROS2Input<BodyPartPoseStatusMessage> chestPoseStatusSubscription;
   private final IHMCROS2Input<BodyPartPoseStatusMessage> pelvisPoseStatusSubscription;
   private int previousConcurrentChestIndex = -1;
   private int previousConcurrentPelvisIndex = -1;
   private final FullHumanoidRobotModel syncedRobot;
   private final ReferenceFrameLibrary referenceFrameLibrary;
   private BodyPartPoseStatusMessage chestPoseStatusMessage;
   private BodyPartPoseStatusMessage pelvisPoseStatusMessage;
   private ModifiableReferenceFrame IKChestOnlyReferenceFrame;
   private ModifiableReferenceFrame IKChestAndPelvisReferenceFrame;
   private ReferenceFrame chestFrame;
   private ReferenceFrame pelvisFrame;
   private ReferenceFrame rootFrame;

   public IKRootCalculator(ROS2PublishSubscribeAPI ros2, FullHumanoidRobotModel syncedRobot, ReferenceFrameLibrary referenceFrameLibrary)
   {
      this.syncedRobot = syncedRobot;
      this.referenceFrameLibrary = referenceFrameLibrary;

      chestPoseStatusSubscription = ros2.subscribe(BehaviorActionSequence.CHEST_POSE_STATUS);
      pelvisPoseStatusSubscription = ros2.subscribe(BehaviorActionSequence.PELVIS_POSE_VARIATION_STATUS);
   }

   /**
    * Retrieves the latest kinematics information from the subscribed pose status messages.
    * This method should be called before computing and setting the root reference frame.
    */
   public void getKinematicsInfo()
   {
      if (chestPoseStatusSubscription.getMessageNotification().poll())
         chestPoseStatusMessage = chestPoseStatusSubscription.getLatest();
      else
         chestPoseStatusMessage = null;

      if (pelvisPoseStatusSubscription.getMessageNotification().poll())
         pelvisPoseStatusMessage = pelvisPoseStatusSubscription.getLatest();
      else
         pelvisPoseStatusMessage = null;
   }

   /**
    * Computes and sets the root reference frame for based on chest and pelvis pose status messages.
    */
   public void computeRoot()
   {
      if (chestPoseStatusMessage != null)
      {
         int chestIndex = (int) chestPoseStatusMessage.getActionIndex();
         boolean isChestCurrentAndConcurrent = chestPoseStatusMessage.getCurrentAndConcurrent();
         IKChestOnlyReferenceFrame = new ModifiableReferenceFrame(referenceFrameLibrary.findFrameByName(chestPoseStatusMessage.getParentFrame().getString(0)));
         IKChestOnlyReferenceFrame.update(transformToParent -> MessageTools.toEuclid(chestPoseStatusMessage.getTransformToParent(), transformToParent));
         if (isChestCurrentAndConcurrent)
         {
            chestFrame = IKChestOnlyReferenceFrame.getReferenceFrame();
            previousConcurrentChestIndex = chestIndex;
         }
         else if (chestIndex == previousConcurrentChestIndex)
         {
            chestFrame = null;
         }
      }

      if (pelvisPoseStatusMessage != null)
      {
         int pelvisIndex = (int) pelvisPoseStatusMessage.getActionIndex();
         boolean isPelvisCurrentAndConcurrent = pelvisPoseStatusMessage.getCurrentAndConcurrent();
         FramePose3D pelvisFramePoseVariation = new FramePose3D(referenceFrameLibrary.findFrameByName(pelvisPoseStatusMessage.getParentFrame().getString(0)),
                                                                MessageTools.toEuclid(pelvisPoseStatusMessage.getTransformToParent()));
         if (chestFrame != null)
         {
            IKChestAndPelvisReferenceFrame = new ModifiableReferenceFrame(IKChestOnlyReferenceFrame.getReferenceFrame().getParent());
            IKChestAndPelvisReferenceFrame.update(transformToParent -> copyTransform(IKChestOnlyReferenceFrame.getTransformToParent(), transformToParent));
         }
         else
            IKChestAndPelvisReferenceFrame = new ModifiableReferenceFrame(syncedRobot.getChest()
                                                                                  .getParentJoint()
                                                                                  .getFrameAfterJoint());
         IKChestAndPelvisReferenceFrame.update(transformToParent -> updateIKChestTransform(IKChestAndPelvisReferenceFrame,
                                                                                           pelvisFramePoseVariation,
                                                                                           transformToParent));
         if (isPelvisCurrentAndConcurrent)
         {
            pelvisFrame = IKChestAndPelvisReferenceFrame.getReferenceFrame();
            previousConcurrentPelvisIndex = pelvisIndex;
         }
         else if (pelvisIndex == previousConcurrentPelvisIndex)
         {
            pelvisFrame = null;
         }
      }

      if (pelvisFrame == null)
         rootFrame = chestFrame;
      else
         rootFrame = pelvisFrame;
   }

   public ReferenceFrame getRoot()
   {
      return rootFrame;
   }

   /**
    * Updates the IKChestTransform based on the pelvisFramePoseVariation and the current IKChestFrame.
    *
    * @param IKChestFrame           The reference frame of the IK chest.
    * @param pelvisFramePoseVariation The variation in the pelvis frame's pose.
    * @param IKChestTransform       The transform to update with the new chest pose.
    */
   private void updateIKChestTransform(ModifiableReferenceFrame IKChestFrame, FramePose3D pelvisFramePoseVariation, RigidBodyTransform IKChestTransform)
   {
      if (pelvisFramePoseVariation.getReferenceFrame() != IKChestFrame.getReferenceFrame().getParent())
         pelvisFramePoseVariation.changeFrame(IKChestFrame.getReferenceFrame().getParent());
      IKChestTransform.set(IKChestFrame.getReferenceFrame().getTransformToRoot());
//      pelvisFramePoseVariation.transform(IKChestTransform);
      IKChestTransform.getTranslation().setZ(IKChestTransform.getTranslationZ() + pelvisFramePoseVariation.getTranslationZ());
   }

   private void copyTransform(RigidBodyTransform transformToCopy, RigidBodyTransform otherTransform)
   {
      otherTransform.set(transformToCopy);
   }
}
