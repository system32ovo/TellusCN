package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class UndergroundPlacementSurfacePolicyTest {
   @Test
   void prefersTheGeneratedSurfaceOverAPostCarvingFallback() {
      int[] surfaces = indexedSurfaces();
      AtomicBoolean fallbackCalled = new AtomicBoolean();

      int surfaceY = UndergroundPlacementSurfacePolicy.resolve(
         surfaces,
         19,
         37,
         (x, z) -> {
            fallbackCalled.set(true);
            return -200;
         }
      );

      assertEquals(5 * 16 + 3, surfaceY);
      assertFalse(fallbackCalled.get());
   }

   @Test
   void indexesNegativeWorldCoordinatesWithinTheirChunk() {
      int[] surfaces = indexedSurfaces();

      assertEquals(
         15 * 16 + 15,
         UndergroundPlacementSurfacePolicy.resolve(surfaces, -1, -1, (x, z) -> Integer.MIN_VALUE)
      );
      assertEquals(
         14 * 16 + 13,
         UndergroundPlacementSurfacePolicy.resolve(surfaces, -3, -2, (x, z) -> Integer.MIN_VALUE)
      );
   }

   @Test
   void fallsBackToTheTerrainModelWhenNoCapturedSurfaceExists() {
      AtomicBoolean fallbackCalled = new AtomicBoolean();

      int surfaceY = UndergroundPlacementSurfacePolicy.resolve(
         null,
         -33,
         48,
         (x, z) -> {
            fallbackCalled.set(true);
            return x - z;
         }
      );

      assertEquals(-81, surfaceY);
      assertTrue(fallbackCalled.get());
   }

   private static int[] indexedSurfaces() {
      int[] surfaces = new int[16 * 16];
      for (int index = 0; index < surfaces.length; index++) {
         surfaces[index] = index;
      }
      return surfaces;
   }
}
