package com.yucareux.tellus.worldgen;

/**
 * A task-local, direct-mapped cache for the sixteen horizontal biome quart
 * columns in a chunk.
 *
 * <p>The cache deliberately stores only the horizontal column input. Callers
 * must continue resolving the final biome for every Y coordinate so
 * surface-relative cave biome selection remains unchanged. Instances are
 * intended to be confined to one chunk-biome task and are not thread-safe.
 */
final class QuartBiomeColumnCache<T> {
   static final int QUARTS_PER_CHUNK_SIDE = 4;
   static final int COLUMN_COUNT = QUARTS_PER_CHUNK_SIDE * QUARTS_PER_CHUNK_SIDE;

   private final ColumnResolver<T> resolver;
   private final int[] quartXs = new int[COLUMN_COUNT];
   private final int[] quartZs = new int[COLUMN_COUNT];
   private final Object[] columns = new Object[COLUMN_COUNT];
   private final boolean[] initialized = new boolean[COLUMN_COUNT];
   private int resolvedColumnCount;

   QuartBiomeColumnCache(ColumnResolver<T> resolver) {
      this.resolver = resolver;
   }

   T resolve(int quartX, int quartZ) {
      int slot = slot(quartX, quartZ);
      if (!this.initialized[slot] || this.quartXs[slot] != quartX || this.quartZs[slot] != quartZ) {
         this.columns[slot] = this.resolver.resolve(quartX, quartZ);
         this.quartXs[slot] = quartX;
         this.quartZs[slot] = quartZ;
         this.initialized[slot] = true;
         this.resolvedColumnCount++;
      }

      @SuppressWarnings("unchecked")
      T column = (T)this.columns[slot];
      return column;
   }

   int resolvedColumnCount() {
      return this.resolvedColumnCount;
   }

   private static int slot(int quartX, int quartZ) {
      return (quartX & (QUARTS_PER_CHUNK_SIDE - 1)) * QUARTS_PER_CHUNK_SIDE
         + (quartZ & (QUARTS_PER_CHUNK_SIDE - 1));
   }

   @FunctionalInterface
   interface ColumnResolver<T> {
      T resolve(int quartX, int quartZ);
   }
}
