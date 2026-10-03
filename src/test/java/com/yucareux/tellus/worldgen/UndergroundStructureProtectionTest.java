package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import org.junit.jupiter.api.Test;

class UndergroundStructureProtectionTest {
   @Test
   void reservesOneBedrockAndFourStoneLayersBelowTheStructure() {
      assertEquals(1, UndergroundStructureProtection.BEDROCK_THICKNESS);
      assertEquals(4, UndergroundStructureProtection.STONE_THICKNESS);
      assertEquals(5, UndergroundStructureProtection.TOTAL_THICKNESS);
      assertEquals(-45, UndergroundStructureProtection.protectionBottomY(-40));
      assertEquals(-59, UndergroundStructureProtection.minimumStructureY(-64));
   }

   @Test
   void extendsOnlyWhenStructureProtectionCrossesActualTerrainFloor() {
      assertFalse(UndergroundStructureProtection.needsTerrainExtension(40, 100, 128));
      assertTrue(UndergroundStructureProtection.needsTerrainExtension(30, 100, 64));
      assertFalse(UndergroundStructureProtection.needsTerrainExtension(41, 100, 64));
   }

   @Test
   void makesBottomAndOutermostSidesBedrockButLeavesTopOpen() {
      assertTrue(UndergroundStructureProtection.isOuterBedrockSkin(5, -20, 5, 0, -20, 0, 10, 10));
      assertTrue(UndergroundStructureProtection.isOuterBedrockSkin(0, -10, 5, 0, -20, 0, 10, 10));
      assertFalse(UndergroundStructureProtection.isOuterBedrockSkin(1, -10, 1, 0, -20, 0, 10, 10));
   }

   @Test
   void usesBoundedDeepslateCavernsWithoutBedrockWallsForBeardBoxStructures() {
      assertTrue(UndergroundStructureProtection.usesBoundedCavernEnvelope(TerrainAdjustment.BEARD_BOX));
      assertFalse(UndergroundStructureProtection.usesBoundedCavernEnvelope(TerrainAdjustment.BURY));
      assertEquals(1, UndergroundStructureProtection.horizontalProtectionThickness(true));
      assertEquals(5, UndergroundStructureProtection.horizontalProtectionThickness(false));
      assertEquals(12, UndergroundStructureProtection.horizontalClearanceRadius(TerrainAdjustment.BEARD_BOX));
      assertEquals(0, UndergroundStructureProtection.clearanceBelow(TerrainAdjustment.BEARD_BOX));
      assertEquals(1, UndergroundStructureProtection.clearanceFloorOffset(TerrainAdjustment.BEARD_BOX));
      assertEquals(0, UndergroundStructureProtection.clearanceFloorOffset(TerrainAdjustment.BURY));
      assertEquals(12, UndergroundStructureProtection.clearanceAbove(TerrainAdjustment.BEARD_BOX));
      assertEquals(
         19,
         UndergroundStructureProtection.protectionFillTopY(40, 20, true, true)
      );
      assertEquals(
         20,
         UndergroundStructureProtection.protectionFillTopY(40, 20, true, false)
      );
      assertEquals(
         40,
         UndergroundStructureProtection.protectionFillTopY(40, 20, false, true)
      );
      assertTrue(
         UndergroundStructureProtection.isOuterBedrockSkin(5, -20, 5, 0, -20, 0, 10, 10, false)
      );
      assertFalse(
         UndergroundStructureProtection.isOuterBedrockSkin(0, -10, 5, 0, -20, 0, 10, 10, false)
      );
   }

   @Test
   void capsAncientCityClearanceBelowTheSurface() {
      assertEquals(
         96,
         UndergroundStructureProtection.clearanceCeilingY(TerrainAdjustment.BEARD_BOX, 90, 112)
      );
      assertEquals(
         102,
         UndergroundStructureProtection.clearanceCeilingY(TerrainAdjustment.BEARD_BOX, 90, 140)
      );
      assertEquals(
         94,
         UndergroundStructureProtection.clearanceCeilingY(TerrainAdjustment.BEARD_THIN, 90, 92)
      );
   }
}
