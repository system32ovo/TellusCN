package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnowTransitionPolicyTest {
   @Test
   void preservesExistingSlopeCoverageInsideBroadSnowfields() {
      SnowTransitionPolicy.SourceSampler allSnow = (worldX, worldZ) -> true;
      for (double slope : new double[]{Double.NaN, 0.0, 47.0, 52.0, 57.0, 65.0}) {
         for (int worldZ = -16; worldZ <= 16; worldZ++) {
            assertEquals(
               SnowSlopePolicy.shouldCover(23, worldZ, slope),
               SnowTransitionPolicy.shouldCover(23, worldZ, slope, true, allSnow, 918273645L)
            );
         }
      }
   }

   @Test
   void neverCreatesSnowFarFromAnySnowSource() {
      SnowTransitionPolicy.SourceSampler noSnow = (worldX, worldZ) -> false;
      for (int worldZ = -64; worldZ <= 64; worldZ++) {
         assertFalse(SnowTransitionPolicy.shouldCover(37, worldZ, 0.0, false, noSnow, 1234L));
      }
   }

   @Test
   void warpingAndMaskTurnAStraightSourceEdgeIntoAnIrregularTransition() {
      SnowTransitionPolicy.SourceSampler westHalfPlane = (worldX, worldZ) -> worldX <= 0;
      Set<Integer> transitionEdges = new HashSet<>();
      int barePocketsInsideSource = 0;
      int snowPatchesOutsideSource = 0;

      for (int worldZ = -192; worldZ <= 192; worldZ++) {
         int easternmostSnow = Integer.MIN_VALUE;
         for (int worldX = -64; worldX <= 64; worldX++) {
            boolean covered = SnowTransitionPolicy.shouldCover(
               worldX, worldZ, 0.0, worldX <= 0, westHalfPlane, 78234789234789L
            );
            if (covered) {
               easternmostSnow = worldX;
               if (worldX > 0) {
                  snowPatchesOutsideSource++;
               }
            } else if (worldX <= 0) {
               barePocketsInsideSource++;
            }
         }
         transitionEdges.add(easternmostSnow);
      }

      assertTrue(barePocketsInsideSource > 0);
      assertTrue(snowPatchesOutsideSource > 0);
      assertTrue(transitionEdges.size() >= 12);
   }

   @Test
   void transitionHasABoundedInfluenceAndUsesAbsoluteCoordinates() {
      SnowTransitionPolicy.SourceSampler westHalfPlane = (worldX, worldZ) -> worldX <= 0;
      long seed = -41234987239487L;
      for (int worldZ = -128; worldZ <= 128; worldZ++) {
         assertTrue(
            SnowTransitionPolicy.shouldCover(
               -SnowTransitionPolicy.MAX_EDGE_DISPLACEMENT_BLOCKS - 1,
               worldZ,
               0.0,
               true,
               westHalfPlane,
               seed
            )
         );
         assertFalse(
            SnowTransitionPolicy.shouldCover(
               SnowTransitionPolicy.MAX_EDGE_DISPLACEMENT_BLOCKS + 1,
               worldZ,
               0.0,
               false,
               westHalfPlane,
               seed
            )
         );
         assertEquals(
            SnowTransitionPolicy.shouldCover(0, worldZ, 0.0, true, westHalfPlane, seed),
            SnowTransitionPolicy.shouldCover(0, worldZ, 0.0, true, westHalfPlane, seed)
         );
      }
   }
}
