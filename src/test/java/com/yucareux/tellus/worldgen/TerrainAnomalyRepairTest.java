package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class TerrainAnomalyRepairTest {
   @Test
   void repairsAnomalyFromAlreadySampledLodNeighbors() {
      int[] heights = {
         120, 120, 120,
         120, 60, 120,
         120, 120, 120
      };
      boolean[] repairMask = new boolean[heights.length];
      repairMask[4] = true;

      TerrainAnomalyRepair.repairHeightGrid(heights, repairMask, 3, -64, 320);

      assertArrayEquals(
         new int[]{
            120, 120, 120,
            120, 120, 120,
            120, 120, 120
         },
         heights
      );
   }

   @Test
   void doesNotCascadeRepairsThroughMutatedNeighbors() {
      int[] heights = {
         120, 120, 120, 120,
         120, 60, 60, 120,
         120, 120, 120, 120,
         120, 120, 120, 120
      };
      boolean[] repairMask = new boolean[heights.length];
      repairMask[5] = true;
      repairMask[6] = true;

      TerrainAnomalyRepair.repairHeightGrid(heights, repairMask, 4, -64, 320);

      assertArrayEquals(
         new int[]{
            120, 120, 120, 120,
            120, 60, 60, 120,
            120, 120, 120, 120,
            120, 120, 120, 120
         },
         heights
      );
   }
}
