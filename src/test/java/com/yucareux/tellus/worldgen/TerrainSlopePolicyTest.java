package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSlopePolicyTest {
   @Test
   void measuresPlanarSlopeFromBothSides() {
      assertEquals(
         45.0,
         TerrainSlopePolicy.localSlopeDegrees(0.0, 30.0, -30.0, 0.0, 0.0, 30.0, 30.0),
         1.0E-9
      );
   }

   @Test
   void preservesSteepExposureAtRidgesAndBowls() {
      assertEquals(
         45.0,
         TerrainSlopePolicy.localSlopeDegrees(30.0, 0.0, 0.0, 30.0, 30.0, 30.0, 30.0),
         1.0E-9
      );
      assertEquals(
         45.0,
         TerrainSlopePolicy.localSlopeDegrees(0.0, 30.0, 30.0, 0.0, 0.0, 30.0, 30.0),
         1.0E-9
      );
   }

   @Test
   void usesAvailableSidesWhenNeighborSamplesAreMissing() {
      assertEquals(
         45.0,
         TerrainSlopePolicy.localSlopeDegrees(0.0, 30.0, Double.NaN, Double.NaN, Double.NaN, 30.0, 30.0),
         1.0E-9
      );
      assertTrue(
         Double.isNaN(
            TerrainSlopePolicy.localSlopeDegrees(
               0.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 30.0, 30.0
            )
         )
      );
   }
}
