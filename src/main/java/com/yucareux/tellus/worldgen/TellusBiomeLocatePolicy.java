package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.worldgen.caves.TellusCaveBiomeDepthPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the surface-relative vertical samples used by Tellus biome locate
 * queries. Vanilla searches absolute Y levels 64 blocks apart, which can miss
 * Tellus's surface-relative cave shell or report a coordinate below a refined terrain
 * shell.
 */
public final class TellusBiomeLocatePolicy {
   private static final int BIOME_QUART_SIZE = 4;

   private TellusBiomeLocatePolicy() {
   }

   /**
    * Returns each quart-aligned cave-biome Y for a terrain column, ordered by
    * vertical distance from the command origin. Every result is guaranteed to
    * remain inside Tellus's configured cave-biome shell.
    */
   public static List<Integer> caveBiomeQuartYs(int surfaceY, int undergroundDepth, int originY) {
      int generationDepth = UndergroundGenerationDepthPolicy.generationDepth(undergroundDepth);
      if (generationDepth <= TellusCaveBiomeDepthPolicy.MIN_CAVE_BIOME_DEPTH) {
         return List.of();
      }

      Set<Integer> uniqueQuartYs = new LinkedHashSet<>();
      for (int depth = TellusCaveBiomeDepthPolicy.MIN_CAVE_BIOME_DEPTH; depth < generationDepth; depth++) {
         int quartY = quartBlockY(surfaceY - depth);
         int representedDepth = surfaceY - quartY;
         if (TellusCaveBiomeDepthPolicy.isCaveBiomeDepth(representedDepth, undergroundDepth)) {
            uniqueQuartYs.add(quartY);
         }
      }

      List<Integer> ordered = new ArrayList<>(uniqueQuartYs);
      ordered.sort(
         Comparator.comparingLong((Integer y) -> Math.abs((long)y - originY))
            .thenComparing(Comparator.reverseOrder())
      );
      return List.copyOf(ordered);
   }

   static int quartBlockY(int blockY) {
      return Math.floorDiv(blockY, BIOME_QUART_SIZE) * BIOME_QUART_SIZE;
   }
}
