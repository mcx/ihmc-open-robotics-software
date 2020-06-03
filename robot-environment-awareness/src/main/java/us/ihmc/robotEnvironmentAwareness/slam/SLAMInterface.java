package us.ihmc.robotEnvironmentAwareness.slam;

import controller_msgs.msg.dds.StereoVisionPointCloudMessage;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.transform.interfaces.RigidBodyTransformReadOnly;
import us.ihmc.robotics.geometry.PlanarRegionsList;

public interface SLAMInterface
{
   abstract void addKeyFrame(StereoVisionPointCloudMessage pointCloudMessage);

   abstract boolean addFrame(StereoVisionPointCloudMessage pointCloudMessage);

   abstract void clear();

<<<<<<< HEAD
   abstract List<RigidBodyTransformReadOnly> getSensorPoses();
=======
   abstract PlanarRegionsList getPlanarRegionsMap();
>>>>>>> c12a8c7feaf... Tested surfel icp.

   /**
    * if this frame is detected as a key frame, return new RigidBodyTransform(); if this frame needs
    * drift correction, return optimized transform; if this frame should not be mergeable, return null;
    */
   default RigidBodyTransformReadOnly computeFrameCorrectionTransformer(SLAMFrame frame)
   {
      return new RigidBodyTransform();
   }
}
