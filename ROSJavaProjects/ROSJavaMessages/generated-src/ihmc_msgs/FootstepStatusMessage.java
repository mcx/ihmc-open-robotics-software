package ihmc_msgs;

public interface FootstepStatusMessage extends org.ros.internal.message.Message {
  static final java.lang.String _TYPE = "ihmc_msgs/FootstepStatusMessage";
  static final java.lang.String _DEFINITION = "## FootstepStatusMessage\r\n# This message gives the status of the current footstep from the controller.\r\n\r\n#Options for enum\r\n# uint8 STARTED = 0\r\n# uint8 COMPLETED = 1\r\nuint8 status\r\n# footstepIndex monotonically increases with each completed footstep in a given\r\n# FootstepDataList and is then reset to 0 after all footsteps in the list are\r\n# completed.\r\nint32 footstepIndex\r\ngeometry_msgs/Vector3 actualFootPositionInWorld\r\ngeometry_msgs/Quaternion actualFootOrientationInWorld\r\n\r\n";
  byte getStatus();
  void setStatus(byte value);
  int getFootstepIndex();
  void setFootstepIndex(int value);
  geometry_msgs.Vector3 getActualFootPositionInWorld();
  void setActualFootPositionInWorld(geometry_msgs.Vector3 value);
  geometry_msgs.Quaternion getActualFootOrientationInWorld();
  void setActualFootOrientationInWorld(geometry_msgs.Quaternion value);
}
