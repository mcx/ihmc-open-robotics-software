package us.ihmc.gdx.simulation.environment;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import imgui.flag.ImGuiMouseButton;
import imgui.internal.ImGui;
import imgui.type.ImFloat;
import imgui.type.ImString;
import us.ihmc.commons.nio.BasicPathVisitor;
import us.ihmc.commons.nio.PathTools;
import us.ihmc.euclid.Axis3D;
import us.ihmc.euclid.geometry.interfaces.Line3DReadOnly;
import us.ihmc.euclid.geometry.tools.EuclidGeometryTools;
import us.ihmc.euclid.tools.EuclidCoreTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.gdx.imgui.ImGuiPanel;
import us.ihmc.gdx.imgui.ImGuiTools;
import us.ihmc.gdx.imgui.ImGuiUniqueLabelMap;
import us.ihmc.gdx.input.ImGui3DViewInput;
import us.ihmc.gdx.sceneManager.GDXSceneLevel;
import us.ihmc.gdx.simulation.environment.object.GDXSCS2EnvironmentObject;
import us.ihmc.gdx.simulation.environment.object.GDXSCS2EnvironmentObjectFactory;
import us.ihmc.gdx.simulation.environment.object.GDXSCS2EnvironmentObjectLibrary;
import us.ihmc.gdx.ui.GDX3DPanel;
import us.ihmc.gdx.ui.GDXImGuiBasedUI;
import us.ihmc.gdx.ui.gizmo.GDXPose3DGizmo;
import us.ihmc.log.LogTools;
import us.ihmc.tools.io.JSONFileTools;
import us.ihmc.tools.io.WorkspacePathTools;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.util.*;

public class GDXSCS2EnvironmentBuilder extends ImGuiPanel
{
   private final static String WINDOW_NAME = ImGuiTools.uniqueLabel(GDXSCS2EnvironmentBuilder.class, "Environment");
   private final ArrayList<GDXSCS2EnvironmentObject> allObjects = new ArrayList<>();
   private final ArrayList<GDXSCS2EnvironmentObject> lightObjects = new ArrayList<>();
   private boolean loadedFilesOnce = false;
   private Path selectedEnvironmentFile = null;
   private final TreeSet<Path> environmentFiles = new TreeSet<>(Comparator.comparing(path -> path.getFileName().toString()));
   private final ImGuiUniqueLabelMap labels = new ImGuiUniqueLabelMap(getClass());
   private final ImString saveString = new ImString("", 100);
   private final Point3D tempTranslation = new Point3D();
   private final Quaternion tempOrientation = new Quaternion();
   private final RigidBodyTransform tempTransform = new RigidBodyTransform();
   private final ImFloat ambientLightAmount = new ImFloat(0.4f);
   private final GDX3DPanel panel3D;
   private boolean isPlacing = false;
   private GDXSCS2EnvironmentObject selectedObject;
   private GDXSCS2EnvironmentObject intersectedObject;
   private final GDXPose3DGizmo pose3DGizmo = new GDXPose3DGizmo();
   private final ImGuiPanel poseGizmoTunerPanel = pose3DGizmo.createTunerPanel(getClass().getSimpleName());
   private final Point3D tempIntersection = new Point3D();

   public GDXSCS2EnvironmentBuilder(GDX3DPanel panel3D)
   {
      super(WINDOW_NAME);
      this.panel3D = panel3D;
      setRenderMethod(this::renderImGuiWidgets);
      addChild(poseGizmoTunerPanel);
   }

   public void create(GDXImGuiBasedUI baseUI)
   {
      // TODO: Implement hiding the real environment to emulate real world operation
      panel3D.getScene().addRenderableProvider(this::getRealRenderables, GDXSceneLevel.MODEL);
      panel3D.getScene().addRenderableProvider(this::getVirtualRenderables, GDXSceneLevel.VIRTUAL);

      pose3DGizmo.create(panel3D.getCamera3D());
      baseUI.getPrimary3DPanel().addImGui3DViewPickCalculator(this::calculate3DViewPick);
      baseUI.getPrimary3DPanel().addImGui3DViewInputProcessor(this::process3DViewInput);
   }

