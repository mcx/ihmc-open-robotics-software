package us.ihmc.behaviors.monteCarloPlanning;

import org.bytedeco.opencv.opencv_core.Mat;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple4D.Vector4D;
import us.ihmc.euclid.tuple4D.Vector4D32;

import java.util.ArrayList;

public class World
{
   private final Mat grid;
   private final Point2D goal;
   private final ArrayList<Vector4D32> obstacles = new ArrayList<>();

   private final int gridHeight;
   private final int gridWidth;
   private final int goalMargin;

   public World(ArrayList<Vector4D32> obstacles, Point2D goal, int goalMargin, int gridHeight, int gridWidth)
   {
      this.grid = new Mat(gridHeight, gridWidth, 0);
      this.gridHeight = gridHeight;
      this.gridWidth = gridWidth;
      this.goal = goal;
      this.goalMargin = goalMargin;
   }

   public void updateGrid(Point2D agent_state, int radius)
   {
      // set a circle of pixels around the agent to be 50
      int agent_min_x = (int) (agent_state.getX() - radius);
      int agent_max_x = (int) (agent_state.getX() + radius);
      int agent_min_y = (int) (agent_state.getY() - radius);
      int agent_max_y = (int) (agent_state.getY() + radius);

      for (int x = agent_min_x; x < agent_max_x; x++)
      {
         for (int y = agent_min_y; y < agent_max_y; y++)
         {
            // if point is within 5 pixels circular radius
            if (Math.sqrt(Math.pow(x - agent_state.getX(), 2) + Math.pow(y - agent_state.getY(), 2)) < radius)
            {
               // check if inside the world boundaries
               if (x >= 0 && x <= gridWidth && y >= 0 && y <= gridHeight)
               {
                  grid.ptr(x, y).putInt(50);
               }
            }
         }
      }
   }

   public Mat getGrid()
   {
      return grid;
   }

   public int getGridHeight()
   {
      return gridHeight;
   }

   public int getGridWidth()
   {
      return gridWidth;
   }

   public Point2D getGoal()
   {
      return goal;
   }

   public int getGoalMargin()
   {
      return goalMargin;
   }

   public ArrayList<Vector4D32> getObstacles()
   {
      return obstacles;
   }
}
