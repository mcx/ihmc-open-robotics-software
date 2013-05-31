package us.ihmc.darpaRoboticsChallenge;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import us.ihmc.SdfLoader.SDFRobot;
import us.ihmc.bambooTools.BambooTools;
import us.ihmc.commonWalkingControlModules.automaticSimulationRunner.AutomaticSimulationRunner;
import us.ihmc.darpaRoboticsChallenge.drcRobot.PlainDRCRobot;
import us.ihmc.darpaRoboticsChallenge.initialSetup.SquaredUpDRCRobotInitialSetup;
import us.ihmc.graphics3DAdapter.GroundProfile;
import us.ihmc.graphics3DAdapter.camera.CameraConfiguration;
import us.ihmc.graphics3DAdapter.graphics.appearances.AppearanceDefinition;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearance;
import us.ihmc.projectM.R2Sim02.initialSetup.RobotInitialSetup;
import us.ihmc.utilities.MemoryTools;
import us.ihmc.utilities.Pair;
import us.ihmc.utilities.ThreadTools;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.SimulationConstructionSet;
import com.yobotics.simulationconstructionset.util.FlatGroundProfile;
import com.yobotics.simulationconstructionset.util.ground.CombinedTerrainObject;
import com.yobotics.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner;
import com.yobotics.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import com.yobotics.simulationconstructionset.util.simulationTesting.NothingChangedVerifier;

public class DRCFlatGroundWalkingTest
{
   private static final boolean ALWAYS_SHOW_GUI = false;
   private static final boolean KEEP_SCS_UP = false;

   private static final boolean CREATE_MOVIE = BambooTools.doMovieCreation();
   private static final boolean checkNothingChanged = BambooTools.getCheckNothingChanged();

   private static final boolean SHOW_GUI = ALWAYS_SHOW_GUI || checkNothingChanged || CREATE_MOVIE;

   private BlockingSimulationRunner blockingSimulationRunner;

   @Before
   public void showMemoryUsageBeforeTest()
   {
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before test.");
   }
   
   @After
   public void destroySimulationAndRecycleMemory()
   {
      if (KEEP_SCS_UP)
      {
         ThreadTools.sleepForever();
      }

      // Do this here in case a test fails. That way the memory will be recycled.
      if (blockingSimulationRunner != null)
      {
         blockingSimulationRunner.destroySimulation();
         blockingSimulationRunner = null;
      }
      
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }


   @Test
   public void testDRCOverShallowRamp() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();


      double standingTimeDuration = 1.0;
      double maximumWalkTime = 30.0;
      double desiredVelocityValue = 1.0;
      double desiredHeadingValue = 0.0;

      boolean useVelocityAndHeadingScript = false;
      boolean cheatWithGroundHeightAtForFootstep = true;

      DRCRobotWalkingControllerParameters drcControlParameters = new DRCRobotWalkingControllerParameters();
//      drcControlParameters.setNominalHeightAboveAnkle(drcControlParameters.nominalHeightAboveAnkle() - 0.03);    // Need to do this or the leg goes straight and the robot falls.

      Pair<CombinedTerrainObject, Double> combinedTerrainObjectAndRampEndX = createRamp();
      CombinedTerrainObject combinedTerrainObject = combinedTerrainObjectAndRampEndX.first();
      double rampEndX = combinedTerrainObjectAndRampEndX.second();
      
      SimulationConstructionSet scs = setupScs(drcControlParameters, combinedTerrainObject, useVelocityAndHeadingScript, cheatWithGroundHeightAtForFootstep);
      scs.setGroundVisible(false);
      scs.addStaticLinkGraphics(combinedTerrainObject.getLinkGraphics());
      
      
      blockingSimulationRunner = new BlockingSimulationRunner(scs, 1000.0);

      NothingChangedVerifier nothingChangedVerifier = null;
      if (checkNothingChanged)
      {
         nothingChangedVerifier = new NothingChangedVerifier("DRCOverShallowRampTest", scs);
      }

      BooleanYoVariable walk = (BooleanYoVariable) scs.getVariable("walk");
      DoubleYoVariable q_x = (DoubleYoVariable) scs.getVariable("q_x");
      DoubleYoVariable desiredSpeed = (DoubleYoVariable) scs.getVariable("desiredVelocityX");
      DoubleYoVariable desiredHeading = (DoubleYoVariable) scs.getVariable("desiredHeading");

//    DoubleYoVariable centerOfMassHeight = (DoubleYoVariable) scs.getVariable("ProcessedSensors.comPositionz");
      DoubleYoVariable comError = (DoubleYoVariable) scs.getVariable("positionError_comHeight");
      DoubleYoVariable leftFootHeight = (DoubleYoVariable) scs.getVariable("p_leftFootPositionZ");
      DoubleYoVariable rightFootHeight = (DoubleYoVariable) scs.getVariable("p_rightFootPositionZ");

