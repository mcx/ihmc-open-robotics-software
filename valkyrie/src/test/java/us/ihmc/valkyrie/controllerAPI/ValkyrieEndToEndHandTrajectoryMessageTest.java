package us.ihmc.valkyrie.controllerAPI;

import static us.ihmc.robotics.Assert.assertEquals;
import static us.ihmc.robotics.Assert.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import controller_msgs.msg.dds.HandTrajectoryMessage;
import controller_msgs.msg.dds.HandWrenchTrajectoryMessage;
import controller_msgs.msg.dds.SE3TrajectoryMessage;
import controller_msgs.msg.dds.WrenchTrajectoryMessage;
import us.ihmc.avatar.controllerAPI.EndToEndHandTrajectoryMessageTest;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.avatar.testTools.scs2.SCS2AvatarTestingSimulationFactory;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.packets.MessageTools;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.humanoidRobotics.communication.packets.HumanoidMessageTools;
import us.ihmc.robotics.math.trajectories.generators.EuclideanTrajectoryPointCalculator;
import us.ihmc.robotics.math.trajectories.trajectorypoints.FrameEuclideanTrajectoryPoint;
import us.ihmc.robotics.math.trajectories.trajectorypoints.lists.FrameEuclideanTrajectoryPointList;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.screwTheory.SelectionMatrix3D;
import us.ihmc.simulationConstructionSetTools.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.valkyrie.ValkyrieRobotModel;

public class ValkyrieEndToEndHandTrajectoryMessageTest extends EndToEndHandTrajectoryMessageTest
{
   private ValkyrieRobotModel robotModel;

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testCustomControlFrame() throws SimulationExceededMaximumTimeException
   {
      super.testCustomControlFrame();
   }

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testMessageWithTooManyTrajectoryPoints() throws Exception
   {
      super.testMessageWithTooManyTrajectoryPoints();
   }

   @Tag("controller-api-2")
   @Override
   @Test
   public void testMultipleTrajectoryPoints() throws Exception
   {
      super.testMultipleTrajectoryPoints();
   }

   @Tag("controller-api-2")
   @Override
   @Test
   public void testQueuedMessages() throws Exception
   {
      super.testQueuedMessages();
   }

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testQueueStoppedWithOverrideMessage() throws Exception
   {
      super.testQueueStoppedWithOverrideMessage();
   }

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testQueueWithWrongPreviousId() throws Exception
   {
      super.testQueueWithWrongPreviousId();
   }

   @Tag("controller-api-2")
   @Override
   @Test
   public void testSingleTrajectoryPoint() throws Exception
   {
      super.testSingleTrajectoryPoint();
   }

   @Tag("controller-api-slow-3")
   @Test
   @Override
   public void testForceExecutionWithSingleTrajectoryPoint() throws Exception
   {
      super.testForceExecutionWithSingleTrajectoryPoint();
   }

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testStopAllTrajectory() throws Exception
   {
      super.testStopAllTrajectory();
   }

   @Tag("controller-api-slow-3")
   @Override
   @Test
   public void testHoldHandWhileWalking() throws SimulationExceededMaximumTimeException
   {
      super.testHoldHandWhileWalking();
   }

