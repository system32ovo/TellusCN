package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.worldgen.caves.TellusCaveBiomeDepthPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class TellusBiomeLocatePolicyTest {
   @Test
   void samplesEveryEligibleQuartLayerAcrossTheConfiguredShell() {
      int surfaceY = 130;
      List<Integer> samples = TellusBiomeLocatePolicy.caveBiomeQuartYs(surfaceY, 512, 64);

      assertFalse(samples.isEmpty());
      assertEquals(126, samples.size());
      for (int y : samples) {
         int depth = surfaceY - y;
         assertEquals(0, Math.floorMod(y, 4));
         assertTrue(TellusCaveBiomeDepthPolicy.isCaveBiomeDepth(depth, 512));
         assertTrue(depth < 512);
      }
   }

   @Test
   void followsShallowerConfiguredTerrainShell() {
      int surfaceY = 101;
      List<Integer> samples = TellusBiomeLocatePolicy.caveBiomeQuartYs(surfaceY, 20, 0);

      assertEquals(List.of(84, 88, 92), samples);
      assertTrue(samples.stream().allMatch(y -> surfaceY - y < 20));
   }

   @Test
   void ordersCaveLayersByDistanceFromLocateOrigin() {
      List<Integer> samples = TellusBiomeLocatePolicy.caveBiomeQuartYs(128, 64, 91);

      assertEquals(List.of(92, 88, 96, 84), samples.subList(0, 4));
   }

   @Test
   void hasNoCaveBiomeSamplesWhenShellEndsAtBiomeThreshold() {
      assertTrue(TellusBiomeLocatePolicy.caveBiomeQuartYs(80, 8, 64).isEmpty());
   }

   @Test
   void quartAlignmentIsCorrectForNegativeWorldHeights() {
      assertEquals(-4, TellusBiomeLocatePolicy.quartBlockY(-1));
      assertEquals(-8, TellusBiomeLocatePolicy.quartBlockY(-5));
   }
}
