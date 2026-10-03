package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeologicalStonePlacementPolicyTest {
   @Test
   void buriesBlobOriginsBelowTheLowestNearbySurface() {
      assertEquals(94, GeologicalStonePlacementPolicy.safeBlobOriginY(120, 100, 20));
      assertEquals(80, GeologicalStonePlacementPolicy.safeBlobOriginY(80, 100, 20));
   }

   @Test
   void rejectsBlobWhenSafeCoverWouldCrossTheShellFloor() {
      assertEquals(
         GeologicalStonePlacementPolicy.REJECTED_PLACEMENT_Y,
         GeologicalStonePlacementPolicy.safeBlobOriginY(90, 100, 95)
      );
   }

   @Test
   void requiresNoiseVeinHostStoneToRemainBuried() {
      assertTrue(GeologicalStonePlacementPolicy.isNoiseStoneBuried(98, 100));
      assertFalse(GeologicalStonePlacementPolicy.isNoiseStoneBuried(99, 100));
   }
}
