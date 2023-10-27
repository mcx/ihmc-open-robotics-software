package us.ihmc.sensors;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.log.LogTools;
import us.ihmc.perception.RawImage;
import us.ihmc.perception.realsense.RealsenseConfiguration;
import us.ihmc.perception.realsense.RealsenseDevice;
import us.ihmc.tools.thread.RestartableThrottledThread;

import java.time.Instant;
import java.util.function.Supplier;

public class RealsenseColorDepthImageRetriever
{
   private static final double OUTPUT_FREQUENCY = 20.0;

   private final RealsenseDevice realsense;

   private long grabSequenceNumber = 0L;

   private RawImage depthImage = null;
   private RawImage colorImage = null;

   private Mat depthMat16UC1;
   private Mat colorMatRGB;

   private final FramePose3D depthPose = new FramePose3D();
   private final FramePose3D colorPose = new FramePose3D();
   private final Supplier<ReferenceFrame> sensorFrameSupplier;
   private final RestartableThrottledThread realsenseGrabThread;

   public RealsenseColorDepthImageRetriever(RealsenseDevice realsense, RealsenseConfiguration realsenseConfiguration, Supplier<ReferenceFrame> sensorFrameSupplier)
   {
      this.sensorFrameSupplier = sensorFrameSupplier;
      this.realsense = realsense;
      realsenseGrabThread = new RestartableThrottledThread("RealsenseImageGrabber", OUTPUT_FREQUENCY, this::updateImages);

      if (realsense.getDevice() == null)
      {
         // Find something else to do here
         LogTools.error("RealSense device is NULL");
      }

      realsense.enableColor(realsenseConfiguration);
      realsense.initialize();
   }

   private void updateImages()
   {
      if (realsense.readFrameData())
      {
         realsense.updateDataBytePointers();
         Instant acquisitionTime = Instant.now();

         ReferenceFrame cameraFrame = sensorFrameSupplier.get();
         depthPose.setToZero(sensorFrameSupplier.get());
         depthPose.changeFrame(ReferenceFrame.getWorldFrame());

         colorPose.setIncludingFrame(cameraFrame, realsense.getDepthToColorTranslation(), realsense.getDepthToColorRotation());
         colorPose.invert();
         colorPose.changeFrame(ReferenceFrame.getWorldFrame());

         if (depthMat16UC1 != null)
            depthMat16UC1.close();
         depthMat16UC1 = new Mat(realsense.getDepthHeight(), realsense.getDepthWidth(), opencv_core.CV_16UC1, realsense.getDepthFrameData());
         depthImage = new RawImage(grabSequenceNumber,
                                   acquisitionTime,
                                   realsense.getDepthWidth(),
                                   realsense.getDepthHeight(),
                                   (float) realsense.getDepthDiscretization(),
                                   depthMat16UC1.clone(),
                                   null,
                                   opencv_core.CV_16UC1,
                                   (float) realsense.getDepthFocalLengthPixelsX(),
                                   (float) realsense.getDepthFocalLengthPixelsY(),
                                   (float) realsense.getDepthPrincipalOffsetXPixels(),
                                   (float) realsense.getDepthPrincipalOffsetYPixels(),
                                   depthPose.getPosition(),
                                   depthPose.getOrientation());

         if (colorMatRGB != null)
            colorMatRGB.close();
         colorMatRGB = new Mat(realsense.getColorHeight(), realsense.getColorWidth(), opencv_core.CV_8UC3, realsense.getColorFrameData());
         colorImage = new RawImage(grabSequenceNumber,
                                   acquisitionTime,
                                   realsense.getColorWidth(),
                                   realsense.getColorHeight(),
                                   (float) realsense.getDepthDiscretization(),
                                   colorMatRGB.clone(),
                                   null,
                                   opencv_core.CV_8UC3,
                                   (float) realsense.getColorFocalLengthPixelsX(),
                                   (float) realsense.getColorFocalLengthPixelsY(),
                                   (float) realsense.getColorPrincipalOffsetXPixels(),
                                   (float) realsense.getColorPrincipalOffsetYPixels(),
                                   colorPose.getPosition(),
                                   colorPose.getOrientation());

         grabSequenceNumber++;
      }
   }

   public RawImage getLatestRawDepthImage()
   {
      return depthImage;
   }

   public RawImage getLatestRawColorImage()
   {
      return colorImage;
   }

   public void start()
   {
      realsenseGrabThread.start();
   }

   public void stop()
   {
      realsenseGrabThread.stop();
   }

   public void destroy()
   {
      stop();
      depthImage.destroy();
      colorImage.destroy();
      realsense.deleteDevice();
   }
}