   public void calculate3DViewPick(ImGui3DViewInput input)
   {
      if (selectedObject != null && !isPlacing)
      {
         pose3DGizmo.calculate3DViewPick(input);
      }
   }

   public void process3DViewInput(ImGui3DViewInput viewInput)
   {
      if (selectedObject != null)
      {
         if (isPlacing)
         {
            Line3DReadOnly pickRay = viewInput.getPickRayInWorld();
            Point3D pickPoint = EuclidGeometryTools.intersectionBetweenLine3DAndPlane3D(EuclidCoreTools.origin3D,
                                                                                        Axis3D.Z,
                                                                                        pickRay.getPoint(),
                                                                                        pickRay.getDirection());
            selectedObject.setPositionInWorld(pickPoint);
            pose3DGizmo.getTransformToParent().set(selectedObject.getObjectTransform());

            if (viewInput.isWindowHovered() && viewInput.mouseReleasedWithoutDrag(ImGuiMouseButton.Left))
            {
               isPlacing = false;
            }
         }
         else
         {
            pose3DGizmo.process3DViewInput(viewInput);
            selectedObject.setTransformToWorld(pose3DGizmo.getTransformToParent());

            intersectedObject = calculatePickedObject(viewInput.getPickRayInWorld());
            if (viewInput.isWindowHovered() && viewInput.mouseReleasedWithoutDrag(ImGuiMouseButton.Left))
            {
               if (intersectedObject != selectedObject)
               {
                  updateObjectSelected(selectedObject, intersectedObject);
                  if (selectedObject != null)
                  {
                     pose3DGizmo.getTransformToParent().set(selectedObject.getObjectTransform());
                  }
               }
            }
         }
      }
      else
      {
         isPlacing = false;
         if (viewInput.isWindowHovered())
         {
            intersectedObject = calculatePickedObject(viewInput.getPickRayInWorld());

            if (intersectedObject != null && viewInput.mouseReleasedWithoutDrag(ImGuiMouseButton.Left))
            {
               updateObjectSelected(selectedObject, intersectedObject);
               pose3DGizmo.getTransformToParent().set(selectedObject.getObjectTransform());
            }
         }
      }
   }

   private GDXSCS2EnvironmentObject calculatePickedObject(Line3DReadOnly pickRay)
   {
      double closestDistance = Double.POSITIVE_INFINITY;
      GDXSCS2EnvironmentObject closestObject = null;
      for (GDXSCS2EnvironmentObject object : allObjects)
      {
         boolean intersects = object.intersect(pickRay, tempIntersection);
         double distance = tempIntersection.distance(pickRay.getPoint());
         if (intersects && (closestObject == null || distance < closestDistance))
         {
            closestObject = object;
            closestDistance = distance;

         }
      }
      return closestObject;
   }

   public void resetSelection()
   {
      updateObjectSelected(selectedObject, null);
      intersectedObject = null;
   }

   public void update()
   {

   }

