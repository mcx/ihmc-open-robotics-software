package us.ihmc.exampleSimulations.fourBarLinkage;

import us.ihmc.euclid.Axis3D;
import us.ihmc.euclid.geometry.tools.EuclidGeometryTools;
import us.ihmc.euclid.matrix.Matrix3D;
import us.ihmc.euclid.tuple3D.UnitVector3D;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.graphicsDescription.appearance.AppearanceDefinition;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.mecano.tools.MecanoTools;
import us.ihmc.mecano.tools.MomentOfInertiaFactory;
import us.ihmc.robotics.robotDescription.LinkDescription;
import us.ihmc.robotics.robotDescription.LinkGraphicsDescription;
import us.ihmc.robotics.robotDescription.LoopClosureConstraintDescription;
import us.ihmc.robotics.robotDescription.PinJointDescription;
import us.ihmc.robotics.robotDescription.RobotDescription;

/**
 * <pre>
 *     _
 *     |
 *     O shoulderJoint
 *     |
 *     | upperarm
 *     |
 * D O----O A
 *    \  /
 *     \/
 *     /\
 *    /  \
 * B O----O C
 *     |
 *     | forearm
 *     |
 *     EE
 * </pre>
 */
public class InvertedFourBarLinkageRobotDescription extends RobotDescription
{
   public InvertedFourBarLinkageRobotDescription()
   {
      super("InvertedFourBarLinkageRobot");

      double lengthAB = 0.2 * Math.sqrt(2.0);
      double lengthBC = 0.2;
      double lengthCD = 0.2 * Math.sqrt(2.0);
      double lengthDA = 0.2;
      double upperarmLength = 0.5;
      double forearmLength = 0.5;
      UnitVector3D axisAB = new UnitVector3D(0.1, 0.0, 0.1);
      UnitVector3D axisCD = new UnitVector3D(-0.1, 0.0, 0.1);

      Vector3D rootJointOffset = new Vector3D(0.0, 0.0, 1.5);
      Vector3D jointAOffset = new Vector3D(0.5 * lengthDA, 0.0, -upperarmLength);
      Vector3D jointBOffset = new Vector3D();
      jointBOffset.setAndScale(-lengthAB, axisAB);
      Vector3D jointCOffsetFromD = new Vector3D();
      jointCOffsetFromD.setAndScale(-lengthCD, axisCD);
      Vector3D jointCOffsetFromB = new Vector3D(lengthBC, 0.0, 0.0);
      Vector3D jointDOffset = new Vector3D(-0.5 * lengthDA, 0.0, -upperarmLength);

      PinJointDescription shoulderJoint = new PinJointDescription("shoulder", rootJointOffset, Axis3D.Y);

      PinJointDescription fourBarJointA = new PinJointDescription("fourBarA", jointAOffset, Axis3D.Y);
      PinJointDescription fourBarJointB = new PinJointDescription("fourBarB", jointBOffset, Axis3D.Y);
      LoopClosureConstraintDescription fourBarJointC = LoopClosureConstraintDescription.createPinConstraintDescription("fourBarC",
                                                                                                                       jointCOffsetFromD,
                                                                                                                       jointCOffsetFromB,
                                                                                                                       Axis3D.Y);
      fourBarJointC.setGains(5000.0, 150.0);

      PinJointDescription fourBarJointD = new PinJointDescription("fourBarD", jointDOffset, Axis3D.Y);

      Vector3D offsetAB = new Vector3D();
      offsetAB.setAndScale(-0.5 * lengthAB, axisAB);
      LinkDescription linkAB = newCylinderLinkDescription("fourBarAB", lengthAB, 0.01, 0.1, axisAB, offsetAB, YoAppearance.BlackMetalMaterial());

      Vector3D offsetCD = new Vector3D();
      offsetCD.setAndScale(-0.5 * lengthCD, axisCD);
      LinkDescription linkCD = newCylinderLinkDescription("fourBarCD", lengthCD, 0.01, 0.1, axisCD, offsetCD, YoAppearance.BlackMetalMaterial());

      Vector3D offsetDA = new Vector3D(0.0, 0.0, -upperarmLength);
      LinkDescription linkDA = newCylinderLinkDescription("fourBarDA", lengthDA, 0.015, 0.1, Axis3D.X, offsetDA, YoAppearance.Grey());

      Vector3D offsetBC = new Vector3D(0.5 * lengthBC, 0.0, 0.0);
      LinkDescription linkBC = newCylinderLinkDescription("fourBarBC", lengthBC, 0.015, 0.1, Axis3D.X, offsetBC, YoAppearance.Grey());

      Vector3D upperarmOffset = new Vector3D(0.0, 0.0, -0.5 * upperarmLength);
      LinkDescription upperarm = newCylinderLinkDescription("upperarm", upperarmLength, 0.025, 1.0, Axis3D.Z, upperarmOffset, YoAppearance.AliceBlue());
      upperarm = merge("upperarm", linkDA, upperarm);
      Vector3D forearmOffset = new Vector3D(0.5 * lengthBC, 0.0, -0.5 * forearmLength);
      LinkDescription forearm = newCylinderLinkDescription("forearm", forearmLength, 0.025, 1.0, Axis3D.Z, forearmOffset, YoAppearance.BlueViolet());
      forearm = merge("forearm", linkBC, forearm);

      shoulderJoint.setLink(upperarm);
      fourBarJointA.setLink(linkAB);
      fourBarJointB.setLink(forearm);
      fourBarJointC.setLink(forearm);
      fourBarJointD.setLink(linkCD);

      shoulderJoint.addJoint(fourBarJointA);
      shoulderJoint.addJoint(fourBarJointD);

      fourBarJointA.addJoint(fourBarJointB);
      fourBarJointD.addConstraint(fourBarJointC);

      addRootJoint(shoulderJoint);
   }

