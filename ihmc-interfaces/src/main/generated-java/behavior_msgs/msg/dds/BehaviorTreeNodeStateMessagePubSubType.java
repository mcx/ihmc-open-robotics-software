package behavior_msgs.msg.dds;

/**
* 
* Topic data type of the struct "BehaviorTreeNodeStateMessage" defined in "BehaviorTreeNodeStateMessage_.idl". Use this class to provide the TopicDataType to a Participant. 
*
* This file was automatically generated from BehaviorTreeNodeStateMessage_.idl by us.ihmc.idl.generator.IDLGenerator. 
* Do not update this file directly, edit BehaviorTreeNodeStateMessage_.idl instead.
*
*/
public class BehaviorTreeNodeStateMessagePubSubType implements us.ihmc.pubsub.TopicDataType<behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage>
{
   public static final java.lang.String name = "behavior_msgs::msg::dds_::BehaviorTreeNodeStateMessage_";
   
   @Override
   public final java.lang.String getDefinitionChecksum()
   {
   		return "3e050a2033dc471455439001bcf9c636bf28dc846723ac522a18e007165afe7e";
   }
   
   @Override
   public final java.lang.String getDefinitionVersion()
   {
   		return "local";
   }

   private final us.ihmc.idl.CDR serializeCDR = new us.ihmc.idl.CDR();
   private final us.ihmc.idl.CDR deserializeCDR = new us.ihmc.idl.CDR();

   @Override
   public void serialize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.pubsub.common.SerializedPayload serializedPayload) throws java.io.IOException
   {
      serializeCDR.serialize(serializedPayload);
      write(data, serializeCDR);
      serializeCDR.finishSerialize();
   }

   @Override
   public void deserialize(us.ihmc.pubsub.common.SerializedPayload serializedPayload, behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data) throws java.io.IOException
   {
      deserializeCDR.deserialize(serializedPayload);
      read(data, deserializeCDR);
      deserializeCDR.finishDeserialize();
   }

   public static int getMaxCdrSerializedSize()
   {
      return getMaxCdrSerializedSize(0);
   }

   public static int getMaxCdrSerializedSize(int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 1 + us.ihmc.idl.CDR.alignment(current_alignment, 1);

      current_alignment += ihmc_common_msgs.msg.dds.InstantMessagePubSubType.getMaxCdrSerializedSize(current_alignment);


      return current_alignment - initial_alignment;
   }

   public final static int getCdrSerializedSize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data)
   {
      return getCdrSerializedSize(data, 0);
   }

   public final static int getCdrSerializedSize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, int current_alignment)
   {
      int initial_alignment = current_alignment;

      current_alignment += 1 + us.ihmc.idl.CDR.alignment(current_alignment, 1);


      current_alignment += ihmc_common_msgs.msg.dds.InstantMessagePubSubType.getCdrSerializedSize(data.getLastTickInstant(), current_alignment);


      return current_alignment - initial_alignment;
   }

   public static void write(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.idl.CDR cdr)
   {
      cdr.write_type_9(data.getStatus());

      ihmc_common_msgs.msg.dds.InstantMessagePubSubType.write(data.getLastTickInstant(), cdr);
   }

   public static void read(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.idl.CDR cdr)
   {
      data.setStatus(cdr.read_type_9());
      	
      ihmc_common_msgs.msg.dds.InstantMessagePubSubType.read(data.getLastTickInstant(), cdr);	

   }

   @Override
   public final void serialize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.idl.InterchangeSerializer ser)
   {
      ser.write_type_9("status", data.getStatus());
      ser.write_type_a("last_tick_instant", new ihmc_common_msgs.msg.dds.InstantMessagePubSubType(), data.getLastTickInstant());

   }

   @Override
   public final void deserialize(us.ihmc.idl.InterchangeSerializer ser, behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data)
   {
      data.setStatus(ser.read_type_9("status"));
      ser.read_type_a("last_tick_instant", new ihmc_common_msgs.msg.dds.InstantMessagePubSubType(), data.getLastTickInstant());

   }

   public static void staticCopy(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage src, behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage dest)
   {
      dest.set(src);
   }

   @Override
   public behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage createData()
   {
      return new behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage();
   }
   @Override
   public int getTypeSize()
   {
      return us.ihmc.idl.CDR.getTypeSize(getMaxCdrSerializedSize());
   }

   @Override
   public java.lang.String getName()
   {
      return name;
   }
   
   public void serialize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.idl.CDR cdr)
   {
      write(data, cdr);
   }

   public void deserialize(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage data, us.ihmc.idl.CDR cdr)
   {
      read(data, cdr);
   }
   
   public void copy(behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage src, behavior_msgs.msg.dds.BehaviorTreeNodeStateMessage dest)
   {
      staticCopy(src, dest);
   }

   @Override
   public BehaviorTreeNodeStateMessagePubSubType newInstance()
   {
      return new BehaviorTreeNodeStateMessagePubSubType();
   }
}
