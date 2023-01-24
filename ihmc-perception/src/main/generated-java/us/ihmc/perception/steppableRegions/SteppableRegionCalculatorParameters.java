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
   public static final String DIRECTORY_NAME_TO_ASSUME_PRESENT = "ihmc-open-robotics-software";
   public static final String SUBSEQUENT_PATH_TO_RESOURCE_FOLDER = "ihmc-perception/src/main/resources";
   public static final String SUBSEQUENT_PATH_TO_JAVA_FOLDER = "ihmc-perception/src/main/generated-java";

   public static final StoredPropertyKeyList keys = new StoredPropertyKeyList();

   public static final DoubleStoredPropertyKey distanceFromCliffBottoms = keys.addDoubleKey("Distance from cliff bottoms");
   public static final DoubleStoredPropertyKey distanceFromCliffTops = keys.addDoubleKey("Distance from cliff tops");
   public static final IntegerStoredPropertyKey yawDiscretizations = keys.addIntegerKey("Yaw discretizations");
   public static final DoubleStoredPropertyKey footWidth = keys.addDoubleKey("Foot width");
   public static final DoubleStoredPropertyKey footLength = keys.addDoubleKey("Foot length");
   public static final DoubleStoredPropertyKey cliffStartHeightToAvoid = keys.addDoubleKey("Cliff start height to avoid");
   public static final DoubleStoredPropertyKey cliffEndHeightToAvoid = keys.addDoubleKey("Cliff end height to avoid");

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
   public SteppableRegionCalculatorParameters(String versionSpecifier)
   {
      this(SteppableRegionCalculatorParameters.class, DIRECTORY_NAME_TO_ASSUME_PRESENT, SUBSEQUENT_PATH_TO_RESOURCE_FOLDER, versionSpecifier);
   }

   /**
    * Loads an alternate version of this property set in other folders.
    */
   public SteppableRegionCalculatorParameters(Class<?> classForLoading, String directoryNameToAssumePresent, String subsequentPathToResourceFolder, String versionSuffix)
   {
      super(keys, classForLoading, SteppableRegionCalculatorParameters.class, directoryNameToAssumePresent, subsequentPathToResourceFolder, versionSuffix);
      load();
   }

   public SteppableRegionCalculatorParameters(StoredPropertySetReadOnly other)
   {
      super(keys, SteppableRegionCalculatorParameters.class, DIRECTORY_NAME_TO_ASSUME_PRESENT, SUBSEQUENT_PATH_TO_RESOURCE_FOLDER, other.getCurrentVersionSuffix());
      set(other);
   }

   public static void main(String[] args)
   {
      StoredPropertySet parameters = new StoredPropertySet(keys,
                                                           SteppableRegionCalculatorParameters.class,
                                                           DIRECTORY_NAME_TO_ASSUME_PRESENT,
                                                           SUBSEQUENT_PATH_TO_RESOURCE_FOLDER);
      parameters.generateJavaFiles(SUBSEQUENT_PATH_TO_JAVA_FOLDER);
   }
}
