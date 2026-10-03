package com.yucareux.tellus.worldgen;

import net.minecraft.util.RandomSource;

/**
 * Preserves the expected number of placed ore features when vanilla's
 * underground profile is stretched into a deeper Tellus terrain shell.
 */
public final class OrePlacementDensityPolicy {
   private OrePlacementDensityPolicy() {
   }

   public static int placementSamples(int actualDepth, int virtualDepth, RandomSource random) {
      if (actualDepth <= virtualDepth || virtualDepth <= 0) {
         return 1;
      }

      double ratio = actualDepth / (double)virtualDepth;
      int samples = Math.max(1, (int)Math.floor(ratio));
      double remainder = ratio - samples;
      return remainder > 0.0 && random.nextDouble() < remainder ? samples + 1 : samples;
   }
}