   @Tag("controller-api-2")
   @Test
   public void testWrenchTrajectoryMessage() throws Exception
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());

      HeavyBallOnTableEnvironment testEnvironment = new HeavyBallOnTableEnvironment();
      SCS2AvatarTestingSimulationFactory simulationTestHelperFactory = SCS2AvatarTestingSimulationFactory.createDefaultTestSimulationFactory(getRobotModel(),
                                                                                                                                             testEnvironment,
                                                                                                                                             simulationTestingParameters);
      simulationTestHelperFactory.addSecondaryRobot(testEnvironment.getBallRobot());
      simulationTestHelperFactory.setUseImpulseBasedPhysicsEngine(true);
      simulationTestHelper = simulationTestHelperFactory.createAvatarTestingSimulation();
      simulationTestHelper.start();

      ThreadTools.sleep(1000);
      boolean success = simulationTestHelper.simulateNow(2.0);
      assertTrue(success);

      double firstTrajectoryTime = 1.0;
      EuclideanTrajectoryPointCalculator calculator = new EuclideanTrajectoryPointCalculator();
      calculator.appendTrajectoryPoint(0.0, new Point3D(0.25, 0.0, 1.05));
      calculator.appendTrajectoryPoint(0.5, new Point3D(0.4, 0.0, 0.95));
      calculator.appendTrajectoryPoint(0.75, new Point3D(0.5, 0.0, 0.85));
      calculator.appendTrajectoryPoint(1.0, new Point3D(0.6, 0.0, 0.95));
      calculator.appendTrajectoryPoint(1.5, new Point3D(0.6, 0.0, 1.25));
      calculator.appendTrajectoryPoint(2.5, new Point3D(0.25, 0.0, 1.05));
      calculator.compute(2.5);
      FrameEuclideanTrajectoryPointList trajectoryPoints = calculator.getTrajectoryPoints();
      SE3TrajectoryMessage se3TrajectoryMessage = new SE3TrajectoryMessage();
      for (int i = 0; i < trajectoryPoints.getNumberOfTrajectoryPoints(); i++)
      {
         FrameEuclideanTrajectoryPoint trajectoryPoint = trajectoryPoints.getTrajectoryPoint(i);
         double time = trajectoryPoint.getTime() + firstTrajectoryTime;
         Point3DReadOnly position = trajectoryPoint.getPositionCopy();
         Vector3DReadOnly linearVelocity = trajectoryPoint.getLinearVelocityCopy();
         se3TrajectoryMessage.getTaskspaceTrajectoryPoints().add()
                             .set(HumanoidMessageTools.createSE3TrajectoryPointMessage(time, position, new Quaternion(), linearVelocity, new Vector3D()));
      }
      se3TrajectoryMessage.getAngularSelectionMatrix().set(MessageTools.createSelectionMatrix3DMessage(new SelectionMatrix3D(null, false, false, false)));

      WrenchTrajectoryMessage wrenchTrajectoryMessage = new WrenchTrajectoryMessage();
      wrenchTrajectoryMessage.getFrameInformation().setTrajectoryReferenceFrameId(ReferenceFrame.getWorldFrame().hashCode());
      wrenchTrajectoryMessage.getWrenchTrajectoryPoints().add().set(HumanoidMessageTools.createWrenchTrajectoryPointMessage(1.8, null, null));
      wrenchTrajectoryMessage.getWrenchTrajectoryPoints().add()
                             .set(HumanoidMessageTools.createWrenchTrajectoryPointMessage(1.9, null, new Vector3D(150.0, 0.0, 75.0)));
      wrenchTrajectoryMessage.getWrenchTrajectoryPoints().add()
                             .set(HumanoidMessageTools.createWrenchTrajectoryPointMessage(2.2, null, new Vector3D(150.0, 0.0, 75.0)));
      wrenchTrajectoryMessage.getWrenchTrajectoryPoints().add().set(HumanoidMessageTools.createWrenchTrajectoryPointMessage(2.7, null, null));
      wrenchTrajectoryMessage.setUseCustomControlFrame(true);
      wrenchTrajectoryMessage.getControlFramePose().getPosition().set(0.05, -0.11, 0.0);

      RobotSide side = RobotSide.RIGHT;
      HandTrajectoryMessage rightHandTrajectoryMessage = HumanoidMessageTools.createHandTrajectoryMessage(side, se3TrajectoryMessage);
      simulationTestHelper.publishToController(rightHandTrajectoryMessage);

      HandWrenchTrajectoryMessage handWrenchTrajectoryMessage = new HandWrenchTrajectoryMessage();
      handWrenchTrajectoryMessage.setRobotSide(side.toByte());
      handWrenchTrajectoryMessage.getWrenchTrajectory().set(wrenchTrajectoryMessage);
      simulationTestHelper.publishToController(handWrenchTrajectoryMessage);

      success = simulationTestHelper.simulateNow(5.0);
      assertTrue(success);

      assertEquals(testEnvironment.getBallRadius(), testEnvironment.getBallRobotPosition().getZ(), 0.01);
   }

   @Tag("controller-api-2")
   @Override
   @Test
   public void testStreaming() throws Exception
   {
      super.testStreaming();
   }

   @Override
   public ValkyrieRobotModel getRobotModel()
   {
      if (robotModel == null)
      {
         robotModel = new ValkyrieRobotModel(RobotTarget.SCS);
         robotModel.disableOneDoFJointDamping();
      }
      return robotModel;
   }

   @Override
   public String getSimpleRobotName()
   {
      return BambooTools.getSimpleRobotNameFor(BambooTools.SimpleRobotNameKeys.VALKYRIE);
   }

   @Override
   public double getLegLength()
   {
      return robotModel.getRobotPhysicalProperties().getLegLength();
   }
}
