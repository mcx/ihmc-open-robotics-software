package us.ihmc.commonWalkingControlModules.trajectories;

import us.ihmc.euclid.referenceFrame.interfaces.FramePoint3DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FrameVector3DReadOnly;
import us.ihmc.robotics.math.trajectories.PositionTrajectoryGenerator;
import us.ihmc.robotics.math.trajectories.trajectorypoints.FrameEuclideanTrajectoryPoint;
import us.ihmc.robotics.trajectories.TrajectoryType;

import java.util.List;

public interface SwingGenerator extends PositionTrajectoryGenerator
{
   boolean doOptimizationUpdate();

   void setStepTime(double stepTime);

   void setInitialConditions(FramePoint3DReadOnly initialPosition, FrameVector3DReadOnly initialVelocity);

   void setFinalConditions(FramePoint3DReadOnly finalPosition, FrameVector3DReadOnly finalVelocity);

   default void setTrajectoryType(TrajectoryType trajectoryType)
   {
      setTrajectoryType(trajectoryType, null);
   }

   void setTrajectoryType(TrajectoryType trajectoryType, List<? extends FramePoint3DReadOnly> waypoints);

   void setStanceFootPosition(FramePoint3DReadOnly stanceFootPosition);

   void setSwingHeight(double swingHeight);

   void setWaypointProportions(double[] waypointProportions);

   int getNumberOfWaypoints();

   void getWaypointData(int waypointIndex, FrameEuclideanTrajectoryPoint waypointDataToPack);
}
