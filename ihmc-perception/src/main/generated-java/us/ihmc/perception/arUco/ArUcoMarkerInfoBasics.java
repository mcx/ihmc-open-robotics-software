package us.ihmc.perception.arUco;

import us.ihmc.tools.property.StoredPropertySetBasics;

/**
 * This class was auto generated. Do not edit by hand. Edit the cooresponding JSON file
 * and run the main in super to regenerate.
 */
public interface ArUcoMarkerInfoBasics extends ArUcoMarkerInfoReadOnly, StoredPropertySetBasics
{
   /**
    * ArUco marker ID in the ArUco dictionary
    */
   default void setMarkerID(double markerID)
   {
      set(ArUcoMarkerInfo.markerID, markerID);
   }

   /**
    * ArUco marker side length size of the black outer part
    */
   default void setMarkerSize(double markerSize)
   {
      set(ArUcoMarkerInfo.markerSize, markerSize);
   }

   /**
    * ArUco marker origin X translation to parent
    */
   default void setMarkerXTranslationToParent(double markerXTranslationToParent)
   {
      set(ArUcoMarkerInfo.markerXTranslationToParent, markerXTranslationToParent);
   }

   /**
    * ArUco marker origin Y translation to parent
    */
   default void setMarkerYTranslationToParent(double markerYTranslationToParent)
   {
      set(ArUcoMarkerInfo.markerYTranslationToParent, markerYTranslationToParent);
   }

   /**
    * ArUco marker origin Z translation to parent
    */
   default void setMarkerZTranslationToParent(double markerZTranslationToParent)
   {
      set(ArUcoMarkerInfo.markerZTranslationToParent, markerZTranslationToParent);
   }

   /**
    * ArUco marker origin yaw rotation to parent
    */
   default void setMarkerYawRotationToParent(double markerYawRotationToParent)
   {
      set(ArUcoMarkerInfo.markerYawRotationToParent, markerYawRotationToParent);
   }

   /**
    * ArUco marker origin pitch rotation to parent
    */
   default void setMarkerPitchRotationToParent(double markerPitchRotationToParent)
   {
      set(ArUcoMarkerInfo.markerPitchRotationToParent, markerPitchRotationToParent);
   }

   /**
    * ArUco marker origin roll rotation to parent
    */
   default void setMarkerRollRotationToParent(double markerRollRotationToParent)
   {
      set(ArUcoMarkerInfo.markerRollRotationToParent, markerRollRotationToParent);
   }
}
