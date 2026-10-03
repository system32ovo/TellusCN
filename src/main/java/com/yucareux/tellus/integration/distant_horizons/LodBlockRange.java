package com.yucareux.tellus.integration.distant_horizons;

/** Inclusive block bounds covered by a Distant Horizons data source. */
record LodBlockRange(int minX, int minZ, int maxX, int maxZ) {
   static LodBlockRange forDhTile(int chunkPosMinX, int chunkPosMinZ, int detailLevel, int dataColumnWidth) {
      if (detailLevel < 0 || detailLevel > 30) {
         throw new IllegalArgumentException("LOD detail level must be between 0 and 30.");
      }
      if (dataColumnWidth <= 0) {
         throw new IllegalArgumentException("LOD data-column width must be positive.");
      }

      long minBlockX = (long)chunkPosMinX * 16L;
      long minBlockZ = (long)chunkPosMinZ * 16L;
      long span = (long)dataColumnWidth * (1L << detailLevel);
      return new LodBlockRange(
         clampToInt(minBlockX),
         clampToInt(minBlockZ),
         clampToInt(minBlockX + span - 1L),
         clampToInt(minBlockZ + span - 1L)
      );
   }

   private static int clampToInt(long value) {
      return (int)Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
   }
}
