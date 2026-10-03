package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UndergroundGenerationDepthPolicyTest {
   @Test
   void usesTheFullConfiguredTerrainShell() {
      assertEquals(64, UndergroundGenerationDepthPolicy.generationDepth(64));
      assertEquals(192, UndergroundGenerationDepthPolicy.generationDepth(192));
      assertEquals(488, UndergroundGenerationDepthPolicy.generationFloorY(1_000, 512));
      assertEquals(489, UndergroundGenerationDepthPolicy.deepestGenerationY(1_000, 512));
   }

   @Test
   void usesTheTerrainShellBelowTheGeneratorsSamplingMinimum() {
      assertEquals(-178, UndergroundGenerationDepthPolicy.deepestGenerationY(77, 256, -368));
   }

   @Test
   void clampsTheUsableShellAboveTheDimensionFloor() {
      assertEquals(-367, UndergroundGenerationDepthPolicy.deepestGenerationY(-200, 512, -368));
   }

   @Test
   void treatsTheConfiguredShellBoundaryAsProtectedTerrain() {
      assertTrue(UndergroundGenerationDepthPolicy.containsDepth(63, 512));
      assertTrue(UndergroundGenerationDepthPolicy.containsDepth(64, 512));
      assertTrue(UndergroundGenerationDepthPolicy.containsDepth(511, 512));
      assertFalse(UndergroundGenerationDepthPolicy.containsDepth(512, 512));
   }

   @Test
   void stillHonorsAConfigurableShellIfItIsEverShallowerThanVanilla() {
      assertEquals(20, UndergroundGenerationDepthPolicy.generationDepth(20));
      assertTrue(UndergroundGenerationDepthPolicy.containsDepth(19, 20));
      assertFalse(UndergroundGenerationDepthPolicy.containsDepth(20, 20));
   }
}
