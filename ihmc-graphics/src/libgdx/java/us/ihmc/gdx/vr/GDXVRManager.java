package us.ihmc.gdx.vr;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.internal.ImGui;
import imgui.type.ImBoolean;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.thread.Notification;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.commons.time.Stopwatch;
import us.ihmc.gdx.imgui.ImGuiPlot;
import us.ihmc.gdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.gdx.input.ImGui3DViewInput;
import us.ihmc.gdx.sceneManager.GDX3DSceneManager;
import us.ihmc.gdx.ui.GDXImGuiBasedUI;
import us.ihmc.gdx.ui.gizmo.GDXPose3DGizmo;
import us.ihmc.log.LogTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.tools.thread.MissingThreadTools;
import us.ihmc.tools.time.FrequencyCalculator;

/** This class should manage VR as part of the ImGuiBasedUI. */
public class GDXVRManager
{
   private static final boolean DEBUG_EYE_FRAMES = false;
   private final GDXVRContext context = new GDXVRContext();
   private Notification contextCreatedNotification;
   private boolean contextInitialized = false;
   private boolean initializing = false;
   private boolean skipHeadset = false;
   private final Object syncObject = new Object();
   private final GDXPose3DGizmo scenePoseGizmo = new GDXPose3DGizmo();
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImBoolean vrEnabled = new ImBoolean(false);
//   private final ResettableExceptionHandlingExecutorService waitGetPosesExecutor = MissingThreadTools.newSingleThreadExecutor("PoseWaiterOnner", true, 1);
   private final Notification posesReady = new Notification();
   private volatile boolean waitingOnPoses = false;
   private final GDXVRTeleporter teleporter = new GDXVRTeleporter();
   private ImGuiPlot vrFPSPlot = new ImGuiPlot(labels.get("VR FPS Hz"), 1000, 300, 50);
   private FrequencyCalculator vrFPSCalculator = new FrequencyCalculator();
   private ImGuiPlot waitGetPosesPlot = new ImGuiPlot(labels.get("Wait Get Poses Hz"), 1000, 300, 50);
   private ImGuiPlot waitGetToRenderDelayPlot = new ImGuiPlot(labels.get("WaitGetToRender Delay"), 1000, 300, 50);
   private final Stopwatch waitGetToRenderStopwatch = new Stopwatch();
   private volatile double waitGetToRenderDuration = Double.NaN;
   private FrequencyCalculator waitGetPosesFrequencyCalculator = new FrequencyCalculator();
   private ImGuiPlot pollEventsPlot = new ImGuiPlot(labels.get("Poll Events Hz"), 1000, 300, 50);
   private FrequencyCalculator pollEventsFrequencyCalculator = new FrequencyCalculator();
   private ImGuiPlot contextInitializedPlot = new ImGuiPlot(labels.get("contextInitialized"), 1000, 300, 50);
   private ImGuiPlot initSystemCountPlot = new ImGuiPlot(labels.get("initSystemCount"), 1000, 300, 50);
   private volatile int initSystemCount = 0;
   private ImGuiPlot setupEyesCountPlot = new ImGuiPlot(labels.get("setupEyesCount"), 1000, 300, 50);
   private volatile int setupEyesCount = 0;
   private ImGuiPlot waitGetPosesExceptionCountPlot = new ImGuiPlot(labels.get("waitGetPosesExceptionCount"), 1000, 300, 50);
   private volatile int waitGetPosesExceptionCount = 0;
   private final Notification waitOnPosesNotification = new Notification();

   public void create()
   {
      teleporter.create();
      context.addVRInputProcessor(teleporter::processVRInput);

      ThreadTools.startAsDaemon(this::waitOnPoses, getClass().getSimpleName() + "WaitOnPosesThread");
   }

   /**
    * Doing poll and render close together makes VR performance the best it can be
    * and reduce dizziness.
    *
    * TODO: This thread has to be a multiple of the parent (240?)
    * TODO: If the rest of the app is too slow, can we run this one faster?
    */
   public void pollEventsAndRender(GDXImGuiBasedUI baseUI, GDX3DSceneManager sceneManager)
   {
      boolean posesReady = pollEvents(baseUI);
      if (posesReady && isVRReady())
      {
         skipHeadset = true;
         vrFPSCalculator.ping();
         waitGetToRenderDuration = waitGetToRenderStopwatch.totalElapsed();
         synchronized (syncObject)
         {
            context.renderEyes(sceneManager.getSceneBasics());
         }
         skipHeadset = false;
      }
   }

