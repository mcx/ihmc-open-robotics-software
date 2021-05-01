package us.ihmc.behaviors;

import org.apache.commons.lang3.tuple.MutablePair;

import org.apache.commons.lang3.tuple.Pair;
import std_msgs.msg.dds.Empty;
import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.commons.exception.DefaultExceptionHandler;
import us.ihmc.commons.exception.ExceptionTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.CommunicationMode;
import us.ihmc.communication.IHMCROS2Callback;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.configuration.NetworkParameters;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.behaviors.tools.BehaviorHelper;
import us.ihmc.behaviors.tools.BehaviorMessagerUpdateThread;
import us.ihmc.behaviors.tools.interfaces.StatusLogger;
import us.ihmc.log.LogTools;
import us.ihmc.messager.Messager;
import us.ihmc.messager.MessagerAPIFactory;
import us.ihmc.messager.MessagerAPIFactory.MessagerAPI;
import us.ihmc.messager.SharedMemoryMessager;
import us.ihmc.messager.kryo.KryoMessager;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.ROS2Node;
import us.ihmc.ros2.ROS2Topic;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.RosTools;

import java.util.*;

import static us.ihmc.behaviors.BehaviorModule.API.BehaviorSelection;

public class BehaviorModule
{
   private static SharedMemoryMessager sharedMemoryMessager;

   private final RosMainNode ros1Node;
   private final boolean manageROS1Node;
   private final ROS2Node ros2Node;
   private final boolean manageROS2Node;
   private final Messager messager;
   private final boolean manageMessager;
   private StatusLogger statusLogger;
   private final Map<String, Pair<BehaviorDefinition, BehaviorInterface>> constructedBehaviors = new HashMap<>();
   private final Map<String, Boolean> enabledBehaviors = new HashMap<>();

   public static BehaviorModule createInterprocess(BehaviorRegistry behaviorRegistry, DRCRobotModel robotModel)
   {
      return new BehaviorModule(behaviorRegistry, robotModel, CommunicationMode.INTERPROCESS, CommunicationMode.INTERPROCESS);
   }

   public static BehaviorModule createIntraprocess(BehaviorRegistry behaviorRegistry, DRCRobotModel robotModel)
   {
      return new BehaviorModule(behaviorRegistry, robotModel, CommunicationMode.INTRAPROCESS, CommunicationMode.INTRAPROCESS);
   }

   public BehaviorModule(BehaviorRegistry behaviorRegistry, 
                         DRCRobotModel robotModel,
                         CommunicationMode ros2CommunicationMode,
                         CommunicationMode messagerCommunicationMode)
   {
      this.manageROS1Node = true;
      this.manageROS2Node = true;
      this.manageMessager = true;

      LogTools.info("Starting behavior module in ROS 2: {}, Messager: {} modes", ros2CommunicationMode.name(), messagerCommunicationMode.name());

      ros1Node = RosTools.createRosNode(NetworkParameters.getROSURI(), "behavior_backpack");

      MessagerAPI messagerAPI = behaviorRegistry.getMessagerAPI();

      PubSubImplementation pubSubImplementation;
      if (ros2CommunicationMode == CommunicationMode.INTERPROCESS)
      {
         pubSubImplementation = PubSubImplementation.FAST_RTPS;
      }
      else // intraprocess
      {
         pubSubImplementation = PubSubImplementation.INTRAPROCESS;
      }
      if (messagerCommunicationMode == CommunicationMode.INTERPROCESS)
      {
         messager = KryoMessager.createServer(messagerAPI,
                                              NetworkPorts.BEHAVIOUR_MODULE_PORT.getPort(),
                                              new BehaviorMessagerUpdateThread(BehaviorModule.class.getSimpleName(), 5));
      }
      else // intraprocess
      {
         messager = new SharedMemoryMessager(messagerAPI);
      }
      ThreadTools.startAThread(() -> ExceptionTools.handle(messager::startMessager, DefaultExceptionHandler.RUNTIME_EXCEPTION), "KryoStarter");

      ros2Node = ROS2Tools.createROS2Node(pubSubImplementation, "behavior_backpack");

      init(behaviorRegistry, robotModel, ros1Node, ros2Node, messager);
   }

   public BehaviorModule(BehaviorRegistry behaviorRegistry,
                         DRCRobotModel robotModel,
                         RosMainNode ros1Node,
                         ROS2Node ros2Node,
                         Messager messager)
   {
      this.ros1Node = ros1Node;
      this.manageROS1Node = false;
      this.ros2Node = ros2Node;
      this.manageROS2Node = false;
      this.messager = messager;
      this.manageMessager = false;

      init(behaviorRegistry, robotModel, ros1Node, ros2Node, messager);
   }

