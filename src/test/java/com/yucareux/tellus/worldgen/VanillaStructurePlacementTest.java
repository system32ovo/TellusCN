package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VanillaStructurePlacementTest {
   @Test
   void recognizesTheVanillaMansionIdAndLegacyAlias() {
      assertTrue(VanillaStructurePlacement.isWoodlandMansionPath("mansion"));
      assertTrue(VanillaStructurePlacement.isWoodlandMansionPath("woodland_mansion"));
      assertFalse(VanillaStructurePlacement.isWoodlandMansionPath("village_plains"));
   }

   @Test
   void increasedHeightAlwaysRetargetsAbsoluteUndergroundPlacements() {
      assertTrue(VanillaStructurePlacement.shouldRetargetStronghold(true, false, false));
      assertTrue(VanillaStructurePlacement.shouldRetargetBuriedStructure(true, -20, 900));
      assertTrue(VanillaStructurePlacement.shouldRetargetMesaMineshaft(true));
   }

   @Test
   void standardHeightOnlyRetargetsInvalidPlacements() {
      assertFalse(VanillaStructurePlacement.shouldRetargetStronghold(false, false, false));
      assertTrue(VanillaStructurePlacement.shouldRetargetStronghold(false, true, false));
      assertTrue(VanillaStructurePlacement.shouldRetargetStronghold(false, false, true));
      assertFalse(VanillaStructurePlacement.shouldRetargetBuriedStructure(false, 39, 40));
      assertTrue(VanillaStructurePlacement.shouldRetargetBuriedStructure(false, 41, 40));
      assertFalse(VanillaStructurePlacement.shouldRetargetMesaMineshaft(false));
   }

   @Test
   void mesaMineshaftsRemainCloserToTheSurface() {
      assertEquals(4, VanillaStructurePlacement.mineshaftSurfaceClearance(true));
      assertEquals(8, VanillaStructurePlacement.mineshaftDepthBase(true));
      assertEquals(25, VanillaStructurePlacement.mineshaftDepthRange(true));
      assertEquals(14, VanillaStructurePlacement.mineshaftSurfaceClearance(false));
      assertEquals(30, VanillaStructurePlacement.mineshaftDepthBase(false));
      assertEquals(21, VanillaStructurePlacement.mineshaftDepthRange(false));
   }

   @Test
   void allowsProtectedStructureToExtendBelowTerrainShell() {
      VanillaStructurePlacement.VerticalPlacementBounds bounds = VanillaStructurePlacement.verticalPlacementBounds(
         -40, 5, 20, -128, 255, 4, 8
      );

      assertEquals(-83, bounds.minOffsetY());
      assertEquals(7, bounds.maxOffsetY());
      assertTrue(bounds.canFit());
   }

   @Test
   void reportsWhenDimensionBoundsLeaveNoProtectedRoom() {
      VanillaStructurePlacement.VerticalPlacementBounds bounds = VanillaStructurePlacement.verticalPlacementBounds(
         -40, 100, 20, -64, 63, 4, 8
      );

      assertFalse(bounds.canFit());
   }
}
