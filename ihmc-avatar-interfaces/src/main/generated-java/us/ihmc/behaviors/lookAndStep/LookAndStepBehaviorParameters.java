package us.ihmc.behaviors.lookAndStep;

import us.ihmc.tools.property.*;

/**
 * The JSON file for this property set is located here:
 * ihmc-avatar-interfaces/src/main/resources/us/ihmc/behaviors/lookAndStep/LookAndStepBehaviorParameters.json
 *
 * This class was auto generated. Property attributes must be edited in the JSON file,
 * after which this class should be regenerated by running the main. This class uses
 * the generator to assist in the addition, removal, and modification of property keys.
 * It is permissible to forgo these benefits and abandon the generator, in which case
 * you should also move it from the generated-java folder to the java folder.
 *
 * If the constant paths have changed, change them in this file and run the main to regenerate.
 */
public class LookAndStepBehaviorParameters extends StoredPropertySet implements LookAndStepBehaviorParametersBasics
{
   public static final StoredPropertyKeyList keys = new StoredPropertyKeyList();

   /**
    * If true, when look and step is initiated, it is given some support regions under
    * it's feet, assuming it is currently supported. This helps the robot take its
    * first steps when the sensor can't see under the feet very well.
    */
   public static final BooleanStoredPropertyKey useInitialSupportRegions = keys.addBooleanKey("Use initial support regions");
   /**
    * If true, the footstep planning algorithm will assume flat ground and walk
    * regardless of sensor input, staying on the same plane and simply generating
    * footsteps on that plane. Only use if the robot has plenty of space and the
    * ground is very flat along the path you're taking.
    */
   public static final BooleanStoredPropertyKey assumeFlatGround = keys.addBooleanKey("Assume flat ground");
   /**
    * If true, there will be virtual (not sensor based) flat regions introduced when
    * the sensor generated planar regions seem locally flat. This parameter is
    * included to help the robot turn in place and take tight corners when the sensor
    * field of view doesn't include the feet, but also because the feet are going to
    * always prevent clean flat regions beneath you. This is an option because it can
    * be a little risky depending on the environment.
    */
   public static final BooleanStoredPropertyKey detectFlatGround = keys.addBooleanKey("Detect flat ground");
   /**
    * How strict to be about the detect flat ground parameter.
    */
   public static final DoubleStoredPropertyKey detectFlatGroundZTolerance = keys.addDoubleKey("Detect flat ground z tolerance");
   /**
    * How strict to be about the detect flat ground parameter.
    */
   public static final DoubleStoredPropertyKey detectFlatGroundOrientationTolerance = keys.addDoubleKey("Detect flat ground orientation tolerance");
   /**
    * How strict to be about the detect flat ground parameter.
    */
   public static final DoubleStoredPropertyKey detectFlatGroundMinRegionAreaToConsider = keys.addDoubleKey("Detect flat ground min region area to consider");
   /**
    * How strict to be about the detect flat ground parameter.
    */
   public static final DoubleStoredPropertyKey detectFlatGroundMinRadius = keys.addDoubleKey("Detect flat ground min radius");
   /**
    * The max size of the generated circle when assuming flat ground. A big circle can
    * help the robot keep moving without pauses by allowing 2-3 step ahead plans.
    */
   public static final DoubleStoredPropertyKey assumedFlatGroundCircleRadius = keys.addDoubleKey("Assumed flat ground circle radius");
   /**
    * When true, the robot takes an extra step to square up it's feet once it's
    * reached the goal.
    */
   public static final BooleanStoredPropertyKey squareUpAtTheEnd = keys.addBooleanKey("Square up at the end");
   /**
    * This is a scalar of the foot support polygons, used in the "Use initial support
    * regions" setting. Should be greater than 1. For example a value of 3 will give
    * the robot two big foot shaped regions, where it's feet are, that are scaled up
    * 3x, to assist with initial steps when the sensor can't see that area or the feet
    * are blocking it.
    */
   public static final DoubleStoredPropertyKey supportRegionScaleFactor = keys.addDoubleKey("Support region scale factor");
   /**
    * When set to 1, look and step will simply be using the latest set of planar
    * regions available. When set to n = 2+, the past n sets of planar regions from
    * successive scans of the environment will be merged together using the
    * PlanarRegionSLAM algorithm and the resulting map will be used for footstep
    * planning. A value of 0 means ignoring all planar regions from the sensor.
    */
   public static final IntegerStoredPropertyKey planarRegionsHistorySize = keys.addIntegerKey("Planar regions history size");
   /**
    * The realtime walking controller has a footstep queue that it processes. Users
    * can override or queue additional footsteps. The parameter decides how many
    * footsteps to send to the controller at once. Currently it always overrides all
    * the footsteps every plan and this parameters determines the maximum that it will
    * send each time. Adding more steps can help the walking controller plan ahead for
    * balance. Sometimes the footstepl planner plans less than this amount. Needs to
    * be at least 1. 3 is usually a reasonable number.
    */
   public static final IntegerStoredPropertyKey maxStepsToSendToController = keys.addIntegerKey("Max steps to send to controller");
   /**
    * When true, basically circumvents any body path planning and sets a path straight
    * to the goal.
    */
   public static final BooleanStoredPropertyKey flatGroundBodyPathPlan = keys.addBooleanKey("Flat ground body path plan");
   /**
    * (Exprimental) Use height map based body path planning algorithm.
    */
   public static final BooleanStoredPropertyKey heightMapBodyPathPlan = keys.addBooleanKey("Height map body path plan");
   /**
    * Swing planner enum ordinal. See SwingPlannerType.
    */
   public static final IntegerStoredPropertyKey swingPlannerType = keys.addIntegerKey("Swing planner type");
   /**
    * Prevent the robot taking unecessary steps in place.
    */
   public static final DoubleStoredPropertyKey minimumStepTranslation = keys.addDoubleKey("Minimum step translation");
   /**
    * Prevent the robot taking unecessary steps in place.
    */
   public static final DoubleStoredPropertyKey minimumStepOrientation = keys.addDoubleKey("Minimum step orientation");
   /**
    * When the sensor for body path planning is mounted on the head and there's a neck
    * pitch, make sure it's at that pitch for body path planning.
    */
   public static final DoubleStoredPropertyKey neckPitchForBodyPath = keys.addDoubleKey("Neck pitch for body path");
   /**
    * Tolerance for neck pitch so it's not stuck going up and down trying to correct
    * itself. It doesn't need to be that accurate.
    */
   public static final DoubleStoredPropertyKey neckPitchTolerance = keys.addDoubleKey("Neck pitch tolerance");
   /**
    * Decides the point during swing in which we start planning the next step. We want
    * to do this later for reactivity, but sooner to give the planner enough time to
    * complete before touchdown.
    */
   public static final DoubleStoredPropertyKey percentSwingToWait = keys.addDoubleKey("Percent swing to wait");
   /**
    * Step swing duration.
    */
   public static final DoubleStoredPropertyKey swingDuration = keys.addDoubleKey("Swing duration");
   /**
    * Double support transfer duration.
    */
   public static final DoubleStoredPropertyKey transferDuration = keys.addDoubleKey("Transfer duration");
   /**
    * The amount of time a reset of the whole behavior takes before becoming active
    * again. You don't want to do this too fast because for safety, you want to wait
    * for things to settle down.
    */
   public static final DoubleStoredPropertyKey resetDuration = keys.addDoubleKey("Reset duration");
   /**
    * How close to the goal suffices to conclude the behavior.
    */
   public static final DoubleStoredPropertyKey goalSatisfactionRadius = keys.addDoubleKey("Goal satisfaction radius");
   /**
    * Facing the same yaw as the goal tolerance for what suffices to conclude the
    * behavior.
    */
   public static final DoubleStoredPropertyKey goalSatisfactionOrientationDelta = keys.addDoubleKey("Goal satisfaction orientation delta");
   /**
    * Only try to plan steps out this far on each footstep plan. Useful if your sensor
    * is long range and you don't want to waste time planning far out.
    */
   public static final DoubleStoredPropertyKey planHorizon = keys.addDoubleKey("Plan horizon");
   /**
    * Let the planner go for longer in tricky situations (to the planner).
    */
   public static final DoubleStoredPropertyKey footstepPlannerTimeoutWhileStopped = keys.addDoubleKey("Footstep planner timeout while stopped");
   /**
    * Expiration so we don't use data that's old because we haven't received new data
    * in a while.
    */
   public static final DoubleStoredPropertyKey planarRegionsExpiration = keys.addDoubleKey("Planar regions expiration");
   /**
    * Expiration so we don't use data that's old because we haven't received new data
    * in a while.
    */
   public static final DoubleStoredPropertyKey heightMapExpiration = keys.addDoubleKey("Height map expiration");
   /**
    * We want to wait a little after footstep planning fails so we can get new sensor
    * data, not free spin, let things settle down, not generate too many failure logs,
    * etc.
    */
   public static final DoubleStoredPropertyKey waitTimeAfterPlanFailed = keys.addDoubleKey("Wait time after plan failed");
   /**
    * We allow the footstep planner to terminate early if it plans this many steps.
    * Take care that this number is as high or higher than "Max steps to send to
    * controller".
    */
   public static final IntegerStoredPropertyKey numberOfStepsToTryToPlan = keys.addIntegerKey("Number of steps to try to plan");
   /**
    * Expiration so we don't use data that's old because we haven't received new data
    * in a while.
    */
   public static final DoubleStoredPropertyKey robotConfigurationDataExpiration = keys.addDoubleKey("Robot configuration data expiration");
   /**
    * How many steps as tracked by the walking status tracker are allowed to be
    * incomplete in order to plan again. This is usually 1 for the currently swinging
    * step. Not sure if it makes sense to set this higher. Setting this to 0 would
    * force the robot to pause every step.
    */
   public static final IntegerStoredPropertyKey acceptableIncompleteFootsteps = keys.addIntegerKey("Acceptable incomplete footsteps");
   /**
    * When "Stop for impassibilities" is set to true, how far away from the obstacle
    * to stop and wait.
    */
   public static final DoubleStoredPropertyKey horizonFromDebrisToStop = keys.addDoubleKey("Horizon from debris to stop");
   /**
    * If true, look and step will accept obstacle bounding boxes and stop short of
    * them if they are in the way, reporting to the operator that it's reached and
    * impassibility.
    */
   public static final BooleanStoredPropertyKey stopForImpassibilities = keys.addBooleanKey("Stop for impassibilities");

   /**
    * Loads this property set.
    */
   public LookAndStepBehaviorParameters()
   {
      this("");
   }

   /**
    * Loads an alternate version of this property set in the same folder.
    */
   public LookAndStepBehaviorParameters(String versionSuffix)
   {
      this(LookAndStepBehaviorParameters.class, versionSuffix);
   }

   /**
    * Loads an alternate version of this property set in other folders.
    */
   public LookAndStepBehaviorParameters(Class<?> classForLoading, String versionSuffix)
   {
      super(keys, classForLoading, LookAndStepBehaviorParameters.class, versionSuffix);
      load();
   }

   public LookAndStepBehaviorParameters(StoredPropertySetReadOnly other)
   {
      super(keys, LookAndStepBehaviorParameters.class, other.getCurrentVersionSuffix());
      set(other);
   }

   public static void main(String[] args)
   {
      StoredPropertySet parameters = new StoredPropertySet(keys, LookAndStepBehaviorParameters.class);
      parameters.generateJavaFiles();
   }
}