   public void renderImGuiWidgets()
   {
      ImGui.separator();
      if (ImGui.sliderFloat("Ambient light", ambientLightAmount.getData(), 0.0f, 1.0f))
      {
         panel3D.getScene().setAmbientLight(ambientLightAmount.get());
      }

      ImGui.separator();
      ImGui.text("Selected: " + (selectedObject == null ? "" : (selectedObject.getTitleCasedName() + " " + selectedObject.getObjectIndex())));
      ImGui.text("Intersected: " + (intersectedObject == null ? "" : (intersectedObject.getTitleCasedName() + " " + intersectedObject.getObjectIndex())));

      // TODO: Place robots
      if (!isPlacing)
      {
         for (GDXSCS2EnvironmentObjectFactory objectFactory : GDXSCS2EnvironmentObjectLibrary.getObjectFactories())
         {
            if (ImGui.button(labels.get("Place " + objectFactory.getName())))
            {
               GDXSCS2EnvironmentObject objectToPlace = objectFactory.getSupplier().get();
               addObject(objectToPlace);
               updateObjectSelected(selectedObject, objectToPlace);
               isPlacing = true;
            }
         }

         ImGui.separator();
      }
      if (selectedObject != null && (ImGui.button("Delete selected") || ImGui.isKeyReleased(ImGuiTools.getDeleteKey())))
      {
         removeObject(selectedObject);
         resetSelection();
      }

      ImGui.text("Environments:");
      if (!loadedFilesOnce && selectedEnvironmentFile != null)
      {
         loadEnvironment(selectedEnvironmentFile);
      }
      boolean reindexClicked = ImGui.button(ImGuiTools.uniqueLabel(this, "Reindex scripts"));
      if (!loadedFilesOnce || reindexClicked)
      {
         loadedFilesOnce = true;
         reindexScripts();
      }
      String fileNameToSave = null;
      for (Path environmentFile : environmentFiles)
      {
         if (ImGui.radioButton(environmentFile.getFileName().toString(), selectedEnvironmentFile != null && selectedEnvironmentFile.equals(environmentFile)))
         {
            loadEnvironment(environmentFile);
         }
         if (selectedEnvironmentFile != null && selectedEnvironmentFile.equals(environmentFile))
         {
            ImGui.sameLine();
            if (ImGui.button("Save"))
            {
               fileNameToSave = environmentFile.getFileName().toString();
            }
         }
      }
      ImGuiTools.inputText("###saveText", saveString);
      ImGui.sameLine();
      if (ImGui.button("Save as"))
      {
         fileNameToSave = saveString.get();
      }
      if (fileNameToSave != null)
      {
         JSONFileTools.saveToClasspath("ihmc-open-robotics-software",
                                       "ihmc-high-level-behaviors/src/libgdx/resources",
                                       "scs2Environments/" + fileNameToSave,
         rootNode ->
         {
            rootNode.put("ambientLight", ambientLightAmount.get());
            ArrayNode objectsArrayNode = rootNode.putArray("objects");
            for (GDXSCS2EnvironmentObject object : allObjects)
            {
               ObjectNode objectNode = objectsArrayNode.addObject();
               objectNode.put("type", object.getClass().getSimpleName());
               tempTransform.set(object.getObjectTransform());
               tempTranslation.set(tempTransform.getTranslation());
               tempOrientation.set(tempTransform.getRotation());
               objectNode.put("x", tempTranslation.getX());
               objectNode.put("y", tempTranslation.getY());
               objectNode.put("z", tempTranslation.getZ());
               objectNode.put("qx", tempOrientation.getX());
               objectNode.put("qy", tempOrientation.getY());
               objectNode.put("qz", tempOrientation.getZ());
               objectNode.put("qs", tempOrientation.getS());
            }
         });
         reindexScripts();
      }

      ImGui.checkbox("Show 3D Widget Tuner", poseGizmoTunerPanel.getIsShowing());
   }

   private void loadEnvironment(Path environmentFile)
   {
      loadedFilesOnce = true;
      selectedEnvironmentFile = environmentFile;
      for (GDXSCS2EnvironmentObject object : allObjects.toArray(new GDXSCS2EnvironmentObject[0]))
      {
         removeObject(object);
      }

      resetSelection();

      JSONFileTools.loadFromWorkspace("ihmc-open-robotics-software",
                                      "ihmc-high-level-behaviors/src/libgdx/resources",
                                      "scs2Environments/" + environmentFile.getFileName().toString(),
      node ->
      {
         JsonNode ambientLightNode = node.get("ambientLight");
         if (ambientLightNode != null)
         {
            float ambientValue = (float) ambientLightNode.asDouble();
            ambientLightAmount.set(ambientValue);
            panel3D.getScene().setAmbientLight(ambientLightAmount.get());
         }
         for (Iterator<JsonNode> it = node.withArray("objects").elements(); it.hasNext(); )
         {
            JsonNode objectNode = it.next();
            String objectTypeName = objectNode.get("type").asText();
            GDXSCS2EnvironmentObject object = GDXSCS2EnvironmentObjectLibrary.loadBySimpleClassName(objectTypeName);

            if (object != null)
            {
               tempTranslation.setX(objectNode.get("x").asDouble());
               tempTranslation.setY(objectNode.get("y").asDouble());
               tempTranslation.setZ(objectNode.get("z").asDouble());
               tempOrientation.set(objectNode.get("qx").asDouble(),
                                   objectNode.get("qy").asDouble(),
                                   objectNode.get("qz").asDouble(),
                                   objectNode.get("qs").asDouble());
               tempTransform.set(tempOrientation, tempTranslation);
               object.setTransformToWorld(tempTransform);
               addObject(object);
            }
            else
            {
               LogTools.warn("Skipping loading object: {}", objectTypeName);
            }
         }
      });
   }

