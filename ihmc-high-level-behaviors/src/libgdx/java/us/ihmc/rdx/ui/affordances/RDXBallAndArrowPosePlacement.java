package us.ihmc.rdx.ui.affordances;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.internal.ImGui;
import imgui.internal.flag.ImGuiItemFlags;
import org.lwjgl.openvr.InputDigitalActionData;
import us.ihmc.avatar.drcRobot.ROS2SyncedRobotModel;
import us.ihmc.behaviors.tools.BehaviorTools;
import us.ihmc.commons.thread.Notification;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple3D.Point3D32;
import us.ihmc.euclid.tuple3D.Vector3D32;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.rdx.imgui.ImGuiLabelMap;
import us.ihmc.rdx.input.ImGui3DViewInput;
import us.ihmc.rdx.input.editor.RDXUIActionMap;
import us.ihmc.rdx.input.editor.RDXUITrigger;
import us.ihmc.rdx.tools.LibGDXTools;
import us.ihmc.rdx.tools.RDXIconTexture;
import us.ihmc.rdx.tools.RDXModelBuilder;
import us.ihmc.rdx.ui.graphics.RDXFootstepGraphic;
import us.ihmc.rdx.vr.RDXVRManager;
import us.ihmc.robotics.referenceFrames.ReferenceFrameMissingTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SegmentDependentList;

import java.util.ArrayList;
import java.util.function.Consumer;

public class RDXBallAndArrowPosePlacement implements RenderableProvider
{
   private final static Pose3D NaN_POSE = BehaviorTools.createNaNPose();
   private final ImGuiLabelMap labels = new ImGuiLabelMap();
   private ModelInstance sphere;
   private ModelInstance arrow;
   private ROS2SyncedRobotModel syncedRobot;
   private RDXFootstepGraphic leftGoalFootstepGraphic;
   private RDXFootstepGraphic rightGoalFootstepGraphic;
   private final FramePose3D leftFootstepGoalPose = new FramePose3D();
   private final FramePose3D rightFootstepGoalPose = new FramePose3D();
   private final RigidBodyTransform goalToWorldTransform = new RigidBodyTransform();
   private final ReferenceFrame goalPoseFrame = ReferenceFrameMissingTools.constructFrameWithChangingTransformToParent(null, null);
   private double halfIdealFootstepWidth;
   private RDXUIActionMap placeGoalActionMap;
   private boolean placingGoal = false;
   private boolean placingPosition = true;
   private final Pose3D goalPoseForReading = new Pose3D();
   private final Point3D32 tempSpherePosition = new Point3D32();
   private final Vector3D32 tempRotationVector = new Vector3D32();
   private final RigidBodyTransform tempTransform = new RigidBodyTransform();
   private final RotationMatrix arrowRotationMatrix = new RotationMatrix();
   private Consumer<Pose3D> placedPoseConsumer;
   private final Notification placedNotification = new Notification();
   private RDXIconTexture locationFlagIcon;
   private Runnable onStartPositionPlacement;
   private Runnable onEndPositionPlacement;

   public void create(Color color, ROS2SyncedRobotModel syncedRobot)
   {
      create(null, color, syncedRobot);
   }

