package us.ihmc.simulationconstructionset.util.environments;

import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.simulationconstructionset.ExternalForcePoint;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.util.ground.CombinedTerrainObject3D;
import us.ihmc.simulationconstructionset.util.ground.TerrainObject3D;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
public class PlanarRegionDefinedEnvironment implements CommonAvatarEnvironmentInterface
{
   private final CombinedTerrainObject3D combinedTerrainObject;
   private final List<Robot> environmentRobots = new ArrayList<>();
   private final List<ExternalForcePoint> contactPoints = new ArrayList<>();
   private final String environmentName;
   private final PlanarRegionsList planarRegionsList;

   public PlanarRegionDefinedEnvironment(String environmentName, PlanarRegionsList planarRegionsList)
   {
      this.environmentName = environmentName;
      this.planarRegionsList = planarRegionsList;

      combinedTerrainObject = createCombinedTerrainObjectFromPlanarRegionsList(environmentName, this.planarRegionsList);
   }

   public PlanarRegionDefinedEnvironment(PlanarRegionsList planarRegionsList)
   {
      this(PlanarRegionDefinedEnvironment.class.getSimpleName(), planarRegionsList);
   }

   private CombinedTerrainObject3D createCombinedTerrainObjectFromPlanarRegionsList(String environmentName, PlanarRegionsList planarRegionsList)
   {
      return new CombinedTerrainObject3D(environmentName);
   }

   @Override
   public TerrainObject3D getTerrainObject3D()
   {
      return combinedTerrainObject;
   }

   @Override
   public List<? extends Robot> getEnvironmentRobots()
   {
      return environmentRobots;
   }

   @Override
   public void createAndSetContactControllerToARobot()
   {

   }

   @Override
   public void addContactPoints(List<? extends ExternalForcePoint> externalForcePoints)
   {
      this.contactPoints.addAll(externalForcePoints);
   }

   @Override
   public void addSelectableListenerToSelectables(SelectableObjectListener selectedListener)
   {

   }
}
