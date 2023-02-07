package us.ihmc.rdx.ui.graphics.ros2;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.internal.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import perception_msgs.msg.dds.ImageMessage;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.log.LogTools;
import us.ihmc.perception.OpenCLManager;
import us.ihmc.perception.memory.NativeMemoryTools;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.pubsub.common.SampleInfo;
import us.ihmc.pubsub.subscriber.Subscriber;
import us.ihmc.rdx.RDXPointCloudRenderer;
import us.ihmc.rdx.imgui.ImGuiTools;
import us.ihmc.rdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.rdx.ui.graphics.RDXOusterDepthImageToPointCloudKernel;
import us.ihmc.rdx.ui.graphics.RDXMessageSizeReadout;
import us.ihmc.rdx.ui.graphics.RDXSequenceDiscontinuityPlot;
import us.ihmc.rdx.ui.tools.ImPlotDoublePlot;
import us.ihmc.rdx.ui.tools.ImPlotFrequencyPlot;
import us.ihmc.rdx.ui.visualizers.RDXVisualizer;
import us.ihmc.robotics.time.TimeTools;
import us.ihmc.ros2.ROS2QosProfile;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.ros2.RealtimeROS2Node;
import us.ihmc.tools.string.StringTools;

import java.nio.ByteBuffer;

public class RDXROS2OusterPointCloudVisualizer extends RDXVisualizer implements RenderableProvider
{
   private final String titleBeforeAdditions;
   private final PubSubImplementation pubSubImplementation;
   private final ROS2Topic<ImageMessage> topic;
   private RealtimeROS2Node realtimeROS2Node;
   private final ImageMessage imageMessage = new ImageMessage();
   private final SampleInfo sampleInfo = new SampleInfo();
   private final Object syncObject = new Object();
   private final ImPlotFrequencyPlot frequencyPlot = new ImPlotFrequencyPlot("Hz", 30);
   private final ImPlotDoublePlot delayPlot = new ImPlotDoublePlot("Delay", 30);
   private final ImFloat pointSize = new ImFloat(0.01f);
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImBoolean subscribed = new ImBoolean(false);
   private final RDXPointCloudRenderer pointCloudRenderer = new RDXPointCloudRenderer();
   private int totalNumberOfPoints;
   private OpenCLManager openCLManager;
   private RDXOusterDepthImageToPointCloudKernel depthImageToPointCloudKernel;
   private ByteBuffer decompressionInputBuffer;
   private BytePointer decompressionInputBytePointer;
   private Mat decompressionInputMat;
   private final RDXMessageSizeReadout messageSizeReadout = new RDXMessageSizeReadout();
   private final RDXSequenceDiscontinuityPlot sequenceDiscontinuityPlot = new RDXSequenceDiscontinuityPlot();
   private int depthWidth;
   private int depthHeight;

   public RDXROS2OusterPointCloudVisualizer(String title, PubSubImplementation pubSubImplementation, ROS2Topic<ImageMessage> topic)
   {
      super(title + " (ROS 2)");
      titleBeforeAdditions = title;
      this.pubSubImplementation = pubSubImplementation;
      this.topic = topic;
   }

   private void subscribe()
   {
      subscribed.set(true);
      this.realtimeROS2Node = ROS2Tools.createRealtimeROS2Node(pubSubImplementation, StringTools.titleToSnakeCase(titleBeforeAdditions));
      ROS2Tools.createCallbackSubscription(realtimeROS2Node, topic, ROS2QosProfile.BEST_EFFORT(), this::queueRenderImageBasedPointCloud);
      realtimeROS2Node.spin();
   }

   private void queueRenderImageBasedPointCloud(Subscriber<ImageMessage> subscriber)
   {
      frequencyPlot.ping();

      synchronized (syncObject)
      {
         imageMessage.getData().resetQuick();
         subscriber.takeNextData(imageMessage, sampleInfo);
         delayPlot.addValue(TimeTools.calculateDelay(imageMessage.getAcquisitionTime().getSecondsSinceEpoch(),
                                                     imageMessage.getAcquisitionTime().getAdditionalNanos()));
      }
   }

   @Override
   public void create()
   {
      super.create();

      openCLManager = new OpenCLManager();
      openCLManager.create();
   }

