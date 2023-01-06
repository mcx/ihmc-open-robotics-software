package us.ihmc.behaviors.sequence;

public interface BehaviorAction extends BehaviorActionData
{
   default void update()
   {

   }

   default void executeAction()
   {
      
   }

   default boolean isExecuting()
   {
      return false;
   }
}
