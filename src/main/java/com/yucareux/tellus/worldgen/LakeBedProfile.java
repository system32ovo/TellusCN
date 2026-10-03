package com.yucareux.tellus.worldgen;

/**
 * Shared, allocation-free lake-bed shaping for full terrain, previews, and
 * coarse LODs. The broad value-noise fields keep neighboring columns coherent
 * while the shore-distance ramp leaves a navigable shallow shelf.
 */
final class LakeBedProfile {
   private static final int SHORE_SHELF_END_BLOCKS = 5;
   private static final int SHALLOW_SLOPE_END_BLOCKS = 8;
   private static final int INNER_SHELF_END_BLOCKS = 10;
   private static final int INTERIOR_TRANSITION_BLOCKS = 32;
   private static final int INTERIOR_BASE_DEPTH = 11;
   private static final int INTERIOR_MIN_DEPTH = 8;
   private static final int INTERIOR_MAX_DEPTH = 14;
   private static final int PRIMARY_NOISE_CELL_BLOCKS = 96;
   private static final int SECONDARY_NOISE_CELL_BLOCKS = 40;
   private static final long PRIMARY_SALT = 0x2D358DCCAA6C78A5L;
   private static final long SECONDARY_SALT = 0x8BB84B93962EACC9L;

   private LakeBedProfile() {
   }

   static int depth(double shoreDistanceBlocks, int worldX, int worldZ) {
      if (!(shoreDistanceBlocks > SHORE_SHELF_END_BLOCKS)) {
         return 1;
      }
      if (shoreDistanceBlocks <= SHALLOW_SLOPE_END_BLOCKS) {
         return 3;
      }
      if (shoreDistanceBlocks <= INNER_SHELF_END_BLOCKS) {
         return 4;
      }

      double progress = smootherstep(
         (shoreDistanceBlocks - INNER_SHELF_END_BLOCKS) / INTERIOR_TRANSITION_BLOCKS
      );
      double primary = valueNoise(
         (long)worldX + worldZ,
         (long)worldZ - worldX,
         PRIMARY_NOISE_CELL_BLOCKS,
         PRIMARY_SALT
      );
      double secondary = valueNoise(worldX, worldZ, SECONDARY_NOISE_CELL_BLOCKS, SECONDARY_SALT);
      double targetDepth = INTERIOR_BASE_DEPTH + primary * 2.25 + secondary * 0.75;
      targetDepth = clamp(targetDepth, INTERIOR_MIN_DEPTH, INTERIOR_MAX_DEPTH);
      int depth = (int)Math.round(4.0 + (targetDepth - 4.0) * progress);
      return clamp(depth, 4, INTERIOR_MAX_DEPTH);
   }

   /**
    * Resolves a lake depth for a sampled LOD cell. A coarse cell represents a
    * footprint rather than the exact shoreline block, so its center is offset
    * by half the sample spacing and its floor cannot use the one-block shore
    * shelf. Without this scale-aware minimum, sparse coarse masks turn into
    * broad terrain slabs immediately below the water surface.
    */
   static int sampledDepth(int shoreDistanceCells, int sampleSpacingBlocks, int worldX, int worldZ) {
      int spacing = Math.max(1, sampleSpacingBlocks);
      double shoreDistanceBlocks = shoreDistanceCells < 0
         ? maximumShoreInfluenceBlocks()
         : (shoreDistanceCells + 0.5) * spacing;
      return Math.max(
         depth(shoreDistanceBlocks, worldX, worldZ),
         minimumSampledDepth(spacing)
      );
   }

   static int minimumSampledDepth(int sampleSpacingBlocks) {
      int spacing = Math.max(1, sampleSpacingBlocks);
      int footprintRadius = spacing / 2 + spacing % 2;
      return clamp(footprintRadius, 1, INTERIOR_MIN_DEPTH);
   }

   static int maximumShoreInfluenceBlocks() {
      return INNER_SHELF_END_BLOCKS + INTERIOR_TRANSITION_BLOCKS;
   }

   static int minimumInteriorDepth() {
      return INTERIOR_MIN_DEPTH;
   }

   static int maximumInteriorDepth() {
      return INTERIOR_MAX_DEPTH;
   }

   private static double valueNoise(long worldX, long worldZ, int cellBlocks, long salt) {
      long cellX = Math.floorDiv(worldX, cellBlocks);
      long cellZ = Math.floorDiv(worldZ, cellBlocks);
      double x = smootherstep(Math.floorMod(worldX, cellBlocks) / (double)cellBlocks);
      double z = smootherstep(Math.floorMod(worldZ, cellBlocks) / (double)cellBlocks);
      double north = lerp(x, signedHash(cellX, cellZ, salt), signedHash(cellX + 1L, cellZ, salt));
      double south = lerp(x, signedHash(cellX, cellZ + 1L, salt), signedHash(cellX + 1L, cellZ + 1L, salt));
      return lerp(z, north, south);
   }

   private static double signedHash(long x, long z, long salt) {
      long mixed = x * -7046029254386353131L ^ z * -4417276706812531889L ^ salt;
      mixed ^= mixed >>> 30;
      mixed *= -4658895280553007687L;
      mixed ^= mixed >>> 27;
      mixed *= -7723592293110705685L;
      mixed ^= mixed >>> 31;
      return (mixed >>> 11) * 0x1.0p-52 - 1.0;
   }

   private static double smootherstep(double value) {
      double t = clamp(value, 0.0, 1.0);
      return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
   }

   private static double lerp(double delta, double start, double end) {
      return start + delta * (end - start);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}