   @Override
   public void update()
   {
      super.update();

      if (subscribed.get() && isActive() && frequencyPlot.anyPingsYet())
      {
         int numberOfBytes;
         long acquisitionTimeSecondsSinceEpoch;
         long acquisitionTimeAdditionalNanos;
         synchronized (syncObject)
         {
            if (decompressionInputBuffer == null)
            {
               depthWidth = imageMessage.getImageWidth();
               depthHeight = imageMessage.getImageHeight();
               totalNumberOfPoints = depthWidth * depthHeight;
               pointCloudRenderer.create(totalNumberOfPoints);
               decompressionInputBuffer = NativeMemoryTools.allocate(depthWidth * depthHeight * Short.BYTES);
               decompressionInputBytePointer = new BytePointer(decompressionInputBuffer);
               decompressionInputMat = new Mat(1, 1, opencv_core.CV_8UC1);

               depthImageToPointCloudKernel = new RDXOusterDepthImageToPointCloudKernel(pointCloudRenderer, openCLManager, depthWidth, depthHeight);
               LogTools.info("Allocated new buffers. {} points.", totalNumberOfPoints);
            }

            sequenceDiscontinuityPlot.update(imageMessage.getSequenceNumber());

            acquisitionTimeSecondsSinceEpoch = imageMessage.getAcquisitionTime().getSecondsSinceEpoch();
            acquisitionTimeAdditionalNanos = imageMessage.getAcquisitionTime().getAdditionalNanos();

            numberOfBytes = imageMessage.getData().size();
            decompressionInputBuffer.rewind();
            decompressionInputBuffer.limit(depthWidth * depthHeight * Short.BYTES);
            for (int i = 0; i < numberOfBytes; i++)
            {
               decompressionInputBuffer.put(imageMessage.getData().get(i));
            }
            decompressionInputBuffer.flip();
         }

         messageSizeReadout.update(numberOfBytes);

         decompressionInputBytePointer.position(0);
         decompressionInputBytePointer.limit(numberOfBytes);

         decompressionInputMat.cols(numberOfBytes);
         decompressionInputMat.data(decompressionInputBytePointer);

         depthImageToPointCloudKernel.getDepthImage().getBackingDirectByteBuffer().rewind();
         opencv_imgcodecs.imdecode(decompressionInputMat,
                                   opencv_imgcodecs.IMREAD_UNCHANGED,
                                   depthImageToPointCloudKernel.getDepthImage().getBytedecoOpenCVMat());
         depthImageToPointCloudKernel.getDepthImage().getBackingDirectByteBuffer().rewind();

         // TODO: Create tuners for these
         double verticalFieldOfView = Math.PI / 2.0;
         double horizontalFieldOfView = 2.0 * Math.PI;
         depthImageToPointCloudKernel.getSensorTransformToWorld().set(imageMessage.getOrientation(), imageMessage.getPosition());
         depthImageToPointCloudKernel.runKernel((float) horizontalFieldOfView, (float) verticalFieldOfView, pointSize.get());

         delayPlot.addValue(TimeTools.calculateDelay(acquisitionTimeSecondsSinceEpoch, acquisitionTimeAdditionalNanos));
      }
   }

   @Override
   public void renderImGuiWidgets()
   {
      if (ImGui.checkbox(labels.getHidden(getTitle() + "Subscribed"), subscribed))
      {
         setSubscribed(subscribed.get());
      }
      ImGuiTools.previousWidgetTooltip("Subscribed");
      ImGui.sameLine();
      super.renderImGuiWidgets();
      ImGui.text(topic.getName());
      if (frequencyPlot.anyPingsYet())
      {
         messageSizeReadout.renderImGuiWidgets();
      }
      ImGui.sliderFloat(labels.get("Point size"), pointSize.getData(), 0.0005f, 0.05f);
      if (frequencyPlot.anyPingsYet())
      {
         frequencyPlot.renderImGuiWidgets();
         delayPlot.renderImGuiWidgets();
         sequenceDiscontinuityPlot.renderImGuiWidgets();
      }
   }

   @Override
   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      if (isActive())
         pointCloudRenderer.getRenderables(renderables, pool);
   }

   @Override
   public void destroy()
   {
      unsubscribe();
      super.destroy();
   }

   public void setSubscribed(boolean subscribed)
   {
      if (subscribed && realtimeROS2Node == null)
      {
         subscribe();
      }
      else if (!subscribed && realtimeROS2Node != null)
      {
         unsubscribe();
      }
   }

   private void unsubscribe()
   {
      subscribed.set(false);
      if (realtimeROS2Node != null)
      {
         realtimeROS2Node.destroy();
         realtimeROS2Node = null;
      }
   }

   public boolean isSubscribed()
   {
      return subscribed.get();
   }
}
