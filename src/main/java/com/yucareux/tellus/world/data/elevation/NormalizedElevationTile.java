package com.yucareux.tellus.world.data.elevation;

import java.util.Objects;

record NormalizedElevationTile(
   NormalizedElevationTileKey key,
   ShortRaster heights,
   ShortRaster mapterhornHeights,
   TellusElevationProvenance provenance
) {
   NormalizedElevationTile {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(heights, "heights");
      Objects.requireNonNull(mapterhornHeights, "mapterhornHeights");
      Objects.requireNonNull(provenance, "provenance");
      if (heights.width() != NormalizedElevationTileKey.TILE_SIZE || heights.height() != NormalizedElevationTileKey.TILE_SIZE) {
         throw new IllegalArgumentException("Unexpected normalized tile dimensions");
      }

      if (mapterhornHeights.width() != NormalizedElevationTileKey.TILE_SIZE
         || mapterhornHeights.height() != NormalizedElevationTileKey.TILE_SIZE) {
         throw new IllegalArgumentException("Unexpected normalized Mapterhorn tile dimensions");
      }

      if (provenance.width() != NormalizedElevationTileKey.TILE_SIZE || provenance.height() != NormalizedElevationTileKey.TILE_SIZE) {
         throw new IllegalArgumentException("Unexpected normalized provenance dimensions");
      }
   }
}
