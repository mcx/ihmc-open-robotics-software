package us.ihmc.rdx.ui.missionControl;

import imgui.ImGui;
import mission_control_msgs.msg.dds.SystemServiceActionMessage;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.rdx.imgui.ImGuiPanel;
import us.ihmc.rdx.imgui.ImGuiTools;
import us.ihmc.ros2.ROS2Node;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ImGuiMachineService
{
   private final String serviceName;
   private final String hostname;
   private final UUID instanceId;
   @Nullable
   private String status;
   private final ImGuiPanel logPanel;
   private final ImGuiConsoleArea consoleArea;
   private IHMCROS2Publisher<SystemServiceActionMessage> serviceActionPublisher;

   // Time of the last action button press
   private long lastActionRequest = -1;
   // If we've clicked an action button, i.e. "Start" and we're expecting a change sometime in the near future
   private boolean waitingOnStatusChange = false;

   public ImGuiMachineService(String serviceName, String hostname, UUID instanceId, ImGuiPanel machinePanel, ROS2Node ros2Node)
   {
      this.serviceName = serviceName;
      this.hostname = hostname;
      this.instanceId = instanceId;
      logPanel = new ImGuiPanel(serviceName + " Log##" + instanceId, this::renderImGuiLogPanelWidgets);
      machinePanel.addChild(logPanel);
      consoleArea = new ImGuiConsoleArea();
      ThreadTools.startAsDaemon(() ->
      {
         serviceActionPublisher = ROS2Tools.createPublisher(ros2Node, ROS2Tools.getSystemServiceActionTopic(instanceId));
      }, "Service-Action-Publisher");
   }

   public String getStatus()
   {
      return status == null ? "Status not available" : status;
   }

   public void setStatus(String status)
   {
      if (status != null && status.startsWith("Active: "))
      {
         status = status.substring(8); // Remove the "Active: "
         if (!status.equals(this.status))
            waitingOnStatusChange = false;
         this.status = status;
      }
      else
      {
         this.status = status;
      }
   }

   public void acceptLogLines(List<String> logLines)
   {
      logLines.forEach(consoleArea::acceptLine);
   }

   private void sendActionMessage(String systemdAction)
   {
      SystemServiceActionMessage message = new SystemServiceActionMessage();
      message.setServiceName(serviceName);
      message.setSystemdAction(systemdAction);
      serviceActionPublisher.publish(message);
   }

   public void sendStartMessage()
   {
      sendActionMessage("start");
   }

   public void sendStopMessage()
   {
      sendActionMessage("stop");
   }

   public void sendRestartMessage()
   {
      sendActionMessage("restart");
   }

   private boolean hasItBeenAWhileSinceTheLastActionRequest()
   {
      return (System.currentTimeMillis() - lastActionRequest) > TimeUnit.SECONDS.toMillis(5);
   }

   public void renderImGuiWidgets()
   {
      String statusString = status.toString();

      ImGui.pushFont(ImGuiTools.getSmallBoldFont());
      ImGui.text(serviceName);
      ImGui.popFont();
      ImGui.text(statusString);

      boolean isMissionControl3 = serviceName.contains("mission-control-3");
      boolean allButtonsDisabled = false;

      if (waitingOnStatusChange && !hasItBeenAWhileSinceTheLastActionRequest() || isMissionControl3)
      {
         allButtonsDisabled = true;
      }

      if (allButtonsDisabled)
         ImGui.beginDisabled(true);

      // Start button
      {
         boolean disabled = statusString.startsWith("active");
         if (disabled)
            ImGui.beginDisabled(true);
         if (ImGui.button("Start##" + instanceId + "-" + serviceName))
         {
            sendStartMessage();
            waitingOnStatusChange = true;
            lastActionRequest = System.currentTimeMillis();
         }
         if (disabled)
            ImGui.endDisabled();
      }
      ImGui.sameLine();
      // Stop button
      {
         boolean disabled = statusString.startsWith("inactive") || statusString.startsWith("failed") || isMissionControl3;
         if (disabled)
            ImGui.beginDisabled(true);
         if (ImGui.button("Stop##" + instanceId + "-" + serviceName))
         {
            sendStopMessage();
            waitingOnStatusChange = true;
            lastActionRequest = System.currentTimeMillis();
         }
         if (disabled)
            ImGui.endDisabled();
      }
      ImGui.sameLine();
      // Restart button
      {
         if (ImGui.button("Restart##" + instanceId + "-" + serviceName))
         {
            sendRestartMessage();
            waitingOnStatusChange = true;
            lastActionRequest = System.currentTimeMillis();
         }
      }

      if (allButtonsDisabled)
         ImGui.endDisabled();
   }

   public void renderImGuiLogPanelWidgets()
   {
      consoleArea.renderImGuiWidgets();
   }
}
