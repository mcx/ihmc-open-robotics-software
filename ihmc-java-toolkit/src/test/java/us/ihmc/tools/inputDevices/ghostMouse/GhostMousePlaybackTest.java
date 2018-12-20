package us.ihmc.tools.inputDevices.ghostMouse;


import java.awt.AWTException;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
@Tag("ui")
public class GhostMousePlaybackTest
{
   private final boolean PLAY_IT_BACK = false;    // Keep false in SVN so we don't mess up Bamboo. Set to true when manually testing...

	@Test// timeout = 30000
   public void testGhostMousePlayback() throws AWTException
   {
      GhostMousePlayback playback = new GhostMousePlayback();

      playback.addPlaybackEvent("{Delay 0.28}");
      playback.addPlaybackEvent("{Move (886,920)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (871,894)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (834,822)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (803,763)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (782,720)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (758,681)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (739,654)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (729,637)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (723,615)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (713,595)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (707,583)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (697,560)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (692,550)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (687,540)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (685,535)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (683,531)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{LMouse down (683,531)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{LMouse up (683,531)}");
      playback.addPlaybackEvent("{Delay 0.5}");
      playback.addPlaybackEvent("{SHIFT down}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{h down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{SHIFT up}");
      playback.addPlaybackEvent("{Delay 0.03}");
      playback.addPlaybackEvent("{h up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{e down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{e up}");
      playback.addPlaybackEvent("{Delay 0.10}");
      playback.addPlaybackEvent("{l down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{l up}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{l down}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{l up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{o down}");
      playback.addPlaybackEvent("{Delay 0.1}");
      playback.addPlaybackEvent("{o up}");
      playback.addPlaybackEvent("{Delay 0.9}");
      playback.addPlaybackEvent("{SPACE down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{SPACE up}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{SPACE down}");
      playback.addPlaybackEvent("{Delay 0.11}");
      playback.addPlaybackEvent("{SPACE up}");
      playback.addPlaybackEvent("{Delay 0.20}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.1}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 1.06}");
      playback.addPlaybackEvent("{1 down}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{1 up}");
      playback.addPlaybackEvent("{Delay 0.1}");
      playback.addPlaybackEvent("{2 down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{2 up}");
      playback.addPlaybackEvent("{Delay 0.23}");
      playback.addPlaybackEvent("{3 down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{3 up}");
      playback.addPlaybackEvent("{Delay 0.10}");
      playback.addPlaybackEvent("{9 down}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{9 up}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{9 down}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{9 up}");
      playback.addPlaybackEvent("{Delay 0.87}");
      playback.addPlaybackEvent("{. down}");
      playback.addPlaybackEvent("{Delay 0.11}");
      playback.addPlaybackEvent("{. up}");
      playback.addPlaybackEvent("{Delay 0.32}");
      playback.addPlaybackEvent("{0 down}");
      playback.addPlaybackEvent("{Delay 0.1}");
      playback.addPlaybackEvent("{0 up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{0 down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{0 up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{0 down}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{0 up}");
      playback.addPlaybackEvent("{Delay 0.69}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.13}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.11}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.06}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.65}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 0.25}");
      playback.addPlaybackEvent("{BACKSPACE down}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{BACKSPACE up}");
      playback.addPlaybackEvent("{Delay 1.04}");
      playback.addPlaybackEvent("{Move (682,534)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (668,547)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (642,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (623,566)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (606,568)}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{Move (605,568)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (597,563)}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{Move (592,559)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (588,556)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (579,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (565,555)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (548,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (540,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (537,556)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (529,559)}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{Move (524,560)}");
      playback.addPlaybackEvent("{Delay 0.12}");
      playback.addPlaybackEvent("{Move (522,560)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (521,561)}");
      playback.addPlaybackEvent("{Delay 0.32}");
      playback.addPlaybackEvent("{LMouse down (521,561)}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{Move (523,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (531,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (539,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (553,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (578,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (611,557)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (639,555)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (664,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (693,552)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (720,553)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (752,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (781,554)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (809,556)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (832,558)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (857,560)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (876,560)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (887,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (897,561)}");
      playback.addPlaybackEvent("{Delay 0.08}");
      playback.addPlaybackEvent("{Move (900,561)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (904,561)}");
      playback.addPlaybackEvent("{Delay 0.58}");
      playback.addPlaybackEvent("{Move (905,561)}");
      playback.addPlaybackEvent("{Delay 0.01}");
      playback.addPlaybackEvent("{LMouse up (905,561)}");
      playback.addPlaybackEvent("{Delay 0.25}");
      playback.addPlaybackEvent("{Move (909,562)}");
      playback.addPlaybackEvent("{Delay 0.19}");
      playback.addPlaybackEvent("{Move (910,562)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (894,559)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (870,553)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (827,548)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (807,548)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (780,548)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (740,544)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (710,542)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (675,541)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (660,541)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (645,543)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (634,544)}");
      playback.addPlaybackEvent("{Delay 0.53}");
      playback.addPlaybackEvent("{LMouse down (634,544)}");
      playback.addPlaybackEvent("{Delay 0.1}");
      playback.addPlaybackEvent("{LMouse up (634,544)}");
      playback.addPlaybackEvent("{Delay 0.14}");
      playback.addPlaybackEvent("{Move (631,545)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (625,548)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (601,555)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (572,557)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (565,555)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{Move (563,549)}");
      playback.addPlaybackEvent("{Delay 0.07}");
      playback.addPlaybackEvent("{Move (561,545)}");
      playback.addPlaybackEvent("{Delay 0.44}");
      playback.addPlaybackEvent("{Move (561,543)}");
      playback.addPlaybackEvent("{Delay 0.05}");
      playback.addPlaybackEvent("{Move (561,541)}");
      playback.addPlaybackEvent("{Delay 0.10}");
      playback.addPlaybackEvent("{Move (561,538)}");
      playback.addPlaybackEvent("{Delay 0.01}");
      playback.addPlaybackEvent("{RMouse down (561,538)}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{RMouse up (561,538)}");
      playback.addPlaybackEvent("{Delay 0.4}");
      playback.addPlaybackEvent("{RMouse down (561,538)}");
      playback.addPlaybackEvent("{Delay 0.12}");
      playback.addPlaybackEvent("{RMouse up (561,538)}");
      playback.addPlaybackEvent("{Delay 0.77}");
      playback.addPlaybackEvent("{MMouse down (561,538)}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{MMouse up (561,538)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (560,538)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (548,538)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (544,537)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{LMouse down (544,537)}");
      playback.addPlaybackEvent("{Delay 0.09}");
      playback.addPlaybackEvent("{LMouse up (544,537)}");
      playback.addPlaybackEvent("{Delay 0.2}");
      playback.addPlaybackEvent("{Move (544,537)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (552,539)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (559,545)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (568,552)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (590,571)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (622,597)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (666,635)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (695,664)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (719,694)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (740,718)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (747,733)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (761,771)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (771,790)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (775,803)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (789,827)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (804,841)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (829,860)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (845,872)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (863,886)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (873,895)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (876,899)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (879,908)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (880,910)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (881,913)}");
      playback.addPlaybackEvent("{Delay 0.02}");
      playback.addPlaybackEvent("{Move (881,918)}");
      playback.addPlaybackEvent("{Delay 0.03}");
      playback.addPlaybackEvent("{Move (881,920)}");
      playback.addPlaybackEvent("{Delay 0.01}");
      playback.addPlaybackEvent("{LMouse down (881,920)}");
      playback.addPlaybackEvent("{Delay 0.04}");
      playback.addPlaybackEvent("{LMouse up (881,920)}");


      if (PLAY_IT_BACK)
         playback.playback();
   }

	@Disabled
	@Test// timeout=300000
   public void testLoad() throws AWTException
   {
      GhostMousePlayback playback = new GhostMousePlayback();

      playback.load("testResources/us/ihmc/utilities/keyboardAndMouse/ghostMousePlaybackTest/GhostMousePlaybackForTest1.rms");

      System.out.println(playback);

      if (PLAY_IT_BACK)
         playback.playback();
   }

}
