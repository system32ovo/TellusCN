package com.yucareux.tellus.worldgen;

import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * Resolves the original terrain surface used to project underground features.
 * Heightmaps may already contain cave entrances by the feature-decoration
 * stage, so they are not a stable source for the pre-carving surface.
 */
public final class UndergroundPlacementSurfacePolicy {
   private static final int CHUNK_SIDE = 16;
   private static final int CHUNK_AREA = CHUNK_SIDE * CHUNK_SIDE;

   private UndergroundPlacementSurfacePolicy() {
   }

   public static int resolve(
      int[] generatedSurfaceYByColumn,
      int worldX,
      int worldZ,
      IntBinaryOperator terrainSurfaceFallback
   ) {
      if (generatedSurfaceYByColumn != null && generatedSurfaceYByColumn.length == CHUNK_AREA) {
         int localX = worldX & (CHUNK_SIDE - 1);
         int localZ = worldZ & (CHUNK_SIDE - 1);
         return generatedSurfaceYByColumn[localZ * CHUNK_SIDE + localX];
      }

      return Objects.requireNonNull(terrainSurfaceFallback, "terrainSurfaceFallback")
         .applyAsInt(worldX, worldZ);
   }
}
