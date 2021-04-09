package us.ihmc.commonWalkingControlModules.modelPredictiveController.ioHandling;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import us.ihmc.commonWalkingControlModules.modelPredictiveController.commands.*;
import us.ihmc.commonWalkingControlModules.modelPredictiveController.core.LinearMPCIndexHandler;
import us.ihmc.commonWalkingControlModules.modelPredictiveController.core.LinearMPCQPSolver;
import us.ihmc.commonWalkingControlModules.modelPredictiveController.core.MPCQPInputCalculator;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.QPInputTypeA;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.QPInputTypeC;
import us.ihmc.commons.MathTools;
import us.ihmc.log.LogTools;

/**
 * This class is meant to compute the cost-to-gos for the individual MPC commands submitted to the core, using the solution to the MPC problem.
 */
public class LinearMPCSolutionInspection
{
   protected static final double epsilon = 1e-3;
   private final LinearMPCIndexHandler indexHandler;
   private final MPCQPInputCalculator inputCalculator;
   protected final QPInputTypeA qpInputTypeA = new QPInputTypeA(0);
   protected final QPInputTypeC qpInputTypeC = new QPInputTypeC(0);

   public LinearMPCSolutionInspection(LinearMPCIndexHandler indexHandler, double gravityZ)
   {
      this.indexHandler = indexHandler;
      inputCalculator = new MPCQPInputCalculator(indexHandler, gravityZ);
   }

   public void inspectSolution(MPCCommandList commandList, DMatrixRMaj solution)
   {
      qpInputTypeA.setNumberOfVariables(indexHandler.getTotalProblemSize());
      qpInputTypeC.setNumberOfVariables(indexHandler.getTotalProblemSize());

      for (int i = 0; i < commandList.getNumberOfCommands(); i++)
      {
         MPCCommand<?> command = commandList.getCommand(i);

         switch (command.getCommandType())
         {
            case VALUE:
               inspectMPCValueObjective((MPCValueCommand) command, solution);
               break;
            case CONTINUITY:
               inspectCoMContinuityObjective((MPCContinuityCommand) command, solution);
               break;
            case LIST:
               inspectSolution((MPCCommandList) command, solution);
               break;
            case RHO_VALUE:
               inspectRhoValueCommand((RhoObjectiveCommand) command, solution);
               break;
            case VRP_TRACKING:
               inspectVRPTrackingObjective((VRPTrackingCommand) command, solution);
               break;
            default:
               throw new RuntimeException("The command type: " + command.getCommandType() + " is not handled.");
         }
      }
   }

   public void inspectRhoValueCommand(RhoObjectiveCommand command, DMatrixRMaj solution)
   {
      int offset = inputCalculator.calculateRhoValueCommand(qpInputTypeA, command);
      if (offset != -1)
         inspectInput(qpInputTypeA, solution);
   }

   public void inspectMPCValueObjective(MPCValueCommand command, DMatrixRMaj solution)
   {
      int offset = inputCalculator.calculateValueObjective(qpInputTypeA, command);
      if (offset != -1)
         command.setCostToGo(inspectInput(qpInputTypeA, solution));
   }

   public void inspectCoMContinuityObjective(MPCContinuityCommand command, DMatrixRMaj solution)
   {
      int offset = inputCalculator.calculateCoMContinuityObjective(qpInputTypeA, command);
      if (offset != -1)
         inspectInput(qpInputTypeA, solution);
   }

   public void inspectVRPTrackingObjective(VRPTrackingCommand command, DMatrixRMaj solution)
   {
      int offset = inputCalculator.calculateVRPTrackingObjective(qpInputTypeC, command);
      if (offset != -1)
         command.setCostToGo(inspectInput(qpInputTypeC, solution));
   }

   public double inspectInput(QPInputTypeA input, DMatrixRMaj solution)
   {
      switch (input.getConstraintType())
      {
         case OBJECTIVE:
            if (input.useWeightScalar())
               return inspectObjective(input.taskJacobian, input.taskObjective, input.getWeightScalar(), solution);
            else
               throw new IllegalArgumentException("Not yet implemented.");
         case EQUALITY:
            inspectEqualityConstraint(input.taskJacobian, input.taskObjective, solution);
            break;
         case LEQ_INEQUALITY:
            inspectMotionLesserOrEqualInequalityConstraint(input.taskJacobian, input.taskObjective, solution);
            break;
         case GEQ_INEQUALITY:
            inspectMotionGreaterOrEqualInequalityConstraint(input.taskJacobian, input.taskObjective, solution);
            break;
         default:
            throw new RuntimeException("Unexpected constraint type: " + input.getConstraintType());
      }

      return 0.0;
   }

   public double inspectInput(QPInputTypeC input, DMatrixRMaj solution)
   {
      int problemSize = indexHandler.getTotalProblemSize();

      Hx.reshape(problemSize, 1);

      CommonOps_DDRM.mult(input.getDirectCostHessian(), solution, Hx);
      CommonOps_DDRM.multTransA(solution, Hx, cost);
      CommonOps_DDRM.multAddTransA(input.getDirectCostGradient(), solution, cost);
      CommonOps_DDRM.scale(input.getWeightScalar(), cost);

      return cost.get(0, 0);
   }

