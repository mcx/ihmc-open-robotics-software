package us.ihmc.commonWalkingControlModules.controlModules.foot.toeOff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.referenceFrame.FrameConvexPolygon2D;
import us.ihmc.euclid.referenceFrame.FramePoint2D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPolygon;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.gui.tools.SimulationOverheadPlotterFactory;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFrameConvexPolygon2D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint2D;
import us.ihmc.yoVariables.listener.YoVariableChangedListener;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoVariable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static us.ihmc.commonWalkingControlModules.desiredFootStep.FootstepListVisualizer.defaultFeetColors;

public class DynamicStateInspectorTest
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private static final double length = 0.2;
   private static final double width = 0.1;

   private FrameConvexPolygon2D leftPolygon;
   private FrameConvexPolygon2D rightPolygon;
   private FrameConvexPolygon2D onToesPolygon;
   private YoRegistry registry;
   private FramePoint2D toePosition;
   private FramePoint2D desiredICP;

   @BeforeEach
   public void setup()
   {
      registry = new YoRegistry("test");

      leftPolygon = createFootPolygon(length, width);
      rightPolygon = createFootPolygon(length, width);
      onToesPolygon = new FrameConvexPolygon2D();

      toePosition = new FramePoint2D();
   }

   @AfterEach
   public void tearDown()
   {
      leftPolygon = null;
      rightPolygon = null;
   }

   @Test
   public void testLeftStepGrid()
   {
      DynamicStateInspector inspector = new DynamicStateInspector(registry);

      FramePose3D leftFootPose = new FramePose3D();
      FramePose3D rightFootPose = new FramePose3D();

      double stepLength = 0.3;
      double stepWidth = 0.2;

      leftFootPose.getPosition().set(stepLength, 0.5 * stepWidth, 0.0);
      rightFootPose.getPosition().set(0.0, -0.5 * stepWidth, 0.0);

      leftPolygon.translate(leftFootPose.getX(), leftFootPose.getY());
      rightPolygon.translate(rightFootPose.getX(), rightFootPose.getY());

      toePosition.setIncludingFrame(rightFootPose.getPosition());
      toePosition.addX(0.5 * length);

      onToesPolygon.addVertices(leftPolygon);
      onToesPolygon.addVertex(toePosition);
      onToesPolygon.update();

      inspector.setPolygons(leftPolygon, rightPolygon, onToesPolygon);

      desiredICP = new FramePoint2D();
      desiredICP.interpolate(new FramePoint2D(rightFootPose.getPosition()), new FramePoint2D(leftFootPose.getPosition()), 0.75);

      visualize(inspector, stepLength, stepWidth, leftFootPose);
   }

   private static FrameConvexPolygon2D createFootPolygon(double length, double width)
   {
      FrameConvexPolygon2D polygon2D = new FrameConvexPolygon2D();
      polygon2D.addVertex(0.5 * length, 0.5 * width);
      polygon2D.addVertex(0.5 * length, -0.5 * width);
      polygon2D.addVertex(-0.5 * length, -0.5 * width);
      polygon2D.addVertex(-0.5 * length, 0.5 * width);
      polygon2D.update();

      return polygon2D;
   }

   private void visualize(DynamicStateInspector inspector, double stepLength, double stepWidth, FramePose3D leadingFootPose)
   {
      SimulationConstructionSet scs = new SimulationConstructionSet(new Robot("dummy"));

      YoGraphicsListRegistry graphicsListRegistry = new YoGraphicsListRegistry();

      YoFrameConvexPolygon2D yoToeOffPolygonViz = new YoFrameConvexPolygon2D("toeOffPolygon", "", worldFrame, 8, registry);
      YoFrameConvexPolygon2D yoSupportPolygonViz = new YoFrameConvexPolygon2D("combinedPolygon", "", worldFrame, 8, registry);
      YoFrameConvexPolygon2D yoLeftFootPolygonViz = new YoFrameConvexPolygon2D("LeftFootPolygon", "", worldFrame, 4, registry);
      YoFrameConvexPolygon2D yoRightFootPolygonViz = new YoFrameConvexPolygon2D("RightFootPolygon", "", worldFrame, 4, registry);
      YoFramePoint2D yoDesiredICP = new YoFramePoint2D("desiredICP", worldFrame, registry);

      YoArtifactPolygon leftFootViz = new YoArtifactPolygon("Left Foot Polygon", yoLeftFootPolygonViz, defaultFeetColors.get(RobotSide.LEFT), false);
      YoArtifactPolygon rightFootViz = new YoArtifactPolygon("Right Foot Polygon", yoRightFootPolygonViz, defaultFeetColors.get(RobotSide.RIGHT), false);
      YoArtifactPolygon supportPolygonArtifact = new YoArtifactPolygon("Combined Polygon", yoSupportPolygonViz, Color.pink, false);
      YoArtifactPolygon toeOffPolygonArtifact = new YoArtifactPolygon("Toe Off Polygon", yoToeOffPolygonViz, Color.RED, false);
      YoGraphicPosition desiredICPArtifact = new YoGraphicPosition("Desired ICP", yoDesiredICP, 0.02, YoAppearance.Blue(), YoGraphicPosition.GraphicType.BALL_WITH_CROSS);

      graphicsListRegistry.registerArtifact("test", leftFootViz);
      graphicsListRegistry.registerArtifact("test", rightFootViz);
      graphicsListRegistry.registerArtifact("test", supportPolygonArtifact);
      graphicsListRegistry.registerArtifact("test", toeOffPolygonArtifact);
      graphicsListRegistry.registerArtifact("test", desiredICPArtifact.createArtifact());

      yoLeftFootPolygonViz.set(leftPolygon);
      yoRightFootPolygonViz.set(rightPolygon);
      yoSupportPolygonViz.addVertices(yoLeftFootPolygonViz);
      yoSupportPolygonViz.addVertices(yoRightFootPolygonViz);
      yoSupportPolygonViz.update();
      yoToeOffPolygonViz.set(onToesPolygon);
      yoDesiredICP.set(desiredICP);


      double gridWidth = 0.5;
      double gridHeight = 0.5;
      double gridRez = 0.025;
      int widthTicks = (int) (gridWidth / gridRez);
      int heightTicks = (int) (gridHeight / gridRez);
      int points = widthTicks * heightTicks;

      List<YoFramePoint2D> validPoints = new ArrayList<>();
      List<YoFramePoint2D> invalidPoints = new ArrayList<>();

      for (int i = 0; i < points; i++)
      {
         YoFramePoint2D validPoint = new YoFramePoint2D("validPoint" + i, worldFrame, registry);
         YoFramePoint2D invalidPoint = new YoFramePoint2D("invalidPoint" + i, worldFrame, registry);
         validPoint.setToNaN();
         invalidPoint.setToNaN();
         validPoints.add(validPoint);
         invalidPoints.add(invalidPoint);

         validPoint.setToNaN();
         invalidPoint.setToNaN();

         YoGraphicPosition validViz = new YoGraphicPosition("valid point" + i, validPoint, gridRez / 5, YoAppearance.Yellow(), YoGraphicPosition.GraphicType.SOLID_BALL);
         YoGraphicPosition invalidViz = new YoGraphicPosition("invalid point" + i, invalidPoint, gridRez / 5, YoAppearance.Red(), YoGraphicPosition.GraphicType.SOLID_BALL);

         graphicsListRegistry.registerArtifact("test", validViz.createArtifact());
         graphicsListRegistry.registerArtifact("test", invalidViz.createArtifact());
      }


      scs.getRootRegistry().addChild(registry);
      scs.addYoGraphicsListRegistry(graphicsListRegistry);
      SimulationOverheadPlotterFactory plotterFactory = scs.createSimulationOverheadPlotterFactory();
      plotterFactory.addYoGraphicsListRegistries(graphicsListRegistry);
      plotterFactory.createOverheadPlotter();

      scs.startOnAThread();

      YoVariableChangedListener changedListener = v -> updateListener(stepLength, inspector, leadingFootPose, validPoints, invalidPoints, scs);
      inspector.attachParameterChangeListener(changedListener);

      updateListener(stepLength, inspector, leadingFootPose, validPoints, invalidPoints, scs);





      scs.tickAndUpdate();

      ThreadTools.sleepForever();
   }

   private void updateListener(double stepLength, DynamicStateInspector inspector, FramePose3D leadingFootPose, List<YoFramePoint2D> validPoints,
                               List<YoFramePoint2D> invalidPoints, SimulationConstructionSet scs)
   {
      double gridWidth = 0.5;
      double gridHeight = 0.5;
      double gridRez = 0.025;
      int widthTicks = (int) (gridWidth / gridRez);
      int heightTicks = (int) (gridHeight / gridRez);
      int points = widthTicks * heightTicks;


      double topLeftX = 0.5 * stepLength + 0.5 * gridHeight;
      double topLeftY = 0.5 * gridWidth;

      int currentValidIdx = 0;
      int currentInvalidIdx = 0;

      for (int widthIdx = 0; widthIdx < widthTicks; widthIdx++)

      {
         for (int heightIdx = 0; heightIdx < heightTicks; heightIdx++)
         {
            FramePoint2D currentICP = new FramePoint2D(worldFrame, topLeftX - heightIdx * gridRez, topLeftY - widthIdx * gridRez);
            inspector.checkICPLocations(RobotSide.RIGHT, leadingFootPose, desiredICP, currentICP, toePosition);

            if (inspector.areDynamicsOkForToeOff())
            {
               validPoints.get(currentValidIdx).set(currentICP);
               currentValidIdx++;
            }
            else
            {
               invalidPoints.get(currentInvalidIdx).set(currentICP);
               currentInvalidIdx++;
            }
         }
      }
            for (int i = currentValidIdx; i < validPoints.size(); i++)
               validPoints.get(i).setToNaN();
            for (int i = currentInvalidIdx; i < invalidPoints.size(); i++)
               invalidPoints.get(i).setToNaN();

      scs.tickAndUpdate();
   }
}