   private void init(BehaviorRegistry behaviorRegistry, DRCRobotModel robotModel, RosMainNode ros1Node, ROS2Node ros2Node, Messager messager)
   {
      if (messager instanceof SharedMemoryMessager)
         sharedMemoryMessager = (SharedMemoryMessager) messager;

      statusLogger = new StatusLogger(messager::submitMessage);

      for (BehaviorDefinition behaviorDefinition : behaviorRegistry.getDefinitionEntries())
      {
         BehaviorHelper helper = new BehaviorHelper(robotModel, messager, ros1Node, ros2Node, false);
         BehaviorInterface constructedBehavior = behaviorDefinition.getBehaviorSupplier().build(helper);
         constructedBehaviors.put(behaviorDefinition.getName(), Pair.of(behaviorDefinition, constructedBehavior));
      }

      messager.registerTopicListener(BehaviorSelection, this::stringBasedSelection);

      new IHMCROS2Callback<>(ros2Node, API.SHUTDOWN, message ->
      {
         statusLogger.info("Received SHUTDOWN. Shutting down...");

         ThreadTools.startAsDaemon(this::destroy, "DestroyThread");
      });

      ros1Node.execute();
   }

   private void stringBasedSelection(String selection)
   {
      ArrayList<String> selectedBehaviors = new ArrayList<>();
      selectedBehaviors.add(selection);
      if (constructedBehaviors.containsKey(selection)) // i.e. Might be "None"
      {
         for (BehaviorDefinition subBehavior : constructedBehaviors.get(selection).getLeft().getSubBehaviors())
         {
            selectedBehaviors.add(subBehavior.getName());
         }
      }

      boolean selectedOne = false;
      for (Map.Entry<String, Pair<BehaviorDefinition, BehaviorInterface>> behavior : constructedBehaviors.entrySet())
      {
         String behaviorName = behavior.getKey();
         boolean selected = selectedBehaviors.contains(behaviorName);
         if (selected)
         {
            selectedOne = true;
         }
         if (enabledBehaviors.computeIfAbsent(behaviorName, key -> false) != selected)
         {
            enabledBehaviors.put(behaviorName, selected);
            statusLogger.info("{} {} behavior.", selected ? "Enabling" : "Disabling", behaviorName);
            behavior.getValue().getRight().setEnabled(selected);
         }
      }
      if (!selectedOne)
      {
         statusLogger.info("All behaviors disabled.");
      }
   }

   public Messager getMessager()
   {
      return messager;
   }

   public void destroy()
   {
      statusLogger.info("Shutting down...");

      if (manageROS2Node)
      {
         ros2Node.destroy();
      }
      if (manageROS1Node)
      {
         ros1Node.shutdown();
      }
      if (manageMessager)
      {
         ExceptionTools.handle(() -> getMessager().closeMessager(), DefaultExceptionHandler.PRINT_STACKTRACE);
      }

      for (Pair<BehaviorDefinition, BehaviorInterface> behavior : constructedBehaviors.values())
      {
         behavior.getRight().destroy();
      }
   }

   public static SharedMemoryMessager getSharedMemoryMessager()
   {
      return sharedMemoryMessager;
   }

   // API created here from build
   public static class API
   {
      public static final ROS2Topic<Empty> SHUTDOWN = ROS2Tools.BEHAVIOR_MODULE.withOutput().withType(Empty.class).withSuffix("shutdown");

      private static final MessagerAPIFactory apiFactory = new MessagerAPIFactory();
      private static final MessagerAPIFactory.Category RootCategory = apiFactory.createRootCategory("Root");
      private static final MessagerAPIFactory.CategoryTheme BehaviorModuleTheme = apiFactory.createCategoryTheme("BehaviorModule");

      public static final MessagerAPIFactory.Topic<String> BehaviorSelection = topic("BehaviorSelection");
      public static final MessagerAPIFactory.Topic<MutablePair<Integer, String>> StatusLog = topic("StatusLog");

      private static <T> MessagerAPIFactory.Topic<T> topic(String name)
      {
         return RootCategory.child(BehaviorModuleTheme).topic(apiFactory.createTypedTopicTheme(name));
      }

      public static synchronized MessagerAPI create(MessagerAPI... behaviorAPIs) // TODO check threading
      {
         apiFactory.includeMessagerAPIs(behaviorAPIs);

         return apiFactory.getAPIAndCloseFactory();
      }
   }
}