   private final DMatrixRMaj solverInput_H = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj solverInput_f = new DMatrixRMaj(0, 0);

   private final DMatrixRMaj Hx = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj cost = new DMatrixRMaj(1, 1);

   private double inspectObjective(DMatrixRMaj taskJacobian, DMatrixRMaj taskObjective, double taskWeight, DMatrixRMaj solution)
   {
      int problemSize = indexHandler.getTotalProblemSize();

      solverInput_H.reshape(problemSize, problemSize);
      solverInput_f.reshape(problemSize, 1);
      Hx.reshape(problemSize, 1);

      solverInput_H.zero();
      solverInput_f.zero();

      LinearMPCQPSolver.addObjective(taskJacobian, taskObjective, taskWeight, problemSize, 0, solverInput_H, solverInput_f);

      CommonOps_DDRM.mult(solverInput_H, solution, Hx);
      CommonOps_DDRM.multTransA(solution, Hx, cost);
      CommonOps_DDRM.multAddTransA(solverInput_f, solution, cost);

      return cost.get(0, 0);
   }

   private final DMatrixRMaj solverInput_Aeq = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj solverInput_beq = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj solverOutput_beq = new DMatrixRMaj(0, 0);

   public void inspectEqualityConstraint(DMatrixRMaj taskJacobian, DMatrixRMaj taskObjective, DMatrixRMaj solution)
   {
      int problemSize = indexHandler.getTotalProblemSize();
      int constraints = taskJacobian.getNumRows();

      solverInput_Aeq.zero();
      solverInput_beq.zero();

      solverInput_Aeq.reshape(0, problemSize);
      solverInput_beq.reshape(0, 1);

      LinearMPCQPSolver.addEqualityConstraint(taskJacobian, taskObjective, problemSize, solverInput_Aeq, solverInput_beq);

      solverOutput_beq.reshape(constraints, problemSize);
      solverOutput_beq.zero();

      CommonOps_DDRM.mult(taskJacobian, solution, solverOutput_beq);

      for (int i = 0; i < constraints; i++)
      {
         if (!MathTools.epsilonEquals(solverOutput_beq.get(i, 0), solverInput_beq.get(i, 0), epsilon))
            LogTools.error("Equality constraint wasn't satisfied.");
      }
   }

   private final DMatrixRMaj solverInput_Ain = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj solverInput_bin = new DMatrixRMaj(0, 0);
   private final DMatrixRMaj solverOutput_bin = new DMatrixRMaj(0, 0);

   public void inspectMotionLesserOrEqualInequalityConstraint(DMatrixRMaj taskJacobian, DMatrixRMaj taskObjective, DMatrixRMaj solution)
   {
      int problemSize = indexHandler.getTotalProblemSize();
      int constraints = taskJacobian.getNumRows();

      solverInput_Ain.zero();
      solverInput_bin.zero();

      solverInput_Ain.reshape(0, problemSize);
      solverInput_bin.reshape(0, 1);

      LinearMPCQPSolver.addMotionLesserOrEqualInequalityConstraint(taskJacobian, taskObjective, problemSize, problemSize, 0, solverInput_Ain, solverInput_bin);

      solverOutput_bin.reshape(constraints, problemSize);
      solverOutput_bin.zero();

      CommonOps_DDRM.mult(taskJacobian, solution, solverOutput_bin);

      for (int i = 0; i < constraints; i++)
      {
         if (solverOutput_bin.get(i, 0) > solverInput_bin.get(i, 0) + epsilon)
            throw new RuntimeException("Inequality constraint wasn't satisfied.");
      }
   }

   public void inspectMotionGreaterOrEqualInequalityConstraint(DMatrixRMaj taskJacobian, DMatrixRMaj taskObjective, DMatrixRMaj solution)
   {
      int problemSize = indexHandler.getTotalProblemSize();
      int constraints = taskJacobian.getNumRows();

      solverInput_Ain.reshape(0, problemSize);
      solverInput_bin.reshape(0, 1);

      solverInput_Ain.zero();
      solverInput_bin.zero();

      LinearMPCQPSolver.addMotionGreaterOrEqualInequalityConstraint(taskJacobian, taskObjective, problemSize, problemSize, 0, solverInput_Ain, solverInput_bin);

      solverOutput_bin.reshape(constraints, problemSize);
      solverOutput_bin.zero();

      CommonOps_DDRM.mult(taskJacobian, solution, solverOutput_bin);

      for (int i = 0; i < constraints; i++)
      {
//         if (solverOutput_bin.get(i, 0) < solverInput_bin.get(i, 0) - epsilon)
//            throw new RuntimeException("Inequality constraint wasn't satisfied.");
      }

   }
}