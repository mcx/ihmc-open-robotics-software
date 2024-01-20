package us.ihmc.perception.steppableRegions;

import us.ihmc.tools.property.*;

/**
 * The JSON file for this property set is located here:
 * ihmc-perception/src/main/resources/us/ihmc/perception/steppableRegions/SteppableRegionCalculatorParameters.json
 *
 * This class was auto generated. Property attributes must be edited in the JSON file,
 * after which this class should be regenerated by running the main. This class uses
 * the generator to assist in the addition, removal, and modification of property keys.
 * It is permissible to forgo these benefits and abandon the generator, in which case
 * you should also move it from the generated-java folder to the java folder.
 *
 * If the constant paths have changed, change them in this file and run the main to regenerate.
 */
public class SteppableRegionCalculatorParameters extends StoredPropertySet implements SteppableRegionCalculatorParametersBasics
{
   public static final StoredPropertyKeyList keys = new StoredPropertyKeyList();

   public static final DoubleStoredPropertyKey distanceFromCliffBottoms = keys.addDoubleKey("Distance from cliff bottoms");
   public static final DoubleStoredPropertyKey distanceFromCliffTops = keys.addDoubleKey("Distance from cliff tops");
   public static final IntegerStoredPropertyKey yawDiscretizations = keys.addIntegerKey("Yaw discretizations");
   public static final DoubleStoredPropertyKey footWidth = keys.addDoubleKey("Foot width");
   public static final DoubleStoredPropertyKey footLength = keys.addDoubleKey("Foot length");
   public static final DoubleStoredPropertyKey cliffStartHeightToAvoid = keys.addDoubleKey("Cliff start height to avoid");
   public static final DoubleStoredPropertyKey cliffEndHeightToAvoid = keys.addDoubleKey("Cliff end height to avoid");
   public static final DoubleStoredPropertyKey minSupportAreaFraction = keys.addDoubleKey("Min support area fraction");
   public static final DoubleStoredPropertyKey minSnapHeightThreshold = keys.addDoubleKey("Min snap height threshold");
   public static final DoubleStoredPropertyKey snapHeightThresholdAtSearchEdge = keys.addDoubleKey("Snap height threshold at search edge");
   public static final DoubleStoredPropertyKey inequalityActivationSlope = keys.addDoubleKey("Inequality activation slope");
   public static final IntegerStoredPropertyKey maxSearchDepthForRegions = keys.addIntegerKey("Max search depth for regions");
   public static final DoubleStoredPropertyKey fractionOfCellToExpandSmallRegions = keys.addDoubleKey("Fraction of cell to expand small regions");
   public static final IntegerStoredPropertyKey maxInteriorPointsToInclude = keys.addIntegerKey("Max interior points to include");
   public static final IntegerStoredPropertyKey minCellsInARegion = keys.addIntegerKey("Min cells in a region");
   public static final DoubleStoredPropertyKey edgeLengthThreshold = keys.addDoubleKey("Edge length threshold");

   /**
    * Loads this property set.
    */
   public SteppableRegionCalculatorParameters()
   {
      this("");
   }

   /**
    * Loads an alternate version of this property set in the same folder.
    */
   public SteppableRegionCalculatorParameters(String versionSuffix)
   {
      this(SteppableRegionCalculatorParameters.class, versionSuffix);
   }

   /**
    * Loads an alternate version of this property set in other folders.
    */
   public SteppableRegionCalculatorParameters(Class<?> classForLoading, String versionSuffix)
   {
      super(keys, classForLoading, SteppableRegionCalculatorParameters.class, versionSuffix);
      load();
   }

   public SteppableRegionCalculatorParameters(StoredPropertySetReadOnly other)
   {
      super(keys, SteppableRegionCalculatorParameters.class, other.getCurrentVersionSuffix());
      set(other);
   }

   public static void main(String[] args)
   {
      StoredPropertySet parameters = new StoredPropertySet(keys, SteppableRegionCalculatorParameters.class);
      parameters.generateJavaFiles();
   }
}
