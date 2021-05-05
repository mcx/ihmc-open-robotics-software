package us.ihmc.commonWalkingControlModules.modelPredictiveController.commands;

import org.ejml.data.DMatrixRMaj;
import us.ihmc.commonWalkingControlModules.controllerCore.command.ConstraintType;

/**
 * This command is designed to be a really efficient command setter to allow directly setting the orientation value
 * at the beginning of the segment indicated by {@link #segmentNumber}.
 */
public class DirectOrientationValueCommand implements MPCCommand<DirectOrientationValueCommand>
{
   private int commandId = -1;
   private int segmentNumber = -1;

   private ConstraintType constraintType = ConstraintType.OBJECTIVE;

   private final DMatrixRMaj objectiveValue = new DMatrixRMaj(6, 1);

   private double objectiveWeight;

   public void setCommandId(int commandId)
   {
      this.commandId = commandId;
   }
   public int getCommandId()
   {
      return commandId;
   }

   public MPCCommandType getCommandType()
   {
      return MPCCommandType.DIRECT_ORIENTATION_VALUE;
   }

   public void reset()
   {
      setSegmentNumber(-1);
      getObjectiveValue().zero();
   }

   public int getSegmentNumber()
   {
      return segmentNumber;
   }

   public void setSegmentNumber(int segmentNumber)
   {
      this.segmentNumber = segmentNumber;
   }

   public ConstraintType getConstraintType()
   {
      return constraintType;
   }

   public void setConstraintType(ConstraintType constraintType)
   {
      this.constraintType = constraintType;
   }

   public DMatrixRMaj getObjectiveValue()
   {
      return objectiveValue;
   }

   public void setObjectiveValue(DMatrixRMaj objectiveValue)
   {
      this.objectiveValue.set(objectiveValue);
   }

   public void setObjectiveWeight(double objectiveWeight)
   {
      this.objectiveWeight = objectiveWeight;
   }

   public double getObjectiveWeight()
   {
      return objectiveWeight;
   }

   public void set(DirectOrientationValueCommand other)
   {
      setCommandId(other.getCommandId());
      setSegmentNumber(other.getSegmentNumber());
      setObjectiveValue(other.getObjectiveValue());
   }
}