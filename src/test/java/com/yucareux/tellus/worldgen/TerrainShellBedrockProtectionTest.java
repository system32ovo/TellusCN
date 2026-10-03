package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerrainShellBedrockProtectionTest {
   @Test
   void flatTerrainNeedsOnlyTheBottomBedrockLayer() {
      int bottomY = TerrainShellBedrockProtection.supportBottomY(160, 64, -64);

      assertEquals(96, bottomY);
      assertEquals(96, TerrainShellBedrockProtection.sideSkinTopY(bottomY, 320, bottomY));
   }

   @Test
   void steepNeighborCreatesVerticalBedrockSkinUpToItsBottom() {
      int bottomY = TerrainShellBedrockProtection.supportBottomY(160, 64, -64);
      int neighborBottomY = TerrainShellBedrockProtection.supportBottomY(220, 64, -64);

      assertEquals(155, TerrainShellBedrockProtection.sideSkinTopY(bottomY, 320, neighborBottomY));
   }

   @Test
   void curtainBridgesAirGapWhenNeighborShellFloatsAboveTheColumn() {
      int bottomY = TerrainShellBedrockProtection.supportBottomY(100, 64, -64);
      int neighborBottomY = TerrainShellBedrockProtection.supportBottomY(200, 64, -64);

      assertEquals(100, TerrainShellBedrockProtection.sideSkinTopY(bottomY, 100, neighborBottomY));
      assertEquals(101, TerrainShellBedrockProtection.voidCurtainBottomY(neighborBottomY, -64, 100));
   }

   @Test
   void curtainIsSkippedWhenNeighborTerrainReachesTheShellBottom() {
      assertEquals(136, TerrainShellBedrockProtection.voidCurtainBottomY(136, -64, 160));
   }

   @Test
   void supportBottomNeverFallsBelowDimensionMinimum() {
      assertEquals(-64, TerrainShellBedrockProtection.supportBottomY(-40, 64, -64));
   }
}
