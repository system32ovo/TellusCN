package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LodSamplingGridTest {
   @Test
   void preservesEveryDhColumnAcrossTheSupported4096ChunkRadius() {
      assertEquals(4096, LodSamplingGrid.MAX_RENDER_DISTANCE_CHUNKS);
      for (int detail = 0; detail <= LodSamplingGrid.FULL_QUALITY_MAX_DETAIL; detail++) {
         assertEquals(1, LodSamplingGrid.terrainStrideForDetail(detail, 8, 64));
      }
      assertEquals(2, LodSamplingGrid.terrainStrideForDetail(12, 8, 64));
   }

   @Test
   void samplingRampsWithDetailAndStopsAtConfiguredCap() {
      assertEquals(1, LodSamplingGrid.strideForDetail(4, 5, 8, 64));
      assertEquals(2, LodSamplingGrid.strideForDetail(5, 5, 8, 64));
      assertEquals(4, LodSamplingGrid.strideForDetail(6, 5, 8, 64));
      assertEquals(8, LodSamplingGrid.strideForDetail(7, 5, 8, 64));
      assertEquals(8, LodSamplingGrid.strideForDetail(20, 5, 8, 64));
   }

   @Test
   void strideIsPowerOfTwoAndAlwaysTilesTheOutputGrid() {
      assertEquals(4, LodSamplingGrid.strideForDetail(20, 5, 7, 64));
      assertEquals(2, LodSamplingGrid.strideForDetail(20, 5, 8, 10));
      assertEquals(5, LodSamplingGrid.sampleWidth(10, 2));
   }

   @Test
   void sampleWidthRejectsInvalidGridShapes() {
      assertThrows(IllegalArgumentException.class, () -> LodSamplingGrid.sampleWidth(0, 1));
      assertThrows(IllegalArgumentException.class, () -> LodSamplingGrid.sampleWidth(10, 4));
   }

   @Test
   void coarseBlocksCoverEveryOutputColumnExactlyOnce() {
      int outputWidth = 64;
      int stride = LodSamplingGrid.strideForDetail(7, 5, 8, outputWidth);
      int sampleWidth = LodSamplingGrid.sampleWidth(outputWidth, stride);
      boolean[] covered = new boolean[outputWidth * outputWidth];

      for (int sampleZ = 0; sampleZ < sampleWidth; sampleZ++) {
         for (int sampleX = 0; sampleX < sampleWidth; sampleX++) {
            for (int outputZ = sampleZ * stride; outputZ < (sampleZ + 1) * stride; outputZ++) {
               for (int outputX = sampleX * stride; outputX < (sampleX + 1) * stride; outputX++) {
                  int index = outputZ * outputWidth + outputX;
                  assertFalse(covered[index], "Output column was covered more than once: " + outputX + "," + outputZ);
                  covered[index] = true;
               }
            }
         }
      }

      for (boolean columnCovered : covered) {
         assertTrue(columnCovered, "Coarse sampling left an output column uncovered.");
      }
   }
}
