package com.yucareux.tellus.world.data.elevation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevationGridRepairTest {
   @Test
   void interpolatesMissingTileStripWithoutZeroHeightCliffs() {
      double[] elevations = {
         10.0, Double.NaN, Double.NaN, Double.NaN, 50.0,
         20.0, Double.NaN, Double.NaN, Double.NaN, 60.0,
         30.0, Double.NaN, Double.NaN, Double.NaN, 70.0
      };
      boolean[] missing = {
         false, true, true, true, false,
         false, true, true, true, false,
         false, true, true, true, false
      };

      assertTrue(ElevationGridRepair.repairMissing(elevations, missing, 5, 3));
      for (int z = 0; z < 3; z++) {
         int row = z * 5;
         assertTrue(elevations[row] < elevations[row + 1]);
         assertTrue(elevations[row + 1] < elevations[row + 2]);
         assertTrue(elevations[row + 2] < elevations[row + 3]);
         assertTrue(elevations[row + 3] < elevations[row + 4]);
      }
   }

   @Test
   void refusesToInventAnEntirelyMissingGrid() {
      assertFalse(
         ElevationGridRepair.repairMissing(
            new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN},
            new boolean[]{true, true, true, true},
            2,
            2
         )
      );
   }
}
