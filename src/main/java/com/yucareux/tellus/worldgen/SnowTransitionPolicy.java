package com.yucareux.tellus.worldgen;

public final class SnowTransitionPolicy {
   public static final int MAX_EDGE_DISPLACEMENT_BLOCKS = 48;
   private static final int SECONDARY_EDGE_DISPLACEMENT_BLOCKS = 28;
   private static final int PRIMARY_WARP_CELL_BLOCKS = 96;
   private static final int SECONDARY_WARP_CELL_BLOCKS = 43;
   private static final int TRANSITION_DETAIL_CELL_BLOCKS = 11;
   private static final int TRANSITION_PATCH_CELL_BLOCKS = 37;
   private static final double LOCAL_SOURCE_WEIGHT = 0.45;
   private static final double PRIMARY_SOURCE_WEIGHT = 0.35;
   private static final double SECONDARY_SOURCE_WEIGHT = 0.20;
   private static final long PRIMARY_WARP_X_SALT = 3476291847715014363L;
   private static final long PRIMARY_WARP_Z_SALT = 8361902749107219433L;
   private static final long SECONDARY_WARP_X_SALT = 5516042115276107717L;
   private static final long SECONDARY_WARP_Z_SALT = 6792168434296017429L;
   private static final long TRANSITION_DETAIL_SALT = 2145517839928346117L;
   private static final long TRANSITION_PATCH_SALT = 6417398719980302667L;

   private SnowTransitionPolicy() {
   }

   public static boolean shouldCover(
      int worldX,
      int worldZ,
      double slopeDegrees,
      boolean localSnowSource,
      SnowTransitionPolicy.SourceSampler sourceSampler,
      long worldSeed
   ) {
      double sourceCoverage = sampleSourceCoverage(worldX, worldZ, localSnowSource, sourceSampler, worldSeed);
      if (sourceCoverage <= 0.0 || !SnowSlopePolicy.shouldCover(worldX, worldZ, slopeDegrees)) {
         return false;
      } else if (sourceCoverage >= 1.0) {
         return true;
      }

      double transitionProbability = smoothStep(sourceCoverage);
      return transitionMask(worldX, worldZ, worldSeed) < transitionProbability;
   }

   static double sampleSourceCoverage(
      int worldX,
      int worldZ,
      boolean localSnowSource,
      SnowTransitionPolicy.SourceSampler sourceSampler,
      long worldSeed
   ) {
      if (sourceSampler == null) {
         throw new IllegalArgumentException("sourceSampler");
      }

      int primaryX = worldX
         + warpOffset(worldX, worldZ, PRIMARY_WARP_CELL_BLOCKS, MAX_EDGE_DISPLACEMENT_BLOCKS, worldSeed, PRIMARY_WARP_X_SALT);
      int primaryZ = worldZ
         + warpOffset(worldX, worldZ, PRIMARY_WARP_CELL_BLOCKS, MAX_EDGE_DISPLACEMENT_BLOCKS, worldSeed, PRIMARY_WARP_Z_SALT);
      int secondaryX = worldX
         + warpOffset(
            worldX,
            worldZ,
            SECONDARY_WARP_CELL_BLOCKS,
            SECONDARY_EDGE_DISPLACEMENT_BLOCKS,
            worldSeed,
            SECONDARY_WARP_X_SALT
         );
      int secondaryZ = worldZ
         + warpOffset(
            worldX,
            worldZ,
            SECONDARY_WARP_CELL_BLOCKS,
            SECONDARY_EDGE_DISPLACEMENT_BLOCKS,
            worldSeed,
            SECONDARY_WARP_Z_SALT
         );
      double coverage = localSnowSource ? LOCAL_SOURCE_WEIGHT : 0.0;
      if (sourceSampler.hasSnowSource(primaryX, primaryZ)) {
         coverage += PRIMARY_SOURCE_WEIGHT;
      }
      if (sourceSampler.hasSnowSource(secondaryX, secondaryZ)) {
         coverage += SECONDARY_SOURCE_WEIGHT;
      }
      return coverage;
   }

   private static int warpOffset(
      int worldX, int worldZ, int cellSize, int amplitude, long worldSeed, long salt
   ) {
      double broad = smoothValueNoise(worldX, worldZ, cellSize, worldSeed, salt);
      double detail = smoothValueNoise(
         worldX + 31,
         worldZ - 47,
         Math.max(7, cellSize / 3),
         worldSeed,
         salt ^ TRANSITION_DETAIL_SALT
      );
      double displacement = (broad * 0.72 + detail * 0.28) * 2.0 - 1.0;
      return (int)Math.round(displacement * amplitude);
   }

   private static double transitionMask(int worldX, int worldZ, long worldSeed) {
      double detail = smoothValueNoise(
         worldX, worldZ, TRANSITION_DETAIL_CELL_BLOCKS, worldSeed, TRANSITION_DETAIL_SALT
      );
      double patches = smoothValueNoise(
         worldX - 19, worldZ + 23, TRANSITION_PATCH_CELL_BLOCKS, worldSeed, TRANSITION_PATCH_SALT
      );
      return detail * 0.62 + patches * 0.38;
   }

   private static double smoothValueNoise(
      int worldX, int worldZ, int cellSize, long worldSeed, long salt
   ) {
      int cellX = Math.floorDiv(worldX, cellSize);
      int cellZ = Math.floorDiv(worldZ, cellSize);
      double fractionX = fade((double)Math.floorMod(worldX, cellSize) / (double)cellSize);
      double fractionZ = fade((double)Math.floorMod(worldZ, cellSize) / (double)cellSize);
      double northWest = cellNoise(cellX, cellZ, worldSeed, salt);
      double northEast = cellNoise(cellX + 1, cellZ, worldSeed, salt);
      double southWest = cellNoise(cellX, cellZ + 1, worldSeed, salt);
      double southEast = cellNoise(cellX + 1, cellZ + 1, worldSeed, salt);
      double north = lerp(fractionX, northWest, northEast);
      double south = lerp(fractionX, southWest, southEast);
      return lerp(fractionZ, north, south);
   }

   private static double cellNoise(int cellX, int cellZ, long worldSeed, long salt) {
      long mixed = mix64(worldSeed ^ salt ^ (long)cellX * 341873128712L ^ (long)cellZ * 132897987541L);
      return (double)(mixed >>> 11) * 0x1.0p-53;
   }

   private static double fade(double value) {
      return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
   }

   private static double smoothStep(double value) {
      double clamped = Math.max(0.0, Math.min(1.0, value));
      return clamped * clamped * (3.0 - 2.0 * clamped);
   }

   private static double lerp(double delta, double start, double end) {
      return start + delta * (end - start);
   }

   private static long mix64(long value) {
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      value *= -4265267296055464877L;
      return value ^ value >>> 33;
   }

   @FunctionalInterface
   public interface SourceSampler {
      boolean hasSnowSource(int worldX, int worldZ);
   }
}
