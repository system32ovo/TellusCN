package com.yucareux.tellus.worldgen.caves;

import com.yucareux.tellus.worldgen.UndergroundGenerationDepthPolicy;

/**
 * Defines where cave biomes may exist inside Tellus's configured
 * surface-relative underground shell.
 */
public final class TellusCaveBiomeDepthPolicy {
   public static final int MIN_CAVE_BIOME_DEPTH = 8;
   public static final int MIN_DEEP_DARK_DEPTH = 24;
   public static final int NO_STRUCTURE_PROBE_DEPTH = -1;
   private static final int PREFERRED_STRUCTURE_PROBE_DEPTH = 104;
   private static final int STRUCTURE_PROBE_BOTTOM_CLEARANCE = 16;

   private TellusCaveBiomeDepthPolicy() {
   }

   /**
    * Returns whether a depth is inside the generation band and far enough
    * below the local terrain surface for cave biomes.
    */
   public static boolean isCaveBiomeDepth(int depthBelowSurface, int undergroundDepth) {
      return depthBelowSurface >= MIN_CAVE_BIOME_DEPTH
         && UndergroundGenerationDepthPolicy.containsDepth(depthBelowSurface, undergroundDepth);
   }

   /**
    * Keeps Deep Dark in the deeper portion of the local generation band
    * without tying it to an absolute world Y.
    */
   public static boolean isDeepDarkDepth(int depthBelowSurface, int undergroundDepth) {
      return depthBelowSurface >= MIN_DEEP_DARK_DEPTH
         && UndergroundGenerationDepthPolicy.containsDepth(depthBelowSurface, undergroundDepth);
   }

   /**
    * Chooses a stable Deep Dark depth for vanilla structure biome checks that
    * originate below Tellus's generation band. The probe remains far enough
    * from its protected floor for large structures while matching their
    * eventual local underground placement.
    */
   public static int structureProbeDepth(int undergroundDepth) {
      int generationDepth = UndergroundGenerationDepthPolicy.generationDepth(undergroundDepth);
      int deepestProbeDepth = generationDepth - STRUCTURE_PROBE_BOTTOM_CLEARANCE;
      if (deepestProbeDepth < MIN_DEEP_DARK_DEPTH) {
         return NO_STRUCTURE_PROBE_DEPTH;
      }

      return Math.min(PREFERRED_STRUCTURE_PROBE_DEPTH, deepestProbeDepth);
   }

   /**
    * Normalizes a cave-biome depth against the usable shell so deeper worlds
    * do not spend most of their underground interval at a saturated depth.
    */
   public static double normalizedDepthFactor(int depthBelowSurface, int undergroundDepth) {
      int usableRange = UndergroundGenerationDepthPolicy.generationDepth(undergroundDepth) - MIN_CAVE_BIOME_DEPTH;
      if (usableRange <= 0) {
         return 0.0;
      }
      return Math.max(0.0, Math.min(1.0, (depthBelowSurface - MIN_CAVE_BIOME_DEPTH) / (double)usableRange));
   }
}
