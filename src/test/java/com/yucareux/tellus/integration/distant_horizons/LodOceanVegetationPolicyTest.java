package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LodOceanVegetationPolicyTest {
   @Test
   void placementIsStableWithinEightBlockPatches() {
      int hash = LodOceanVegetationPolicy.patchHash(80, -40);
      assertEquals(hash, LodOceanVegetationPolicy.patchHash(87, -33));
      assertEquals(hash, LodOceanVegetationPolicy.patchHash(80, -40));
   }

   @Test
   void coralRequiresShallowWarmOceanWater() {
      int coralHash = findCoralHash();
      assertTrue(LodOceanVegetationPolicy.shouldUseCoral(true, 20, coralHash));
      assertFalse(LodOceanVegetationPolicy.shouldUseCoral(false, 20, coralHash));
      assertFalse(LodOceanVegetationPolicy.shouldUseCoral(true, 1, coralHash));
      assertFalse(LodOceanVegetationPolicy.shouldUseCoral(true, LodOceanVegetationPolicy.MAX_CORAL_DEPTH + 1, coralHash));
   }

   @Test
   void aquaticColumnsLeaveWaterAboveTheirTop() {
      assertEquals(1, LodOceanVegetationPolicy.columnHeight(true, 2, 0));
      assertTrue(LodOceanVegetationPolicy.columnHeight(true, 20, -1) <= 3);
      assertTrue(LodOceanVegetationPolicy.columnHeight(false, 20, -1) <= 4);
   }

   private static int findCoralHash() {
      for (int x = 0; x < 1024; x++) {
         int hash = LodOceanVegetationPolicy.patchHash(x * LodOceanVegetationPolicy.PATCH_BLOCKS, 0);
         if (LodOceanVegetationPolicy.shouldUseCoral(true, 20, hash)) {
            return hash;
         }
      }
      throw new AssertionError("Expected a deterministic coral patch");
   }
}
