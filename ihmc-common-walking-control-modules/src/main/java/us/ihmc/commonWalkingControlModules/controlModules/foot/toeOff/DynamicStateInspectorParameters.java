package us.ihmc.commonWalkingControlModules.controlModules.foot.toeOff;

import us.ihmc.yoVariables.listener.YoVariableChangedListener;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

public class DynamicStateInspectorParameters
{

   /** If the ICP is this distance based the heel of the elading foot, toe off should happen, regardless of any of the other conditions. **/
   private final YoDouble distanceForwardFromHeel;

   /** This checks to make sure the ICP isn't falling to the outside of the trailing foot. **/
   private final YoDouble minLateralDistanceInside;

   /**
    * These variables make sure the ICP is far enough from the toe off point. If they're far enough, then there's probably enough control
    * authority to control them
    */
   private final YoDouble minDistanceFromTheToe;
   private final YoDouble minFractionOfStrideFromTheToe;

   private final YoDouble minDistanceAlongErrorFromOutsideEdge;
   private final YoDouble minOrthogonalDistanceFromOutsideEdge;
   private final YoDouble minDistanceAlongErrorFromInsideEdge;
   private final YoDouble minOrthogonalDistanceFromInsideEdge;

   private final YoDouble minNormalizedDistanceFromOutsideEdge;
   private final YoDouble minNormalizedDistanceFromInsideEdge;
   private final YoDouble maxRatioOfControlDecreaseFromToeingOff;
   private final YoDouble maxNormalizedErrorNeededForControl;

   public DynamicStateInspectorParameters(YoRegistry parentRegistry)
   {
      this("", parentRegistry);
   }

   public DynamicStateInspectorParameters(String suffix, YoRegistry parentRegistry)
   {
      YoRegistry registry = new YoRegistry(getClass().getSimpleName() + suffix);

      distanceForwardFromHeel = new YoDouble("distForwardFromHeel" + suffix, registry);
      minLateralDistanceInside = new YoDouble("minLatDistInside" + suffix, registry);

      minDistanceFromTheToe = new YoDouble("minDistanceFromToe" + suffix, registry);
      minFractionOfStrideFromTheToe = new YoDouble("minFractionOfStrideFromToe" + suffix, registry);

      minDistanceAlongErrorFromOutsideEdge = new YoDouble("minDistAlongErrorFromOutEdge" + suffix, registry);
      minOrthogonalDistanceFromOutsideEdge = new YoDouble("minOrthoDistFromOutEdge" + suffix, registry);
      minDistanceAlongErrorFromInsideEdge = new YoDouble("minDistAlongErrorFromInEdge" + suffix, registry);
      minOrthogonalDistanceFromInsideEdge = new YoDouble("minOrthoDistFromInEdge" + suffix, registry);

      minNormalizedDistanceFromOutsideEdge = new YoDouble("minNormDistFromOutEdge" + suffix, registry);
      minNormalizedDistanceFromInsideEdge = new YoDouble("minNormDistFromInEdge" + suffix, registry);
      maxRatioOfControlDecreaseFromToeingOff = new YoDouble("maxRatioOfControlDecreaseFromToeingOff" + suffix, registry);
      maxNormalizedErrorNeededForControl = new YoDouble("maxNormErrorNeededForControl" + suffix, registry);

      minNormalizedDistanceFromOutsideEdge.setToNaN();
      minNormalizedDistanceFromInsideEdge.setToNaN();
      maxNormalizedErrorNeededForControl.setToNaN();
      maxRatioOfControlDecreaseFromToeingOff.set(Double.POSITIVE_INFINITY);

      minNormalizedDistanceFromInsideEdge.set(0.2);

      parentRegistry.addChild(registry);
   }

   public double getDistanceForwardFromHeel()
   {
      return distanceForwardFromHeel.getDoubleValue();
   }

   public double getMinLateralDistanceInside()
   {
      return minLateralDistanceInside.getDoubleValue();
   }

   public double getMinDistanceFromTheToe()
   {
      return minDistanceFromTheToe.getDoubleValue();
   }

   public double getMinFractionOfStrideFromTheToe()
   {
      return minFractionOfStrideFromTheToe.getDoubleValue();
   }

   public double getMinDistanceAlongErrorFromOutsideEdge()
   {
      return minDistanceAlongErrorFromOutsideEdge.getDoubleValue();
   }

   public double getMinNormalizedDistanceFromOutsideEdge()
   {
      return minNormalizedDistanceFromOutsideEdge.getDoubleValue();
   }

   public double getMinNormalizedDistanceFromInsideEdge()
   {
      return minNormalizedDistanceFromInsideEdge.getDoubleValue();
   }

   public double getMinOrthogonalDistanceFromOutsideEdge()
   {
      return minOrthogonalDistanceFromOutsideEdge.getDoubleValue();
   }

   public double getMinDistanceAlongErrorFromInsideEdge()
   {
      return minDistanceAlongErrorFromInsideEdge.getDoubleValue();
   }

   public double getMinOrthogonalDistanceFromInsideEdge()
   {
      return minOrthogonalDistanceFromInsideEdge.getDoubleValue();
   }

   public double getMaxNormalizedErrorNeededForControl()
   {
      return maxNormalizedErrorNeededForControl.getDoubleValue();
   }

   public double getMaxRatioOfControlDecreaseFromToeingOff()
   {
      return maxRatioOfControlDecreaseFromToeingOff.getDoubleValue();
   }

   public void attachParameterChangeListener(YoVariableChangedListener changedListener)
   {
      distanceForwardFromHeel.addListener(changedListener);
      minLateralDistanceInside.addListener(changedListener);
      minDistanceFromTheToe.addListener(changedListener);
      minFractionOfStrideFromTheToe.addListener(changedListener);
      minDistanceAlongErrorFromOutsideEdge.addListener(changedListener);
      minOrthogonalDistanceFromOutsideEdge.addListener(changedListener);
      minDistanceAlongErrorFromInsideEdge.addListener(changedListener);
      minOrthogonalDistanceFromInsideEdge.addListener(changedListener);
      minNormalizedDistanceFromInsideEdge.addListener(changedListener);
      minNormalizedDistanceFromOutsideEdge.addListener(changedListener);
      maxRatioOfControlDecreaseFromToeingOff.addListener(changedListener);
      maxNormalizedErrorNeededForControl.addListener(changedListener);
   }
}