   public void create(Consumer<Pose3D> placedPoseConsumer, Color color, ROS2SyncedRobotModel syncedRobot)
   {
      this.syncedRobot = syncedRobot;
      this.placedPoseConsumer = placedPoseConsumer;
      float sphereRadius = 0.03f;
      sphere = RDXModelBuilder.createSphere(sphereRadius, color);
      arrow = RDXModelBuilder.createArrow(sphereRadius * 6.0, color);

      SegmentDependentList<RobotSide, ArrayList<Point2D>> contactPoints = syncedRobot.getRobotModel().getContactPointParameters().getControllerFootGroundContactPoints();
      leftGoalFootstepGraphic = new RDXFootstepGraphic(contactPoints, RobotSide.LEFT);
      rightGoalFootstepGraphic = new RDXFootstepGraphic(contactPoints, RobotSide.RIGHT);
      leftGoalFootstepGraphic.create();
      rightGoalFootstepGraphic.create();

      halfIdealFootstepWidth = syncedRobot.getRobotModel().getFootstepPlannerParameters().getIdealFootstepWidth() / 2.0;

      placeGoalActionMap = new RDXUIActionMap(startAction ->
      {
         placingGoal = true;
         placingPosition = true;

         if (onStartPositionPlacement != null)
            onStartPositionPlacement.run();
      });
      placeGoalActionMap.mapAction(RDXUITrigger.POSITION_LEFT_CLICK, trigger ->
      {
         placingPosition = false;
         if (onEndPositionPlacement != null)
            onEndPositionPlacement.run();
      });
      placeGoalActionMap.mapAction(RDXUITrigger.ORIENTATION_LEFT_CLICK, trigger ->
      {
         onPlaced();

         placingGoal = false;
      });
      placeGoalActionMap.mapAction(RDXUITrigger.RIGHT_CLICK, trigger ->
      {
         placingGoal = false;

         if (placingPosition && onEndPositionPlacement != null)
            onEndPositionPlacement.run();
      });

      clear();

      locationFlagIcon = new RDXIconTexture("icons/locationFlag.png");
   }

   public void processImGui3DViewInput(ImGui3DViewInput input)
   {
      if (placingGoal && input.isWindowHovered())
      {
         Point3DReadOnly pickPointInWorld = input.getPickPointInWorld();

         if (placingPosition)
         {
            sphere.transform.setTranslation(pickPointInWorld.getX32(), pickPointInWorld.getY32(), pickPointInWorld.getZ32());
            LibGDXTools.toEuclid(sphere.transform, tempSpherePosition);

            // Find vector from mid-feet z up frame to current sphere location
            goalPoseForReading.set(syncedRobot.getReferenceFrames().getMidFeetZUpFrame().getTransformToWorldFrame());
            tempRotationVector.set(tempSpherePosition);
            tempRotationVector.sub(goalPoseForReading.getPosition());
            double yaw = Math.atan2(tempRotationVector.getY(), tempRotationVector.getX());

            goalPoseForReading.setRotationYawAndZeroTranslation(yaw);
            goalPoseForReading.prependTranslation(tempSpherePosition);

            updateGoalFootstepGraphics(goalPoseForReading);

            if (input.mouseReleasedWithoutDrag(ImGuiMouseButton.Left))
            {
               placeGoalActionMap.triggerAction(RDXUITrigger.POSITION_LEFT_CLICK);
            }
         }
         else // placing orientation
         {
            LibGDXTools.toEuclid(sphere.transform, tempSpherePosition);
            LibGDXTools.toLibGDX(tempSpherePosition, arrow.transform);

            tempRotationVector.set(pickPointInWorld);
            tempRotationVector.sub(tempSpherePosition);

            double yaw = Math.atan2(tempRotationVector.getY(), tempRotationVector.getX());
            arrowRotationMatrix.setToYawOrientation(yaw);
            LibGDXTools.toLibGDX(arrowRotationMatrix, arrow.transform);

            goalPoseForReading.set(tempSpherePosition, arrowRotationMatrix);

            updateGoalFootstepGraphics(goalPoseForReading);

            if (input.mouseReleasedWithoutDrag(ImGuiMouseButton.Left))
            {
               placeGoalActionMap.triggerAction(RDXUITrigger.ORIENTATION_LEFT_CLICK);
            }
         }

         if (input.mouseReleasedWithoutDrag(ImGuiMouseButton.Right))
         {
            placeGoalActionMap.triggerAction(RDXUITrigger.RIGHT_CLICK);
         }
      }
   }

   public void handleVREvents(RDXVRManager vrManager)
   {
      vrManager.getContext().getController(RobotSide.LEFT).runIfConnected(controller ->
      {
         InputDigitalActionData triggerClick = controller.getClickTriggerActionData();
         if (triggerClick.bChanged() && triggerClick.bState())
         {
            placingGoal = true;
         }
         if (triggerClick.bChanged() && !triggerClick.bState())
         {
            placingGoal = false;
            onPlaced();
         }

         controller.getTransformZUpToWorld(sphere.transform);
         controller.getTransformZUpToWorld(arrow.transform);
      });
   }

