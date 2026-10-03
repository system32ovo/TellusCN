package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BadlandsTerrainPolicyTest {
   @Test
   void promotesOnlyRuggedAridDryTerrain() {
      assertTrue(BadlandsTerrainPolicy.shouldPromoteToBadlands(60, "BWh", 350.0));
      assertTrue(BadlandsTerrainPolicy.shouldPromoteToBadlands(20, "BSk", 120.0));
      assertFalse(BadlandsTerrainPolicy.shouldPromoteToBadlands(60, "BWh", 119.9));
      assertFalse(BadlandsTerrainPolicy.shouldPromoteToBadlands(40, "BWh", 500.0));
      assertFalse(BadlandsTerrainPolicy.shouldPromoteToBadlands(60, "Csa", 500.0));
   }

   @Test
   void cliffBandsReachPastTheExposedRelief() {
      assertEquals(24, BadlandsTerrainPolicy.cliffBandDepth(4, 3));
      assertEquals(88, BadlandsTerrainPolicy.cliffBandDepth(4, 80));
      assertEquals(65536, BadlandsTerrainPolicy.cliffBandDepth(4, Integer.MAX_VALUE));
   }

   @Test
   void regionalBiomeSamplesUseStableCoarseCells() {
      assertEquals(27, BadlandsTerrainPolicy.regionalSampleCellBlocks(30.0));
      assertEquals(16, BadlandsTerrainPolicy.regionalSampleCellBlocks(100.0));
      assertEquals(16, BadlandsTerrainPolicy.regionalSampleCellBlocks(Double.NaN));
   }

   @Test
   void bandingIsDeterministicAndUsesTheFullGrandCanyonPalette() {
      Set<Integer> materials = new HashSet<>();
      for (int y = -64; y < 192; y++) {
         int material = BadlandsTerrainPolicy.bandMaterialIndex(1234, -5678, y);
         assertEquals(material, BadlandsTerrainPolicy.bandMaterialIndex(1234, -5678, y));
         materials.add(material);
      }

      assertEquals(7, materials.size());
   }

   @Test
   void plateauWeatheringIsSparseAndAvoidsDarkGroundStains() {
      Set<Integer> materials = new HashSet<>();
      int redSandColumns = 0;
      int terracottaColumns = 0;
      for (int z = -4096; z < 4096; z += 16) {
         for (int x = -4096; x < 4096; x += 16) {
            int material = BadlandsTerrainPolicy.plateauMaterialIndex(x, z);
            materials.add(material);
            if (material == BadlandsTerrainPolicy.PLATEAU_RED_SAND) {
               redSandColumns++;
            } else if (material == BadlandsTerrainPolicy.PLATEAU_TERRACOTTA) {
               terracottaColumns++;
            }
         }
      }

      int sampledColumns = redSandColumns + terracottaColumns;
      double terracottaCoverage = terracottaColumns / (double)sampledColumns;
      assertEquals(Set.of(BadlandsTerrainPolicy.PLATEAU_RED_SAND, BadlandsTerrainPolicy.PLATEAU_TERRACOTTA), materials);
      assertTrue(terracottaCoverage >= 0.02, "Expected some exposed terracotta");
      assertTrue(terracottaCoverage <= 0.05, "Exposed terracotta should remain a sparse plateau accent");
   }
}
