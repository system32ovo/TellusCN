package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class QuartBiomeColumnCacheTest {
   @Test
   void resolvesEachHorizontalColumnOnceAcrossAllVerticalLayers() {
      AtomicInteger horizontalResolutions = new AtomicInteger();
      QuartBiomeColumnCache<String> cache = new QuartBiomeColumnCache<>((quartX, quartZ) -> {
         horizontalResolutions.incrementAndGet();
         return quartX + ":" + quartZ;
      });

      int chunkQuartX = -1204;
      int chunkQuartZ = 772;
      String shallow = null;
      String deep = null;
      int verticalResolutions = 0;
      for (int quartY = -32; quartY < 256; quartY++) {
         for (int localX = 0; localX < QuartBiomeColumnCache.QUARTS_PER_CHUNK_SIDE; localX++) {
            for (int localZ = 0; localZ < QuartBiomeColumnCache.QUARTS_PER_CHUNK_SIDE; localZ++) {
               String column = cache.resolve(chunkQuartX + localX, chunkQuartZ + localZ);
               String resolvedBiome = column + "@" + quartY;
               verticalResolutions++;
               if (localX == 1 && localZ == 2) {
                  if (quartY == -32) {
                     deep = resolvedBiome;
                  } else if (quartY == 255) {
                     shallow = resolvedBiome;
                  }
               }
            }
         }
      }

      assertEquals(QuartBiomeColumnCache.COLUMN_COUNT, horizontalResolutions.get());
      assertEquals(QuartBiomeColumnCache.COLUMN_COUNT, cache.resolvedColumnCount());
      assertEquals(QuartBiomeColumnCache.COLUMN_COUNT * 288, verticalResolutions);
      assertNotEquals(deep, shallow, "Y-dependent biome resolution must remain outside the horizontal cache");
   }

   @Test
   void directMappedSlotsRemainCorrectAcrossChunkBoundaries() {
      AtomicInteger horizontalResolutions = new AtomicInteger();
      QuartBiomeColumnCache<Long> cache = new QuartBiomeColumnCache<>((quartX, quartZ) -> {
         horizontalResolutions.incrementAndGet();
         return key(quartX, quartZ);
      });

      assertEquals(key(-1, -1), cache.resolve(-1, -1));
      assertEquals(key(3, 3), cache.resolve(3, 3));
      assertEquals(key(-1, -1), cache.resolve(-1, -1));
      assertEquals(3, horizontalResolutions.get(), "slot collisions must re-resolve instead of returning a stale column");
   }

   private static long key(int quartX, int quartZ) {
      return ((long)quartX << 32) ^ (quartZ & 0xffffffffL);
   }
}
