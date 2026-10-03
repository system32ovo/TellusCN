package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepslateSlopePolicyTest {
   @Test
   void interpolatesConfiguredCoverageAnchors() {
      assertEquals(0.0, DeepslateSlopePolicy.coveragePercent(39.999), 1.0E-9);
      assertEquals(0.0, DeepslateSlopePolicy.coveragePercent(40.0), 1.0E-9);
      assertEquals(20.0, DeepslateSlopePolicy.coveragePercent(45.0), 1.0E-9);
      assertEquals(40.0, DeepslateSlopePolicy.coveragePercent(50.0), 1.0E-9);
      assertEquals(60.0, DeepslateSlopePolicy.coveragePercent(55.0), 1.0E-9);
      assertEquals(80.0, DeepslateSlopePolicy.coveragePercent(60.0), 1.0E-9);
      assertEquals(90.0, DeepslateSlopePolicy.coveragePercent(62.5), 1.0E-9);
      assertEquals(100.0, DeepslateSlopePolicy.coveragePercent(65.0), 1.0E-9);
      assertEquals(100.0, DeepslateSlopePolicy.coveragePercent(90.0), 1.0E-9);
   }

   @Test
   void missingOrGentleSlopeDoesNotCoverAndVerySteepSlopeAlwaysCovers() {
      assertFalse(DeepslateSlopePolicy.shouldCover(12, 34, Double.NaN));
      assertFalse(DeepslateSlopePolicy.shouldCover(12, 34, 40.0));
      assertTrue(DeepslateSlopePolicy.shouldCover(12, 34, 65.0));
   }

   @Test
   void coordinateSelectionIsStable() {
      boolean selected = DeepslateSlopePolicy.shouldCover(-12345, 67890, 55.0);
      assertEquals(selected, DeepslateSlopePolicy.shouldCover(-12345, 67890, 55.0));
   }

   @Test
   void extendsTheSurfaceThroughLocallyExposedCliffRelief() {
      assertEquals(4, DeepslateSlopePolicy.surfaceDepthForRelief(4, 0));
      assertEquals(4, DeepslateSlopePolicy.surfaceDepthForRelief(4, 3));
      assertEquals(5, DeepslateSlopePolicy.surfaceDepthForRelief(4, 4));
      assertEquals(81, DeepslateSlopePolicy.surfaceDepthForRelief(4, 80));
      assertEquals(65536, DeepslateSlopePolicy.surfaceDepthForRelief(4, Integer.MAX_VALUE));
   }
}