   private void onPlaced()
   {
      if (placedPoseConsumer != null)
         placedPoseConsumer.accept(goalPoseForReading);

      placedNotification.set();
   }

   private void updateGoalFootstepGraphics(Pose3DReadOnly goalPose)
   {
      goalPose.get(goalToWorldTransform);
      goalPoseFrame.update();

      leftFootstepGoalPose.setToZero(goalPoseFrame);
      leftFootstepGoalPose.getPosition().addY(halfIdealFootstepWidth);
      leftFootstepGoalPose.changeFrame(ReferenceFrame.getWorldFrame());
      leftGoalFootstepGraphic.setPose(leftFootstepGoalPose);

      rightFootstepGoalPose.setToZero(goalPoseFrame);
      rightFootstepGoalPose.getPosition().subY(halfIdealFootstepWidth);
      rightFootstepGoalPose.changeFrame(ReferenceFrame.getWorldFrame());
      rightGoalFootstepGraphic.setPose(rightFootstepGoalPose);
   }

   public boolean renderPlaceGoalButton()
   {
      boolean placementStarted = false;
      if (locationFlagIcon != null)
      {
         ImGui.image(locationFlagIcon.getTexture().getTextureObjectHandle(), 22.0f, 22.0f);
         ImGui.sameLine();
      }
      boolean pushedFlags = false;
      if (placingGoal)
      {
         ImGui.pushItemFlag(ImGuiItemFlags.Disabled, true);
         ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.6f);
         pushedFlags = true;
      }
      if (ImGui.button(labels.get(pushedFlags ? "Placing" : "Place goal")))
      {
         placementStarted = true;
         placeGoalActionMap.start();
      }
      if (pushedFlags)
      {
         ImGui.popItemFlag();
         ImGui.popStyleVar();
      }
      if (ImGui.isItemHovered())
      {
         ImGui.setTooltip("Hold Ctrl and scroll the mouse wheel while placing to adjust Z.");
      }
      ImGui.sameLine();
      ImGui.beginDisabled(!isPlaced());
      if (ImGui.button(labels.get("Clear")))
      {
         clear();
      }
      ImGui.endDisabled();

      return placementStarted;
   }

   @Override
   public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      if (isPlaced())
      {
         sphere.getRenderables(renderables, pool);
         arrow.getRenderables(renderables, pool);
         leftGoalFootstepGraphic.getRenderables(renderables, pool);
         rightGoalFootstepGraphic.getRenderables(renderables, pool);
      }
   }

   public boolean isPlaced()
   {
      return !Float.isNaN(sphere.transform.val[Matrix4.M03]);
   }

   public boolean isPlacingGoal()
   {
      return placingGoal;
   }

   public void clear()
   {
      placingGoal = false;
      placingPosition = true;
      if (sphere != null)
         sphere.transform.val[Matrix4.M03] = Float.NaN;
      if (arrow != null)
         arrow.transform.val[Matrix4.M03] = Float.NaN;
      leftGoalFootstepGraphic.setPose(NaN_POSE);
      rightGoalFootstepGraphic.setPose(NaN_POSE);
   }

   public Pose3DReadOnly getGoalPose()
   {
      return goalPoseForReading;
   }

   public void setGoalPoseAndPassOn(Pose3DReadOnly pose)
   {
      setGoalPoseNoCallbacks(pose);
      onPlaced();
   }

   public void setGoalPoseNoCallbacks(Pose3DReadOnly pose)
   {
      if (pose == null)
      {
         clear();
      }
      else
      {
         LibGDXTools.toLibGDX(pose.getPosition(), sphere.transform);
         LibGDXTools.toLibGDX(pose, tempTransform, arrow.transform);
      }
      goalPoseForReading.set(pose);
   }

   public void setOnStartPositionPlacement(Runnable onStartPositionPlacement)
   {
      this.onStartPositionPlacement = onStartPositionPlacement;
   }

   public void setOnEndPositionPlacement(Runnable onEndPositionPlacement)
   {
      this.onEndPositionPlacement = onEndPositionPlacement;
   }

   public Notification getPlacedNotification()
   {
      return placedNotification;
   }
}
