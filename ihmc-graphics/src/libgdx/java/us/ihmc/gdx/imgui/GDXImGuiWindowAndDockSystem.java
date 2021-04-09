package us.ihmc.gdx.imgui;

import imgui.ImFont;
import imgui.ImGuiIO;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiDockNodeFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.internal.ImGui;
import org.lwjgl.glfw.GLFWErrorCallback;
import us.ihmc.log.LogTools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.glfw.GLFW.*;

public class GDXImGuiWindowAndDockSystem
{
   private Path imGuiSettingsPath;
   private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
   private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

   private boolean isFirstRenderCall = true;
   private String glslVersion; // TODO: ?
   private long windowHandle;
   private ImFont imFont;
   private int dockspaceId;

   public void create(long windowHandle, String windowTitle)
   {
      this.windowHandle = windowHandle;
      imGuiSettingsPath = Paths.get(System.getProperty("user.home"), ".ihmc/" + windowTitle.replaceAll(" ", "") + "ImGuiSettings.ini").toAbsolutePath().normalize();

      GLFWErrorCallback.createPrint(System.err).set();

      if (!glfwInit())
      {
         throw new IllegalStateException("Unable to initialize GLFW");
      }
//               glfwDefaultWindowHints();
//               if (SystemUtils.IS_OS_MAC) {
//                  glslVersion = "#version 150";
//                  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
//                  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
//                  glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);  // 3.2+ only
//                  glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL20.GL_TRUE);            // Required on Mac
//               } else {
//                  glslVersion = "#version 130";
//                  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
//                  glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0);
//               }
//
//               GL.createCapabilities();

      ImGui.createContext();

      final ImGuiIO io = ImGui.getIO();
      io.setIniFilename(null); // We don't want to save .ini file
//               io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
      io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
      io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);
      io.setConfigViewportsNoTaskBarIcon(true);
      io.setConfigWindowsMoveFromTitleBarOnly(true);

      if (!Boolean.parseBoolean(System.getProperty("imgui.dark")))
         ImGui.styleColorsLight();
      imFont = ImGuiTools.setupFonts(io);

      // When viewports are enabled we tweak WindowRounding/WindowBg so platform windows can look identical to regular ones.
      if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable))
      {
         final ImGuiStyle style = imgui.ImGui.getStyle();
         style.setWindowRounding(0.0f);
         style.setColor(ImGuiCol.WindowBg, imgui.ImGui.getColorU32(ImGuiCol.WindowBg, 1));
      }

      imGuiGlfw.init(windowHandle, true);
      imGuiGl3.init(glslVersion);
   }

   public void beforeWindowManagement()
   {
      if (isFirstRenderCall && Files.exists(imGuiSettingsPath))
      {
         String settingsPath = imGuiSettingsPath.toString();
         LogTools.info("Loading ImGui settings from {}", settingsPath);
         ImGui.loadIniSettingsFromDisk(settingsPath);
      }

      imGuiGlfw.newFrame();
      ImGui.newFrame();

      ImGui.pushFont(imFont);

      int flags = ImGuiDockNodeFlags.None;
      flags += ImGuiDockNodeFlags.PassthruCentralNode;
//      flags += ImGuiDockNodeFlags.AutoHideTabBar;
      dockspaceId = ImGui.dockSpaceOverViewport(ImGui.getMainViewport(), flags);

   }

   public void afterWindowManagement()
   {
      ImGui.popFont();

      ImGui.render();
      imGuiGl3.renderDrawData(ImGui.getDrawData());

      if (imgui.ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
         final long backupWindowPtr = glfwGetCurrentContext();
         imgui.ImGui.updatePlatformWindows();
         imgui.ImGui.renderPlatformWindowsDefault();
         glfwMakeContextCurrent(backupWindowPtr);
      }

      glfwSwapBuffers(windowHandle);
      glfwPollEvents();

      isFirstRenderCall = false;
   }

   public void dispose()
   {
      imGuiGl3.dispose();
      imGuiGlfw.dispose();

      ImGui.destroyContext();
   }

   public int getCentralDockspaceId()
   {
      return dockspaceId;
   }

   public boolean isFirstRenderCall()
   {
      return isFirstRenderCall;
   }

   public ImGuiImplGl3 getImGuiGl3()
   {
      return imGuiGl3;
   }

   public Path getImGuiSettingsPath()
   {
      return imGuiSettingsPath;
   }
}