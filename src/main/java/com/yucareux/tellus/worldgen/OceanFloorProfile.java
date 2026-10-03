package com.yucareux.tellus.worldgen;

import net.minecraft.util.Mth;

/** Shared full-terrain and DH ocean-floor safety profile. */
public final class OceanFloorProfile {
   public static final int DEFAULT_TRANSITION_BLOCKS = 512;
   public static final int MAX_TRANSITION_BLOCKS = 2048;
   public static final int TRIGGER_SCAN_BLOCKS = 64;
   public static final int MAX_DEPTH_INCREASE_PER_BLOCK = 4;
   public static final int CORRECTION_DILATION_BLOCKS = 16;
   public static final int MIN_OFFSHORE_DEPTH = 20;
   public static final int MIN_DEPTH = 1;

   private OceanFloorProfile() {
   }

   public static int clampTransitionDistance(int blocks) {
      return Mth.clamp(blocks, 0, MAX_TRANSITION_BLOCKS);
   }

   /**
    * Converts a distance authored in blocks at 1:1 scale into the block
    * distance for the selected world scale. At least one block is kept for
    * every positive distance because sub-block distances cannot be represented
    * by the terrain grid.
    */
   public static int scaleBlockDistance(int blocksAtOneToOne, double worldScale) {
      if (blocksAtOneToOne <= 0) {
         return 0;
      }

      double metersPerBlock = Double.isFinite(worldScale) && worldScale > 0.0
         ? Math.max(1.0, worldScale)
         : 1.0;
      return Math.max(1, (int)Math.round(blocksAtOneToOne / metersPerBlock));
   }

   public static int transitionBlocksForScale(int blocksAtOneToOne, double worldScale) {
      return scaleBlockDistance(clampTransitionDistance(blocksAtOneToOne), worldScale);
   }

   public static int minimumOffshoreDepthForScale(double worldScale) {
      return scaleBlockDistance(MIN_OFFSHORE_DEPTH, worldScale);
   }

   public static boolean shouldCorrect(
      boolean validBathymetry,
      int rawDepth,
      double distanceFromCoast,
      int adjacentCoastwardDepth,
      double cellSize
   ) {
      if (!validBathymetry) {
         return true;
      }

      double distance = Math.max(0.0, distanceFromCoast);
      double horizontalStep = Math.max(1.0, cellSize);
      if (rawDepth > MIN_DEPTH + MAX_DEPTH_INCREASE_PER_BLOCK * distance) {
         return true;
      }

      return adjacentCoastwardDepth > 0
         && rawDepth - adjacentCoastwardDepth > MAX_DEPTH_INCREASE_PER_BLOCK * horizontalStep;
   }

   public static int floorHeight(
      int seaLevel,
      int rawFloor,
      double distanceFromCoast,
      boolean correctionRequired,
      int transitionBlocks,
      double cellSize
   ) {
      return floorHeight(
         seaLevel,
         rawFloor,
         distanceFromCoast,
         correctionRequired,
         transitionBlocks,
         cellSize,
         MIN_OFFSHORE_DEPTH
      );
   }

   public static int floorHeight(
      int seaLevel,
      int rawFloor,
      double distanceFromCoast,
      boolean correctionRequired,
      int transitionBlocks,
      double cellSize,
      int minimumOffshoreDepth
   ) {
      int rawDepth = Math.max(MIN_DEPTH, seaLevel - Math.min(rawFloor, seaLevel - MIN_DEPTH));
      int offshoreMinimum = Math.max(MIN_DEPTH, minimumOffshoreDepth);
      boolean minimumDepthRequired = rawDepth < offshoreMinimum;
      int offshoreDepth = Math.max(offshoreMinimum, rawDepth);
      int safeRawFloor = seaLevel - offshoreDepth;
      int clampedTransition = clampTransitionDistance(transitionBlocks);
      if ((!correctionRequired && !minimumDepthRequired) || clampedTransition <= 0 || distanceFromCoast >= clampedTransition) {
         return safeRawFloor;
      }

      double t = smoothstep(Math.max(0.0, distanceFromCoast) / clampedTransition);
      int depth = Math.max(MIN_DEPTH, (int)Math.round(Mth.lerp(t, MIN_DEPTH, offshoreDepth)));
      return seaLevel - depth;
   }

   /**
    * Fits bathymetry into the vertical space available in the dimension while
    * retaining a monotonic relationship between raw depths. Shallow depths are
    * unchanged; deep values are logarithmically distributed instead of being
    * hard-clamped into a flat layer at the world bottom.
    */
   public static int fitFloorToWorld(
      int seaLevel,
      int profiledFloor,
      int minimumFloor,
      int expectedMaximumRawDepth
   ) {
      int maxDepth = Math.max(MIN_DEPTH, seaLevel - minimumFloor);
      int rawDepth = Math.max(MIN_DEPTH, seaLevel - Math.min(profiledFloor, seaLevel - MIN_DEPTH));
      int linearDepth = Math.min(48, Math.max(MIN_DEPTH, maxDepth / 3));
      int sourceMaximum = Math.max(linearDepth + 1, expectedMaximumRawDepth);
      if (maxDepth >= sourceMaximum) {
         return seaLevel - Math.min(rawDepth, maxDepth);
      }
      if (rawDepth <= linearDepth) {
         return seaLevel - rawDepth;
      }
      if (maxDepth <= linearDepth) {
         return seaLevel - maxDepth;
      }

      double compressionScale = Math.max(8.0, linearDepth);
      double numerator = Math.log1p((Math.min(rawDepth, sourceMaximum) - linearDepth) / compressionScale);
      double denominator = Math.log1p((sourceMaximum - linearDepth) / compressionScale);
      double t = denominator > 0.0 ? Mth.clamp(numerator / denominator, 0.0, 1.0) : 1.0;
      int fittedDepth = linearDepth + (int)Math.round(t * (maxDepth - linearDepth));
      return seaLevel - Mth.clamp(fittedDepth, MIN_DEPTH, maxDepth);
   }

   static double smoothstep(double value) {
      double t = Mth.clamp(value, 0.0, 1.0);
      return t * t * (3.0 - 2.0 * t);
   }
}