   public void loadEnvironment(String environmentFileName)
   {
      reindexScripts();
      Optional<Path> match = environmentFiles.stream().filter(path -> path.getFileName().toString().equals(environmentFileName)).findFirst();
      if (match.isPresent())
      {
         loadEnvironment(match.get());
      }
      else
      {
         LogTools.error("Could not find environment file: {}", environmentFileName);
      }
   }

   public void updateObjectSelected(GDXSCS2EnvironmentObject from, GDXSCS2EnvironmentObject to)
   {
      if (from != to)
      {
         if (from != null)
            from.setSelected(false);

         if (to != null)
            to.setSelected(true);

         selectedObject = to;
      }
   }

   public void addObject(GDXSCS2EnvironmentObject environmentObject)
   {
      allObjects.add(environmentObject);

//      if (environmentObject instanceof GDXPointLightObject)
//      {
//         GDXPointLightObject pointLightObject = (GDXPointLightObject) environmentObject;
//         sceneManager.getscene().addPointLight(pointLightObject.getLight());
//         lightObjects.add(pointLightObject);
//      }
//      else if (environmentObject instanceof GDXDirectionalLightObject)
//      {
//         GDXDirectionalLightObject directionalLightObject = (GDXDirectionalLightObject) environmentObject;
//         sceneManager.getscene().addDirectionalLight(directionalLightObject.getLight());
//         lightObjects.add(directionalLightObject);
//      }
   }

   public void removeObject(GDXSCS2EnvironmentObject environmentObject)
   {
      allObjects.remove(environmentObject);

//      if (environmentObject instanceof GDXPointLightObject)
//      {
//         GDXPointLightObject lightObject = (GDXPointLightObject) environmentObject;
//         sceneManager.getscene().removePointLight(lightObject.getLight());
//         lightObjects.remove(environmentObject);
//      }
//      else if (environmentObject instanceof GDXDirectionalLightObject)
//      {
//         GDXDirectionalLightObject lightObject = (GDXDirectionalLightObject) environmentObject;
//         sceneManager.getscene().removeDirectionalLight(lightObject.getLight());
//         lightObjects.remove(environmentObject);
//      }
   }

   private void reindexScripts()
   {
      Path scriptsPath = WorkspacePathTools.findPathToResource("ihmc-open-robotics-software",
                                                               "ihmc-high-level-behaviors/src/libgdx/resources",
                                                               "scs2Environments");
      environmentFiles.clear();
      PathTools.walkFlat(scriptsPath, (path, pathType) ->
      {
         if (pathType == BasicPathVisitor.PathType.FILE)
         {
            environmentFiles.add(path);
         }
         return FileVisitResult.CONTINUE;
      });
   }

   public void getRealRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      for (GDXSCS2EnvironmentObject object : allObjects)
      {
//         if (!(object instanceof GDXPointLightObject) && !(object instanceof GDXDirectionalLightObject))
            object.getRealRenderables(renderables, pool);
      }
   }

   public void getVirtualRenderables(Array<Renderable> renderables, Pool<Renderable> pool)
   {
      for (GDXSCS2EnvironmentObject object : lightObjects)
      {
         object.getRealRenderables(renderables, pool);
      }
      if (selectedObject != null)
      {
         selectedObject.getCollisionMeshRenderables(renderables, pool);
         pose3DGizmo.getRenderables(renderables, pool);
      }
      if (intersectedObject != null && intersectedObject != selectedObject)
      {
         intersectedObject.getCollisionMeshRenderables(renderables, pool);
      }
   }

   public void destroy()
   {

   }

   public String getWindowName()
   {
      return WINDOW_NAME;
   }

   public ArrayList<GDXSCS2EnvironmentObject> getAllObjects()
   {
      return allObjects;
   }
}
