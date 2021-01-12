package us.ihmc.commonWalkingControlModules.dynamicPlanning.comPlanning;

import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FrameVector3DReadOnly;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.robotics.time.TimeIntervalProvider;

import java.util.List;

/**
 * Provides the contact state that constitutes the contact sequence used by the {@link CoMTrajectoryPlannerInterface}. This includes
 * the starting and ending eCMP position, the time interval, and the contact state {@link ContactState}.
 */
public interface ContactStateProvider extends TimeIntervalProvider
{
   /**
    * Provides the starting eCMP position for the current contact state.
    */
   FramePoint3DReadOnly getECMPStartPosition();

   /**
    * Provides the starting eCMP position for the current contact state.
    */
   FramePoint3DReadOnly getECMPEndPosition();

   /**
    * Provides the starting eCMP velocity for the current contact state.
    */
   FrameVector3DReadOnly getECMPStartVelocity();

   /**
    * Provides the ending eCMP velocity for the current contact state.
    */
   FrameVector3DReadOnly getECMPEndVelocity();

   /**
    * Specifies whether the current state is in contact or not.
    */
   ContactState getContactState();

   List<String> getBodiesInContact();

   default int getNumberOfBodiesInContact()
   {
      return getBodiesInContact().size();
   }

   default Vector3DReadOnly getSurfaceNormal()
   {
      Vector3D vector3D = new Vector3D();
      vector3D.setToNaN();
      return vector3D;
   }
}
