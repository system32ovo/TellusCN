package com.yucareux.tellus.integration.distant_horizons.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ManagedTerrainAvailabilityTest {
   private static final String KEY = "test";

   @AfterEach
   void clear() {
      ManagedTerrainAvailability.clearAll();
   }

   @Test
   void waitsForTheRestOfADownloadBatchThenAllowsGeneration() {
      assertEquals(ManagedTerrainAvailability.WAIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 64));
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(0, 0), false);
      assertEquals(ManagedTerrainAvailability.WAIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 64));
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(1, 0), false);
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(0, 1), false);
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(1, 1), true);
      assertEquals(ManagedTerrainAvailability.READY, ManagedTerrainAvailability.availability(KEY, 0, 0, 64));
   }

   @Test
   void splitsOnlyRequestsLargerThanTheProgressiveDownloadBatch() {
      assertEquals(256, ManagedTerrainAvailability.DEFAULT_PROGRESSIVE_BATCH_WIDTH_CHUNKS);
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(0, 0), false);
      assertEquals(ManagedTerrainAvailability.WAIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 256));
      assertEquals(ManagedTerrainAvailability.SPLIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 512));
   }

   @Test
   void adaptiveBatchWidthPreventsFineSplitsAtMaximumDistance() {
      ManagedTerrainAvailability.configureProgressiveBatchWidth(KEY, 2048);
      ManagedTerrainAvailability.markReady(KEY, new ManagedTerrainCell(0, 0), false);
      assertEquals(ManagedTerrainAvailability.WAIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 2048));
      assertEquals(ManagedTerrainAvailability.SPLIT, ManagedTerrainAvailability.availability(KEY, 0, 0, 4096));
   }
}
