package us.ihmc.rdx.perception;

import imgui.ImGui;
import imgui.type.ImInt;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_features2d.SimpleBlobDetector;
import us.ihmc.rdx.imgui.ImGuiPanel;
import us.ihmc.rdx.imgui.ImGuiTools;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.tools.thread.MissingThreadTools;
import us.ihmc.tools.thread.ResettableExceptionHandlingExecutorService;
import us.ihmc.tools.thread.ZeroCopySwapReference;

import java.util.function.Consumer;

public class CalibrationPatternDetection
{
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImGuiPanel panel = new ImGuiPanel("Calibration Pattern", this::renderImGuiWidgets);
   private final Mat bgrSourceCopy;
   private final Mat grayscaleImage;
   private final SimpleBlobDetector simpleBlobDetector;
   private final ImInt patternWidth = new ImInt(4);
   private final ImInt patternHeight = new ImInt(3);
   private boolean patternFound = false;
   private Size patternSize;
   private final ZeroCopySwapReference<Mat> cornersOrCenters;
   private final Object avoidCopiedImageTearing = new Object();
   private final Runnable doPatternDetection = this::doPatternDetection;
   private final ResettableExceptionHandlingExecutorService patternDetectionThreadQueue
         = MissingThreadTools.newSingleThreadExecutor("PatternDetection", true, 1);
   private enum Pattern { CHESSBOARD, CIRCLES }
   private Pattern pattern = Pattern.CHESSBOARD;
   private final Consumer<Mat> accessOnLowPriorityThread = this::accessOnLowPriorityThread;
   private Mat rgbaMatForDrawing;
   private final Consumer<Mat> accessOnHighPriorityThread = this::accessOnHighPriorityThread;

   public CalibrationPatternDetection()
   {
      bgrSourceCopy = new Mat();
      grayscaleImage = new Mat();
      patternSize = new Size(patternWidth.get(), patternHeight.get());
      cornersOrCenters = new ZeroCopySwapReference<>(Mat::new);
      simpleBlobDetector = SimpleBlobDetector.create();
   }

   public void copyBGRImage(Mat bgrImageToCopy)
   {
      synchronized (avoidCopiedImageTearing)
      {
         bgrImageToCopy.copyTo(bgrSourceCopy);
      }
   }

   public void copyRGBImage(Mat bgrImageToCopy)
   {
      synchronized (avoidCopiedImageTearing)
      {
         opencv_imgproc.cvtColor(bgrImageToCopy, bgrSourceCopy, opencv_imgproc.COLOR_RGB2BGR);
      }
   }

   public void update()
   {
      if (bgrSourceCopy.rows() > 0)
      {
         patternDetectionThreadQueue.clearQueueAndExecute(doPatternDetection);
      }
   }

   public void doPatternDetection()
   {
      synchronized (avoidCopiedImageTearing)
      {
         opencv_imgproc.cvtColor(bgrSourceCopy, grayscaleImage, opencv_imgproc.COLOR_BGR2GRAY);
      }

      cornersOrCenters.accessOnLowPriorityThread(accessOnLowPriorityThread);
   }

   private void accessOnLowPriorityThread(Mat cornersOrCenters)
   {
      if (pattern == Pattern.CHESSBOARD)
      {
         patternFound = opencv_calib3d.findChessboardCorners(grayscaleImage,
                                                             patternSize,
                                                             cornersOrCenters,
                                                             opencv_calib3d.CALIB_CB_ADAPTIVE_THRESH | opencv_calib3d.CALIB_CB_NORMALIZE_IMAGE);
      }
      else
      {
         patternFound = opencv_calib3d.findCirclesGrid(grayscaleImage,
                                                       patternSize,
                                                       cornersOrCenters,
                                                       opencv_calib3d.CALIB_CB_SYMMETRIC_GRID,
                                                       simpleBlobDetector);
      }
   }

   public void drawCornersOrCenters(Mat rgbaMat)
   {
      rgbaMatForDrawing = rgbaMat;
      cornersOrCenters.accessOnHighPriorityThread(accessOnHighPriorityThread);
   }

   private void accessOnHighPriorityThread(Mat cornersOrCenters)
   {
      opencv_calib3d.drawChessboardCorners(rgbaMatForDrawing, patternSize, cornersOrCenters, patternFound);
   }

   public void renderImGuiWidgets()
   {
      ImGui.text("Pattern:");
      ImGui.sameLine();
      if (ImGui.radioButton(labels.get("Chessboard"), pattern == Pattern.CHESSBOARD))
      {
         pattern = Pattern.CHESSBOARD;
      }
      ImGui.sameLine();
      if (ImGui.radioButton(labels.get("Circles"), pattern == Pattern.CIRCLES))
      {
         pattern = Pattern.CIRCLES;
      }
      if (ImGuiTools.volatileInputInt(labels.get("Pattern width"), patternWidth))
      {
         patternSize = new Size(patternWidth.get(), patternHeight.get());
      }
      if (ImGuiTools.volatileInputInt(labels.get("Pattern height"), patternHeight))
      {
         patternSize = new Size(patternWidth.get(), patternHeight.get());
      }
      ImGui.text("Pattern found: " + patternFound);
   }

   public ImGuiPanel getPanel()
   {
      return panel;
   }
}