   private boolean pollEvents(GDXImGuiBasedUI baseUI)
   {
      boolean posesReadyThisFrame = false;
      if (vrEnabled.get())
      {
         if (!initializing && contextCreatedNotification == null) // should completely dispose and recreate?
         {
            initializing = true;
            contextCreatedNotification = new Notification();
            MissingThreadTools.startAsDaemon(getClass().getSimpleName() + "-initSystem", DefaultExceptionHandler.MESSAGE_AND_STACKTRACE, () ->
            {
               initSystemCount++;
               synchronized (syncObject)
               {
                  context.initSystem();
               }
               contextCreatedNotification.set();
            });
         }
         if (contextCreatedNotification != null && contextCreatedNotification.poll())
         {
            initializing = false;
            setupEyesCount++;
            synchronized (syncObject)
            {
               context.setupEyes();
            }

            if (!Boolean.parseBoolean(System.getProperty("gdx.free.spin")))
            {
               baseUI.setForegroundFPS(350); // TODO: Do something better with this
            }
            baseUI.setVsync(false); // important to disable vsync for VR

            scenePoseGizmo.create(baseUI.get3DSceneManager().getCamera3D());

            contextInitialized = true;
         }

         if (contextInitialized)
         {
            // Waiting for the poses on a thread allows for the rest of the application to keep
            // cranking faster than VR can do stuff. This also makes the performance graph in Steam
            // show the correct value and the OpenVR stack work much better.
            // TODO: This whole thing might have major issues because
            // there's a delay waiting for the next time this method is called
            posesReadyThisFrame = posesReady.poll();

            if (!posesReadyThisFrame && !waitingOnPoses)
            {
               waitingOnPoses = true;
               waitOnPosesNotification.set();
//               MissingThreadTools.startAsDaemon("WaitGetPoses", exception ->
//               {
//                  waitGetPosesExceptionCount++;
//                  LogTools.error(exception.getMessage());
//                  exception.printStackTrace();
//               }, () ->
//               {
//                  waitGetPosesFrequencyCalculator.ping();
//                  synchronized (syncObject)
//                  {
//                     context.waitGetPoses();
//                  }
//                  waitGetToRenderStopwatch.reset();
//                  posesReady.set();
//               });
            }
            else
            {
               waitingOnPoses = false;
            }

            if (posesReadyThisFrame)
            {
               pollEventsFrequencyCalculator.ping();
               synchronized (syncObject)
               {
                  context.pollEvents(); // FIXME: Potential bug is that the poses get updated in the above thread while they're being used in here
               }
            }
         }
      }
      else
      {
         if (contextCreatedNotification != null && contextInitialized)
         {
            dispose();
         }
      }

      return posesReadyThisFrame;
   }

   private void waitOnPoses()
   {
      while (true)
      {
         waitOnPosesNotification.blockingPoll();

         try
         {
            waitGetPosesFrequencyCalculator.ping();
            synchronized (syncObject)
            {
               context.waitGetPoses();
            }
            waitGetToRenderStopwatch.reset();
            posesReady.set();
         }
         catch (Exception exception)
         {
            waitGetPosesExceptionCount++;
            LogTools.error(exception.getMessage());
            exception.printStackTrace();
         }
      }
   }

   public void renderImGuiEnableWidget()
   {
      if (ImGui.checkbox(labels.get("VR Enabled"), vrEnabled))
      {
         if (vrEnabled.get())
            LogTools.info("Enabling VR");
         else
            LogTools.info("Disabling VR");
      }
      if (ImGui.isItemHovered())
      {
         float right = ImGui.getWindowPosX() + ImGui.getWindowSizeX();
         float y = ImGui.getItemRectMaxY();
         ImGui.setNextWindowPos(right - 600, y); // prevent the tooltip from creating a new window
         ImGui.setTooltip("It is recommended to start SteamVR and power on the VR controllers before clicking this button.");
      }
   }

   public void renderImGuiDebugWidgets()
   {
      contextInitializedPlot.render(contextInitialized ? 1.0 : 0.0);
      initSystemCountPlot.render(initSystemCount);
      setupEyesCountPlot.render(setupEyesCount);
      waitGetPosesPlot.render(waitGetPosesFrequencyCalculator.getFrequency());
      pollEventsPlot.render(pollEventsFrequencyCalculator.getFrequency());
      vrFPSPlot.render(vrFPSCalculator.getFrequency());
      waitGetToRenderDelayPlot.render(waitGetToRenderDuration);
      waitGetPosesExceptionCountPlot.render(waitGetPosesExceptionCount);
   }

   public void dispose()
   {
      if (contextCreatedNotification != null && contextInitialized)
      {
         contextCreatedNotification = null;
         contextInitialized = false;
         context.dispose();
      }
   }

   public boolean isVRReady()
   {
      // Wait for VR setup to be ready. This is the primary indicator, called only when the headset is connected
      return vrEnabled.get() && contextInitialized && context.getHeadset().isConnected();
   }

   public void process3DViewInput(ImGui3DViewInput input)
   {
      if (isVRReady())
      {
         scenePoseGizmo.process3DViewInput(input);
      }
   }

   public void getVirtualRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      if (vrEnabled.get() && contextInitialized)
      {
         if (!skipHeadset)
         {
            context.getHeadsetRenderable(renderables, pool);
         }
         context.getControllerRenderables(renderables, pool);
         context.getBaseStationRenderables(renderables, pool);
         if (DEBUG_EYE_FRAMES)
         {
            for (RobotSide side : RobotSide.values)
            {
               context.getEyes().get(side).getCoordinateFrameInstance().getRenderables(renderables, pool);
            }
         }
         scenePoseGizmo.getRenderables(renderables, pool);
         teleporter.getRenderables(renderables, pool);
      }
   }

   public GDXVRContext getContext()
   {
      return context;
   }

   public ImBoolean getVREnabled()
   {
      return vrEnabled;
   }
}
