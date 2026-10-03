package com.yucareux.tellus.integration.distant_horizons;

/** Deterministic, detail-independent placement policy for aquatic DH columns. */
final class LodOceanVegetationPolicy {
   static final int PATCH_BLOCKS = 8;
   static final int MAX_CORAL_DEPTH = 48;
   private static final int VEGETATION_SALT = 1013904223;

   private LodOceanVegetationPolicy() {
   }

   static int patchHash(int worldX, int worldZ) {
      return mixHash(Math.floorDiv(worldX, PATCH_BLOCKS), Math.floorDiv(worldZ, PATCH_BLOCKS), VEGETATION_SALT);
   }

   static boolean shouldPlace(int chancePercent, int patchHash) {
      if (chancePercent <= 0) {
         return false;
      }
      if (chancePercent >= 100) {
         return true;
      }

      int roll = patchHash >>> 24 & 0xFF;
      return roll < chancePercent * 255 / 100;
   }

   static boolean shouldUseCoral(boolean warmOcean, int waterDepth, int patchHash) {
      if (!warmOcean || waterDepth < 2 || waterDepth > MAX_CORAL_DEPTH) {
         return false;
      }

      return (patchHash >>> 16 & 0xFF) < 178; // roughly 70% of warm-ocean vegetation patches
   }

   static int columnHeight(boolean coral, int waterDepth, int patchHash) {
      int maxHeight = Math.min(coral ? 3 : 4, Math.max(0, waterDepth - 1));
      if (maxHeight <= 0) {
         return 0;
      }

      int requested = coral ? 1 + (patchHash >>> 12 & 1) : 1 + (patchHash >>> 12 & 3);
      return Math.min(requested, maxHeight);
   }

   static int coralVariant(int patchHash) {
      return Math.floorMod(patchHash >>> 8, 5);
   }

   private static int mixHash(int worldX, int worldZ, int seed) {
      int hash = worldX * 522133279 ^ worldZ * -1640531527 ^ seed * 668265261;
      hash ^= hash >>> 15;
      hash *= -2048144789;
      hash ^= hash >>> 13;
      hash *= -1028477387;
      return hash ^ hash >>> 16;
   }
}
