package us.ihmc.perception;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencl.global.OpenCL;
import org.bytedeco.opencv.global.opencv_core;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.log.LogTools;
import us.ihmc.tools.thread.Activator;

import java.nio.ByteBuffer;

public class BytedecoTools
{
   public static Activator loadNativesOnAThread()
   {
      Activator nativesActivated = new Activator();
      ThreadTools.startAThread(() ->
      {
         loadNatives();
         nativesActivated.activate();
      }, "Bytedeco loader");
      return nativesActivated;
   }

   public static Activator loadOpenCVNativesOnAThread()
   {
      Activator nativesActivated = new Activator();
      ThreadTools.startAThread(() ->
      {
         loadOpenCV();
         nativesActivated.activate();
      }, "Bytedeco loader");
      return nativesActivated;
   }

   public static void loadNatives()
   {
      loadOpenCL();
      loadOpenCV();
   }

   public static void loadOpenCL()
   {
      LogTools.info("Loading Bytedeco OpenCL...");
      Loader.load(OpenCL.class);
      LogTools.info("Bytedeco OpenCL loaded.");
   }

   public static void loadOpenCV()
   {
      LogTools.info("Loading Bytedeco OpenCV...");
      Loader.load(opencv_core.class);
      LogTools.info("Bytedeco OpenCV loaded.");
   }

   public static String stringFromByteBuffer(BytePointer bytePointerWithString)
   {
      ByteBuffer byteBuffer = bytePointerWithString.asBuffer();
      StringBuilder stringBuilder = new StringBuilder();
      for (int i = 0; i < byteBuffer.capacity(); i++)
      {
         if (byteBuffer.get(i) == 0)
            break;

         stringBuilder.append((char) byteBuffer.get(i));
      }
      return stringBuilder.toString();
   }
}
