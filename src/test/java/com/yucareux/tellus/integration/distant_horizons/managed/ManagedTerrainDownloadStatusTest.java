package com.yucareux.tellus.integration.distant_horizons.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ManagedTerrainDownloadStatusTest {
   @Test
   void includesKnownByteProgressForActiveCells() {
      ManagedTerrainDownloadStatus status = new ManagedTerrainDownloadStatus(
         ManagedTerrainDownloadStatus.Stage.DOWNLOADING,
         10,
         100,
         20,
         0,
         0,
         50L,
         100L,
         512,
         64,
         "Downloading"
      );

      assertEquals(0.2, status.progress(), 1.0E-9);
   }

   @Test
   void ignoresActiveCellsWhenRequestSizesAreUnknown() {
      ManagedTerrainDownloadStatus status = new ManagedTerrainDownloadStatus(
         ManagedTerrainDownloadStatus.Stage.DOWNLOADING,
         10,
         100,
         20,
         0,
         0,
         50L,
         -1L,
         512,
         64,
         "Downloading"
      );

      assertEquals(0.1, status.progress(), 1.0E-9);
   }
}