   private static LinkDescription newCylinderLinkDescription(String name, double length, double radius, double mass, Vector3DReadOnly axis,
                                                             Vector3DReadOnly comOffset, AppearanceDefinition appearance)
   {
      LinkDescription linkDescription = new LinkDescription(name);
      linkDescription.setMass(mass);
      linkDescription.setMomentOfInertia(MomentOfInertiaFactory.solidCylinder(mass, radius, length, axis));
      linkDescription.setCenterOfMassOffset(comOffset);

      LinkGraphicsDescription linkGraphicsDescription = new LinkGraphicsDescription();
      linkGraphicsDescription.translate(comOffset);
      linkGraphicsDescription.rotate(EuclidGeometryTools.axisAngleFromZUpToVector3D(axis));
      linkGraphicsDescription.translate(0.0, 0.0, -0.5 * length);
      linkGraphicsDescription.addCylinder(length, radius, appearance);
      linkDescription.setLinkGraphics(linkGraphicsDescription);

      AppearanceDefinition inertiaAppearance = YoAppearance.LightGreen();
      inertiaAppearance.setTransparency(0.5);
      linkDescription.addEllipsoidFromMassProperties(inertiaAppearance);

      return linkDescription;
   }

   private static LinkDescription merge(String name, LinkDescription linkA, LinkDescription linkB)
   {
      double mergedMass = linkA.getMass() + linkB.getMass();
      Vector3D mergedCoM = new Vector3D();
      mergedCoM.setAndScale(linkA.getMass(), linkA.getCenterOfMassOffset());
      mergedCoM.scaleAdd(linkB.getMass(), linkB.getCenterOfMassOffset(), mergedCoM);
      mergedCoM.scale(1.0 / mergedMass);

      Vector3D translationInertiaA = new Vector3D();
      translationInertiaA.sub(mergedCoM, linkA.getCenterOfMassOffset());
      Matrix3D inertiaA = new Matrix3D(linkA.getMomentOfInertia());
      MecanoTools.translateMomentOfInertia(linkA.getMass(), null, false, translationInertiaA, inertiaA);

      Vector3D translationInertiaB = new Vector3D();
      translationInertiaB.sub(mergedCoM, linkB.getCenterOfMassOffset());
      Matrix3D inertiaB = new Matrix3D(linkB.getMomentOfInertia());
      MecanoTools.translateMomentOfInertia(linkB.getMass(), null, false, translationInertiaB, inertiaB);

      Matrix3D mergedInertia = new Matrix3D();
      mergedInertia.add(inertiaA);
      mergedInertia.add(inertiaB);

      LinkDescription merged = new LinkDescription(name);
      merged.setMass(mergedMass);
      merged.setCenterOfMassOffset(mergedCoM);
      merged.setMomentOfInertia(mergedInertia);

      LinkGraphicsDescription mergedGraphics = new LinkGraphicsDescription();
      mergedGraphics.combine(linkA.getLinkGraphics());
      mergedGraphics.combine(linkB.getLinkGraphics());
      merged.setLinkGraphics(mergedGraphics);

      return merged;
   }
}
