package us.ihmc.robotics;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.RandomMatrices_DDRM;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.RandomNumbers;
import us.ihmc.euclid.tools.EuclidCoreRandomTools;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.matrixlib.MatrixTestTools;
import us.ihmc.matrixlib.NativeCommonOps;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class MatrixMissingToolsTest
{
   private static final double EPSILON = 1.0e-9;

   @Test
   public void testSetDiagonalValues()
   {
      int iters = 100;
      DMatrixRMaj matrixToSet = new DMatrixRMaj(4, 7);
      Random random = new Random(1738L);
      for (int i = 0; i < iters; i++)
      {
         matrixToSet.setData(RandomNumbers.nextDoubleArray(random, 4 * 7, 100));
         DMatrixRMaj originalMatrix = new DMatrixRMaj(matrixToSet);
         double value = RandomNumbers.nextDouble(random, 10.0);
         MatrixMissingTools.setDiagonalValues(matrixToSet, value, 1, 3);

         for (int row = 0; row < 4; row++)
         {
            for (int col = 0; col < 7; col++)
            {
               if (row == 1 && col == 3)
                  assertEquals(value, matrixToSet.get(row, col), EPSILON);
               else if (row == 2 && col == 4)
                  assertEquals(value, matrixToSet.get(row, col), EPSILON);
               else if (row == 3 && col == 5)
                  assertEquals(value, matrixToSet.get(row, col), EPSILON);
               else
                  assertEquals(originalMatrix.get(row, col), matrixToSet.get(row, col), EPSILON);
            }
         }
      }
   }

   @Test
   public void testFast2x2Inverse()
   {
      int iters = 500;
      double epsilon = 1e-8;
      Random random = new Random(1738L);
      for (int i = 0; i < iters; i++)
      {
         DMatrixRMaj matrix = new DMatrixRMaj(2, 2);
         DMatrixRMaj matrixInverseExpected = new DMatrixRMaj(2, 2);
         DMatrixRMaj matrixInverse = new DMatrixRMaj(2, 2);
         matrix.setData(RandomNumbers.nextDoubleArray(random, 4, 10.0));

         NativeCommonOps.invert(matrix, matrixInverseExpected);
         MatrixMissingTools.fast2x2Inverse(matrix, matrixInverse);

         MatrixTestTools.assertMatrixEquals(matrixInverseExpected, matrixInverse, epsilon);
      }
   }

   @Test
   public void testToSkewSymmetric()
   {
      int iters = 500;
      Random random = new Random(1738L);
      for (int i = 0; i < iters; i++)
      {
         Vector3D vectorA = EuclidCoreRandomTools.nextVector3D(random);
         Vector3D vectorB = EuclidCoreRandomTools.nextVector3D(random);
         Vector3D vectorC = new Vector3D();

         vectorC.cross(vectorA, vectorB);

         DMatrixRMaj vectorBVector = new DMatrixRMaj(3, 1);
         DMatrixRMaj vectorCVector = new DMatrixRMaj(3, 1);
         DMatrixRMaj vectorCActual = new DMatrixRMaj(3, 1);
         DMatrixRMaj skewVectorA = new DMatrixRMaj(3, 3);
         vectorB.get(vectorBVector);
         MatrixMissingTools.toSkewSymmetricMatrix(vectorA, skewVectorA);

         CommonOps_DDRM.mult(skewVectorA, vectorBVector, vectorCVector);
         vectorC.get(vectorCActual);

         MatrixTestTools.assertMatrixEquals(vectorCActual, vectorCVector, EPSILON);
      }
   }

   @Test
   public void testSetMatrixRows()
   {
      Random random = new Random(45348L);

      // Pass in 0 for numberOfRows -- should do nothing
      DMatrixRMaj src = RandomMatrices_DDRM.rectangle(10, 10, random);
      DMatrixRMaj dest = RandomMatrices_DDRM.rectangle(10, 10, random);
      DMatrixRMaj srcCopy = new DMatrixRMaj(src);
      DMatrixRMaj destCopy = new DMatrixRMaj(dest);
      MatrixMissingTools.setMatrixRows(src, 0, dest, 0, 0);
      assertArrayEquals(srcCopy.getData(), src.getData(), EPSILON);
      assertArrayEquals(destCopy.getData(), dest.getData(), EPSILON);

      // Number of columns don't match -- should throw exception
      src = RandomMatrices_DDRM.rectangle(10, 10, random);
      dest = RandomMatrices_DDRM.rectangle(8, 8, random);  // dest is smaller than src
      int numberOfRows = 5;
      try
      {
         MatrixMissingTools.setMatrixRows(src, 0, dest, 0, numberOfRows);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }

      // Dest is too small -- should throw exception
      src = RandomMatrices_DDRM.rectangle(10, 10, random);
      dest = RandomMatrices_DDRM.rectangle(5, 10, random);  // dest is too small
      numberOfRows = 10;
      try
      {
         MatrixMissingTools.setMatrixRows(src, 0, dest, 0, numberOfRows);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }

      // Src is too small -- should throw exception
      src = RandomMatrices_DDRM.rectangle(5, 10, random);  // src is too small
      dest = RandomMatrices_DDRM.rectangle(10, 10, random);
      numberOfRows = 10;
      try
      {
         MatrixMissingTools.setMatrixRows(src, 0, dest, 0, numberOfRows);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }
   }

   @Test
   public void testSetMatrixColumns()
   {
      Random random = new Random(1738L);

      // Pass in 0 for numberOfColumns -- should do nothing
      DMatrixRMaj src = RandomMatrices_DDRM.rectangle(10, 10, random);
      DMatrixRMaj dest = RandomMatrices_DDRM.rectangle(10, 10, random);
      DMatrixRMaj srcCopy = new DMatrixRMaj(src);
      DMatrixRMaj destCopy = new DMatrixRMaj(dest);
      MatrixMissingTools.setMatrixColumns(src, 0, dest, 0, 0);
      assertArrayEquals(srcCopy.getData(), src.getData(), EPSILON);
      assertArrayEquals(destCopy.getData(), dest.getData(), EPSILON);

      // Number of rows don't match -- should throw exception
      src = RandomMatrices_DDRM.rectangle(10, 10, random);
      dest = RandomMatrices_DDRM.rectangle(8, 8, random);  // dest is smaller than src
      int numberOfColumns = 5;
      try
      {
         MatrixMissingTools.setMatrixColumns(src, 0, dest, 0, numberOfColumns);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }

      // Dest has too few columns -- should throw exception
      src = RandomMatrices_DDRM.rectangle(10, 10, random);
      dest = RandomMatrices_DDRM.rectangle(10, 5, random);  // dest only has 5 columns
      numberOfColumns = 10;
      try
      {
         MatrixMissingTools.setMatrixColumns(src, 0, dest, 0, numberOfColumns);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }

      // Src has too few columns -- should throw exception
      src = RandomMatrices_DDRM.rectangle(10, 5, random);  // src only has 5 columns
      dest = RandomMatrices_DDRM.rectangle(10, 10, random);
      numberOfColumns = 10;
      try
      {
         MatrixMissingTools.setMatrixColumns(src, 0, dest, 0, numberOfColumns);
         fail("Should have thrown exception");
      }
      catch (IllegalArgumentException e)
      {
         // good
      }
   }
}