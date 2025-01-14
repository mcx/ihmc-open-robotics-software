package us.ihmc.rdx.ui.teleoperation;

import us.ihmc.rdx.tools.RDXIconTexture;
import us.ihmc.rdx.ui.RDX3DPanelToolbarButton;
import us.ihmc.rdx.ui.RDXBaseUI;
import us.ihmc.robotics.robotSide.RobotSide;

public class RDXHandQuickAccessButtons
{
   private final RDX3DPanelToolbarButton openHandCalibrateButton;
   private final Runnable openHand;
   private final Runnable closeHand;
   private final Runnable calibrateHand;
   private final Runnable resetHand;
   private final RDX3DPanelToolbarButton closeHandResetButton;
   private final RDXIconTexture openIcon;
   private final RDXIconTexture calibrateIcon;
   private final RDXIconTexture closeIcon;
   private final RDXIconTexture resetIcon;
   private final String openHandText;
   private final String calibrateHandText;
   private final String closeHandText;
   private final String resetHandText;

   public RDXHandQuickAccessButtons(RDXBaseUI baseUI,
                                    RobotSide side,
                                    Runnable openHand,
                                    Runnable closeHand,
                                    Runnable calibrateHand,
                                    Runnable resetHand)
   {
      if (side == RobotSide.LEFT) // Make buttons symmetrical
      {
         closeHandResetButton = baseUI.getPrimary3DPanel().addToolbarButton();
         openHandCalibrateButton = baseUI.getPrimary3DPanel().addToolbarButton();
      }
      else
      {
         openHandCalibrateButton = baseUI.getPrimary3DPanel().addToolbarButton();
         closeHandResetButton = baseUI.getPrimary3DPanel().addToolbarButton();
      }

      this.openHand = openHand;
      this.closeHand = closeHand;
      this.calibrateHand = calibrateHand;
      this.resetHand = resetHand;

      calibrateIcon = openHandCalibrateButton.loadAndSetIcon("icons/calibrate.png");
      openIcon = openHandCalibrateButton.loadAndSetIcon("icons/openHand%s.png".formatted(side.getPascalCaseName()));
      openHandText = "Open %s hand".formatted(side.getLowerCaseName());
      calibrateHandText = "Calibrate %s hand".formatted(side.getLowerCaseName());
      openHandCalibrateButton.setTooltipText(openHandText);
      openHandCalibrateButton.setOnPressed(openHand);

      resetIcon = closeHandResetButton.loadAndSetIcon("icons/resetHand.png");
      closeIcon = closeHandResetButton.loadAndSetIcon("icons/closeHand%s.png".formatted(side.getPascalCaseName()));
      closeHandText = "Close %s hand".formatted(side.getLowerCaseName());
      resetHandText = "Reset %s hand".formatted(side.getLowerCaseName());
      closeHandResetButton.setTooltipText(closeHandText);
      closeHandResetButton.setOnPressed(closeHand);
   }

   public void update(RDXSakeHandInformation sakeHandInfo)
   {
      openHandCalibrateButton.setIconTexture(sakeHandInfo.getCalibrated() ? openIcon : calibrateIcon);
      openHandCalibrateButton.setOnPressed(sakeHandInfo.getCalibrated() ? openHand : calibrateHand);
      openHandCalibrateButton.setTooltipText(sakeHandInfo.getCalibrated() ? openHandText : calibrateHandText);
      closeHandResetButton.setIconTexture(sakeHandInfo.getNeedsReset() ? resetIcon : closeIcon);
      closeHandResetButton.setOnPressed(sakeHandInfo.getNeedsReset() ? resetHand : closeHand);
      closeHandResetButton.setTooltipText(sakeHandInfo.getNeedsReset() ? resetHandText : closeHandText);
   }
}