      initiateMotion(standingTimeDuration, blockingSimulationRunner, walk);
      desiredSpeed.set(desiredVelocityValue);
      desiredHeading.set(desiredHeadingValue);

//    ThreadTools.sleepForever();

      double timeIncrement = 1.0;
      boolean done = false;
      while (!done)
      {
         blockingSimulationRunner.simulateAndBlock(timeIncrement);

         if (Math.abs(comError.getDoubleValue()) > 0.09)
         {
            fail("comError = " + Math.abs(comError.getDoubleValue()));
         }

         if (scs.getTime() > standingTimeDuration + maximumWalkTime)
            done = true;
         if (q_x.getDoubleValue() > rampEndX)
            done = true;
      }

      if (checkNothingChanged)
         checkNothingChanged(nothingChangedVerifier);

      createMovie(scs);
   }
   
   private Pair<CombinedTerrainObject, Double> createRamp()
   {
      double rampSlopeUp = 0.1;
      double rampSlopeDown = 0.08;

      double rampXStart0 = 0.5;
      double rampXLength0 = 2.0;
      double landingHeight = rampSlopeUp * rampXLength0;
      double landingLength = 1.0;
      double rampXLength1 = landingHeight / rampSlopeDown;

      double rampYStart = -2.0;
      double rampYEnd = 6.0;

      double landingStartX = rampXStart0 + rampXLength0;
      double landingEndX = landingStartX + landingLength;
      double rampEndX = landingEndX + rampXLength1;

      CombinedTerrainObject combinedTerrainObject = new CombinedTerrainObject("JustARamp");

      AppearanceDefinition appearance = YoAppearance.Green();
      combinedTerrainObject.addRamp(rampXStart0, rampYStart, landingStartX, rampYEnd, landingHeight, appearance);
      combinedTerrainObject.addBox(landingStartX, rampYStart, landingEndX, rampYEnd, 0.0, landingHeight, YoAppearance.Gray());
      combinedTerrainObject.addRamp(rampEndX, rampYStart, landingEndX, rampYEnd, landingHeight, appearance);

      combinedTerrainObject.addBox(rampXStart0 - 2.0, rampYStart, rampEndX + 2.0, rampYEnd, -0.05, 0.0);
      
      return new Pair<CombinedTerrainObject, Double>(combinedTerrainObject, rampEndX);
   }

   @Test
   public void testDRCFlatGroundWalking() throws SimulationExceededMaximumTimeException
   {
      BambooTools.reportTestStartedMessage();

      double standingTimeDuration = 1.0;
      double walkingTimeDuration = 90.0;

      boolean useVelocityAndHeadingScript = true;
      boolean cheatWithGroundHeightAtForFootstep = false;

      GroundProfile groundProfile = new FlatGroundProfile();

      DRCRobotWalkingControllerParameters drcControlParameters = new DRCRobotWalkingControllerParameters();
      SimulationConstructionSet scs = setupScs(drcControlParameters, groundProfile, useVelocityAndHeadingScript, cheatWithGroundHeightAtForFootstep);

      NothingChangedVerifier nothingChangedVerifier = null;
      if (checkNothingChanged)
      {
         nothingChangedVerifier = new NothingChangedVerifier("DRCFlatGroundWalkingTest", scs);
         walkingTimeDuration = 7.0;
      }

      blockingSimulationRunner = new BlockingSimulationRunner(scs, 1000.0);

      BooleanYoVariable walk = (BooleanYoVariable) scs.getVariable("walk");

//    DoubleYoVariable desiredHeading = (DoubleYoVariable) scs.getVariable("desiredHeading");
//    DoubleYoVariable pelvisYaw = (DoubleYoVariable) scs.getVariable("q_yaw");
//    DoubleYoVariable centerOfMassHeight = (DoubleYoVariable) scs.getVariable("ProcessedSensors.comPositionz");
      DoubleYoVariable comError = (DoubleYoVariable) scs.getVariable("positionError_comHeight");

      initiateMotion(standingTimeDuration, blockingSimulationRunner, walk);

//    ThreadTools.sleepForever();

      double timeIncrement = 1.0;

      while (scs.getTime() - standingTimeDuration < walkingTimeDuration)
      {
         blockingSimulationRunner.simulateAndBlock(timeIncrement);

         // TODO: Put test for heading back in here.
//       if (!MathTools.epsilonEquals(desiredHeading.getDoubleValue(), pelvisYaw.getDoubleValue(), epsilonHeading))
//       {
//          fail("Desired Heading too large of error: " + desiredHeading.getDoubleValue());
//       }

         if (Math.abs(comError.getDoubleValue()) > 0.02)
         {
            fail("Math.abs(comError.getDoubleValue()) > 0.02: " + comError.getDoubleValue() + " at t = " + scs.getTime());
         }
      }

      if (checkNothingChanged)
         checkNothingChanged(nothingChangedVerifier);

      createMovie(scs);
      BambooTools.reportTestFinishedMessage();

   }

   private void initiateMotion(double standingTimeDuration, BlockingSimulationRunner runner, BooleanYoVariable walk)
           throws SimulationExceededMaximumTimeException
   {
      walk.set(false);
      runner.simulateAndBlock(standingTimeDuration);
      walk.set(true);
   }

   private void createMovie(SimulationConstructionSet scs)
   {
      if (CREATE_MOVIE)
      {
         BambooTools.createMovieAndDataWithDateTimeClassMethodAndShareOnSharedDriveIfAvailable(scs, 1);
      }
   }

   boolean setupForCheatingUsingGroundHeightAtForFootstepProvider = false;

   private SimulationConstructionSet setupScs(DRCRobotWalkingControllerParameters drcControlParameters, GroundProfile groundProfile,
           boolean useVelocityAndHeadingScript, boolean cheatWithGroundHeightAtForFootstep)
   {
      AutomaticSimulationRunner automaticSimulationRunner = null;
      DRCGuiInitialSetup guiInitialSetup = createGUIInitialSetup();

      DRCRobotModel robotModel = DRCRobotModel.getDefaultRobotModel();
      double timePerRecordTick = 0.005;
      int simulationDataBufferSize = 16000;

      RobotInitialSetup<SDFRobot> robotInitialSetup = new SquaredUpDRCRobotInitialSetup(0.0);
      DRCRobotInterface robotInterface = new PlainDRCRobot(robotModel, false);
      DRCSCSInitialSetup scsInitialSetup = new DRCSCSInitialSetup(groundProfile, robotInterface.getSimulateDT());

      if (cheatWithGroundHeightAtForFootstep)
         scsInitialSetup.setInitializeEstimatorToActual(true);

      DRCFlatGroundWalkingTrack drcFlatGroundWalkingTrack = new DRCFlatGroundWalkingTrack(drcControlParameters, robotInterface, robotInitialSetup, guiInitialSetup,
                                                               scsInitialSetup, useVelocityAndHeadingScript, automaticSimulationRunner, timePerRecordTick,
                                                               simulationDataBufferSize, cheatWithGroundHeightAtForFootstep);

      SimulationConstructionSet scs = drcFlatGroundWalkingTrack.getSimulationConstructionSet();

      setupCameraForUnitTest(scs);

      return scs;
   }

   private void checkNothingChanged(NothingChangedVerifier nothingChangedVerifier)
   {
      ArrayList<String> stringsToIgnore = new ArrayList<String>();
      stringsToIgnore.add("nano");
      stringsToIgnore.add("milli");

      boolean writeNewBaseFile = nothingChangedVerifier.getWriteNewBaseFile();

      double maxPercentDifference = 0.001;
      nothingChangedVerifier.verifySameResultsAsPreviously(maxPercentDifference, stringsToIgnore);
      assertFalse("Had to write new base file. On next run nothing should change", writeNewBaseFile);
   }

   private DRCGuiInitialSetup createGUIInitialSetup()
   {
      DRCGuiInitialSetup guiInitialSetup = new DRCGuiInitialSetup(true, false);
      guiInitialSetup.setIsGuiShown(SHOW_GUI);

      return guiInitialSetup;
   }

   protected void setupCameraForUnitTest(SimulationConstructionSet scs)
   {
      CameraConfiguration cameraConfiguration = new CameraConfiguration("testCamera");
      cameraConfiguration.setCameraFix(0.6, 0.4, 1.1);
      cameraConfiguration.setCameraPosition(-0.15, 10.0, 3.0);
      cameraConfiguration.setCameraTracking(true, true, true, false);
      cameraConfiguration.setCameraDolly(true, true, true, false);
      scs.setupCamera(cameraConfiguration);
      scs.selectCamera("testCamera");
   }
}
