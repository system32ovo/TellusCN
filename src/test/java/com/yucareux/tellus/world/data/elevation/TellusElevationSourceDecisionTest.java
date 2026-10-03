package com.yucareux.tellus.world.data.elevation;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellusElevationSourceDecisionTest {
   @Test
   void usesBathymetryOnlyForOceanMaskAtOrBelowThreshold() {
      assertFalse(TellusElevationSource.shouldUseBathymetry(false, -20.0));
      assertFalse(TellusElevationSource.shouldUseBathymetry(false, 0.0));
      assertFalse(TellusElevationSource.shouldUseBathymetry(false, 12.0));
      assertFalse(TellusElevationSource.shouldUseBathymetry(false, Double.NaN));

      assertTrue(TellusElevationSource.shouldUseBathymetry(true, -20.0));
      assertTrue(TellusElevationSource.shouldUseBathymetry(true, 0.0));
      assertTrue(TellusElevationSource.shouldUseBathymetry(true, Double.NaN));
      assertFalse(TellusElevationSource.shouldUseBathymetry(true, 0.01));
   }

   @Test
   void doesNotSampleOpenWatersForPositiveMapterhornTerrain() {
      AtomicInteger calls = new AtomicInteger();

      double selected = TellusElevationSource.resolveElevationMeters(
         true,
         35.0,
         () -> {
            calls.incrementAndGet();
            return -300.0;
         },
         0.0
      );

      assertEquals(35.0, selected);
      assertEquals(0, calls.get());
   }

   @Test
   void samplesOpenWatersForMissingZeroAndNegativeMapterhornTerrain() {
      AtomicInteger calls = new AtomicInteger();

      assertEquals(-400.0, resolveOcean(Double.NaN, calls));
      assertEquals(-400.0, resolveOcean(0.0, calls));
      assertEquals(-400.0, resolveOcean(-1.0, calls));
      assertEquals(3, calls.get());
   }

   @Test
   void retainsMapterhornOrLandFallbackWhenOpenWatersIsMissing() {
      assertEquals(-8.0, TellusElevationSource.resolveElevationMeters(true, -8.0, () -> Double.NaN, 0.0));
      assertEquals(0.0, TellusElevationSource.resolveElevationMeters(true, Double.NaN, () -> Double.NaN, 0.0));
      assertEquals(9.0, TellusElevationSource.resolveElevationMeters(false, 9.0, () -> -100.0, 0.0));
   }

   @Test
   void marksOnlyAvailablePositiveMapterhornAsLandOverride() {
      TellusElevationSource.ResolvedElevationSample positive = sample(true, 1.0);
      TellusElevationSource.ResolvedElevationSample zero = sample(true, 0.0);
      TellusElevationSource.ResolvedElevationSample missing = sample(false, Double.NaN);

      assertTrue(positive.mapterhornLandOverride());
      assertFalse(zero.mapterhornLandOverride());
      assertFalse(missing.mapterhornLandOverride());
   }

   @Test
   void previewRejectsMissingTerrainFallbackButAcceptsRealTerrainAndBathymetry() {
      TellusElevationSource.ResolvedElevationSample missingTerrain = new TellusElevationSource.ResolvedElevationSample(
         0.0,
         TellusElevationSource.DemUsage.TERRAIN_TILES,
         TellusElevationSource.DemUsage.TERRAIN_TILES.bit(),
         30.0,
         false,
         Double.NaN
      );
      TellusElevationSource.ResolvedElevationSample terrain = sample(true, 125.0);
      TellusElevationSource.ResolvedElevationSample bathymetry = new TellusElevationSource.ResolvedElevationSample(
         -340.0,
         TellusElevationSource.DemUsage.OPENWATERS,
         TellusElevationSource.DemUsage.OPENWATERS.bit(),
         463.0,
         false,
         Double.NaN
      );

      assertFalse(TellusElevationSource.isUsablePreviewElevation(missingTerrain));
      assertTrue(TellusElevationSource.isUsablePreviewElevation(terrain));
      assertTrue(TellusElevationSource.isUsablePreviewElevation(bathymetry));
   }

   @Test
   void doesNotWalkParentsForGlobalMapterhornZooms() {
      assertEquals(12, TellusElevationSource.mapterhornMinimumFallbackZoom(18));
      assertEquals(12, TellusElevationSource.mapterhornMinimumFallbackZoom(13));
      assertEquals(12, TellusElevationSource.mapterhornMinimumFallbackZoom(12));
      assertEquals(4, TellusElevationSource.mapterhornMinimumFallbackZoom(4));
   }

   @Test
   void treatsOnlyMissingGlobalMapterhornTilesAsZeroElevation() {
      assertTrue(TellusElevationSource.mapterhornMissingTileRepresentsZero(0));
      assertTrue(TellusElevationSource.mapterhornMissingTileRepresentsZero(12));
      assertFalse(TellusElevationSource.mapterhornMissingTileRepresentsZero(13));
      assertFalse(TellusElevationSource.mapterhornMissingTileRepresentsZero(-1));
   }

   @Test
   void lowersPreviewSourceZoomAsLodCellsGrow() {
      assertEquals(16, TellusElevationSource.selectPreviewZoom(1.0, 1.0, 18));
      assertEquals(12, TellusElevationSource.selectPreviewZoom(1.0, 16.0, 18));
      assertEquals(8, TellusElevationSource.selectPreviewZoom(1.0, 256.0, 18));
      assertEquals(4, TellusElevationSource.selectPreviewZoom(1.0, 4096.0, 18));
   }

   @Test
   void previewZoomUsesWorldScaleForFullResolutionAndHonorsProviderLimit() {
      assertEquals(14, TellusElevationSource.selectPreviewZoom(4.0, Double.NaN, 18));
      assertEquals(6, TellusElevationSource.selectPreviewZoom(1000.0, Double.NaN, 18));
      assertEquals(12, TellusElevationSource.selectPreviewZoom(1.0, 1.0, 12));
   }

   private static double resolveOcean(double mapterhorn, AtomicInteger calls) {
      return TellusElevationSource.resolveElevationMeters(
         true,
         mapterhorn,
         () -> {
            calls.incrementAndGet();
            return -400.0;
         },
         0.0
      );
   }

   private static TellusElevationSource.ResolvedElevationSample sample(boolean available, double mapterhorn) {
      return new TellusElevationSource.ResolvedElevationSample(
         available ? mapterhorn : 0.0,
         TellusElevationSource.DemUsage.TERRAIN_TILES,
         TellusElevationSource.DemUsage.TERRAIN_TILES.bit(),
         30.0,
         available,
         mapterhorn
      );
   }
}
