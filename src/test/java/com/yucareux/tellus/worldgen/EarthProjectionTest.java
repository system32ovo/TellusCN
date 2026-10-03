package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EarthProjectionTest {
   @Test
   void mercatorHeightScaleIsSecantOfLatitude() {
      assertEquals(1.0, EarthProjection.heightScaleCorrectionAtLatitude(0.0), 1.0E-12);
      assertEquals(Math.sqrt(2.0), EarthProjection.heightScaleCorrectionAtLatitude(-45.0), 1.0E-12);
      assertEquals(2.0, EarthProjection.heightScaleCorrectionAtLatitude(60.0), 1.0E-12);
   }

   @Test
   void blockCorrectionIsInverseOfLocalMercatorGroundScale() {
      double worldScale = 30.0;
      double blockZ = EarthProjection.latToBlockZ(60.0, worldScale);
      double localGroundScale = EarthProjection.groundMetersPerBlockZ(blockZ, worldScale);

      assertEquals(15.0, localGroundScale, 1.0E-9);
      assertEquals(worldScale / localGroundScale, EarthProjection.heightScaleCorrection(blockZ, worldScale), 1.0E-12);
   }

   @Test
   void directBlockCorrectionMatchesLatitudeFormulaAcrossMercatorDomain() {
      double[] latitudes = {
         -EarthProjection.MAX_MERCATOR_LATITUDE,
         -80.0,
         -63.0695,
         -27.9881,
         0.0,
         27.9881,
         63.0695,
         80.0,
         EarthProjection.MAX_MERCATOR_LATITUDE
      };
      double[] worldScales = {1.0, 30.0, 1000.0};
      for (double worldScale : worldScales) {
         for (double latitude : latitudes) {
            double blockZ = EarthProjection.latToBlockZ(latitude, worldScale);
            assertEquals(
               EarthProjection.heightScaleCorrectionAtLatitude(latitude),
               EarthProjection.heightScaleCorrection(blockZ, worldScale),
               2.0E-9
            );
         }
      }
   }

   @Test
   void longitudeBlockConversionsRoundTripWithoutProjectionWork() {
      double[] worldScales = {1.0, 30.0, 1000.0};
      double[] longitudes = {-180.0, -105.2253, 0.0, 86.925, 180.0};
      for (double worldScale : worldScales) {
         for (double longitude : longitudes) {
            double blockX = EarthProjection.longitudeToBlockX(longitude, worldScale);
            assertEquals(longitude, EarthProjection.blockXToLongitude(blockX, worldScale), 1.0E-12);
         }
      }
   }
}
