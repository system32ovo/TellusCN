package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.mask.TellusLandMaskSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OceanClassificationTest {
   @Test
   void classifiesMarineWaterWithoutOceanHintAsOcean() {
      boolean ocean = OceanClassification.isOcean(false, TellusLandMaskSource.LandMaskSample.known(false), 62, 80, 63);

      assertTrue(ocean);
   }

   @Test
   void classifiesKnownOceanNoDataAboveSeaAsOceanFallback() {
      boolean ocean = OceanClassification.isOcean(false, TellusLandMaskSource.LandMaskSample.known(false), 80, 0, 63);

      assertTrue(ocean);
   }

   @Test
   void keepsBelowSeaInlandWaterAsInland() {
      boolean ocean = OceanClassification.isOcean(false, TellusLandMaskSource.LandMaskSample.known(true), 62, 80, 63);

      assertFalse(ocean);
   }

   @Test
   void strictOvertureRejectsEsaMarineWaterWithoutOsmHint() {
      boolean ocean = OceanClassification.isOcean(false, true, TellusLandMaskSource.LandMaskSample.known(false), 62, 80, 63);

      assertFalse(ocean);
   }

   @Test
   void strictOvertureRejectsUnhintedUnknownWater() {
      boolean ocean = OceanClassification.isOcean(false, true, TellusLandMaskSource.LandMaskSample.unknown(), 62, 80, 63);

      assertFalse(ocean);
   }

   @Test
   void strictOvertureKeepsNegativeDemOutsideOceanOnLand() {
      boolean ocean = OceanClassification.isOcean(false, true, TellusLandMaskSource.LandMaskSample.known(false), -120, 0, 63);

      assertFalse(ocean);
   }

   @Test
   void strictOvertureStillAcceptsOceanHint() {
      boolean ocean = OceanClassification.isOcean(true, true, TellusLandMaskSource.LandMaskSample.known(true), 80, 80, 63);

      assertTrue(ocean);
   }

   @Test
   void positiveMapterhornCannotRejectOvertureOceanHint() {
      boolean ocean = OceanClassification.isOcean(
         true, true, TellusLandMaskSource.LandMaskSample.known(false), 80, 80, 63, true
      );

      assertTrue(ocean);
   }

   @Test
   void positiveMapterhornDoesNotSuppressInlandWaterWithoutOceanMask() {
      assertFalse(WaterSurfaceResolver.suppressOceanWater(false, true));
      assertFalse(WaterSurfaceResolver.suppressOceanWater(true, true));
   }

   @Test
   void oceanSafetyRampDefaultsToFiveHundredTwelveBlocks() {
      assertEquals(512, WaterSurfaceResolver.oceanFloorTransitionBlocks());
   }

   @Test
   void touchingOvertureRiverAndOceanRemainSeparateComponents() {
      assertFalse(WaterSurfaceResolver.canConnectWaterComponentCells(true, false, true));
      assertFalse(WaterSurfaceResolver.canConnectWaterComponentCells(true, true, false));
      assertTrue(WaterSurfaceResolver.canConnectWaterComponentCells(true, false, false));
      assertTrue(WaterSurfaceResolver.canConnectWaterComponentCells(true, true, true));
   }
}
