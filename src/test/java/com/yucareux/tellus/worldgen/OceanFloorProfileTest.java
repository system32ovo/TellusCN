package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OceanFloorProfileTest {
   @Test
   void keepsTransitionInRealWorldMetersAcrossSupportedScales() {
      assertEquals(512, OceanFloorProfile.transitionBlocksForScale(512, 1.0));
      assertEquals(256, OceanFloorProfile.transitionBlocksForScale(512, 2.0));
      assertEquals(51, OceanFloorProfile.transitionBlocksForScale(512, 10.0));
      assertEquals(17, OceanFloorProfile.transitionBlocksForScale(512, 30.0));
      assertEquals(5, OceanFloorProfile.transitionBlocksForScale(512, 100.0));
      assertEquals(1, OceanFloorProfile.transitionBlocksForScale(512, 1000.0));
      assertEquals(0, OceanFloorProfile.transitionBlocksForScale(0, 100.0));
   }

   @Test
   void scalesOffshoreMinimumDepthWithWorldScale() {
      assertEquals(20, OceanFloorProfile.minimumOffshoreDepthForScale(1.0));
      assertEquals(2, OceanFloorProfile.minimumOffshoreDepthForScale(10.0));
      assertEquals(1, OceanFloorProfile.minimumOffshoreDepthForScale(30.0));
      assertEquals(1, OceanFloorProfile.minimumOffshoreDepthForScale(100.0));

      int transitionBlocks = OceanFloorProfile.transitionBlocksForScale(512, 100.0);
      assertEquals(
         58,
         OceanFloorProfile.floorHeight(
            63,
            58,
            transitionBlocks,
            false,
            transitionBlocks,
            1.0,
            OceanFloorProfile.minimumOffshoreDepthForScale(100.0)
         )
      );
   }

   @Test
   void naturallyShallowProfileRampsToMinimumOffshoreDepth() {
      assertFalse(OceanFloorProfile.shouldCorrect(true, 5, 1.0, 1, 1.0));
      assertEquals(62, OceanFloorProfile.floorHeight(63, 58, 0.0, false, 512, 1.0));
      assertEquals(43, OceanFloorProfile.floorHeight(63, 58, 512.0, false, 512, 1.0));
      assertEquals(43, OceanFloorProfile.floorHeight(63, 58, 700.0, false, 512, 1.0));
   }

   @Test
   void bathymetryDeeperThanOffshoreMinimumRemainsUnchangedWithoutCorrection() {
      assertEquals(13, OceanFloorProfile.floorHeight(63, 13, 20.0, false, 512, 1.0));
   }

   @Test
   void abruptProfileUsesOneBlockCoastAndReachesRawAtTransitionEnd() {
      assertTrue(OceanFloorProfile.shouldCorrect(true, 120, 1.0, 1, 1.0));
      assertEquals(62, OceanFloorProfile.floorHeight(63, -57, 0.0, true, 512, 1.0));
      assertEquals(-57, OceanFloorProfile.floorHeight(63, -57, 512.0, true, 512, 1.0));
      assertEquals(-57, OceanFloorProfile.floorHeight(63, -57, 700.0, true, 512, 1.0));
   }

   @Test
   void invalidBathymetryAlwaysRequestsCorrectionAndCannotRiseAboveWater() {
      assertTrue(OceanFloorProfile.shouldCorrect(false, 1, 0.0, 0, 1.0));
      assertEquals(62, OceanFloorProfile.floorHeight(63, 90, 0.0, true, 512, 1.0));
   }

   @Test
   void adjacentOffshoreDropUsesDhCellFootprintAsThreshold() {
      assertTrue(OceanFloorProfile.shouldCorrect(true, 10, 8.0, 1, 1.0));
      assertFalse(OceanFloorProfile.shouldCorrect(true, 10, 8.0, 1, 4.0));
   }

   @Test
   void fitsDeepBathymetryIntoWorldWithoutFlatteningDistinctDepths() {
      int shallow = OceanFloorProfile.fitFloorToWorld(0, -3000, -120, 11034);
      int medium = OceanFloorProfile.fitFloorToWorld(0, -5000, -120, 11034);
      int deep = OceanFloorProfile.fitFloorToWorld(0, -7000, -120, 11034);

      assertTrue(shallow > medium);
      assertTrue(medium > deep);
      assertTrue(deep >= -120);
   }

   @Test
   void keepsShallowShelfDepthUnchangedAndReservesSeabedSupport() {
      assertEquals(-20, OceanFloorProfile.fitFloorToWorld(0, -20, -120, 11034));
      assertEquals(8, WaterSurfaceResolver.oceanFloorSupportBlocks());
      assertTrue(OceanFloorProfile.fitFloorToWorld(0, -11034, -120, 11034) >= -120);
   }
}
