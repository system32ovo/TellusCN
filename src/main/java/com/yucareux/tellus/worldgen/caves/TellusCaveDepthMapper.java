package com.yucareux.tellus.worldgen.caves;

import net.minecraft.util.Mth;

/**
 * Maps vanilla's fixed Overworld underground profile into the solid material
 * available below a Tellus terrain column.
 */
public final class TellusCaveDepthMapper {
   public static final int VANILLA_MIN_Y = -64;
   public static final int VANILLA_SEA_LEVEL = 63;
   public static final int VANILLA_MAX_Y = 319;

   private TellusCaveDepthMapper() {
   }

   /**
    * Converts an actual Tellus Y inside a terrain column to the equivalent Y
    * in a vanilla density column. When the Tellus shell is shallower than the
    * vanilla column, the profile is compressed without moving its endpoints.
    */
   public static int virtualYForActualY(
      int actualY,
      int actualSurfaceY,
      int actualBottomY,
      int virtualSurfaceY,
      int virtualBottomY
   ) {
      int actualDepth = actualSurfaceY - actualY;
      int actualDepthRange = actualSurfaceY - actualBottomY;
      int virtualDepthRange = virtualSurfaceY - virtualBottomY;
      if (actualDepthRange <= 0 || virtualDepthRange <= 0) {
         return virtualSurfaceY;
      }

      double fraction = Mth.clamp(actualDepth / (double)actualDepthRange, 0.0, 1.0);
      return Mth.clamp(virtualSurfaceY - Mth.floor(fraction * virtualDepthRange + 0.5), virtualBottomY, virtualSurfaceY);
   }

   /**
    * Gives height-range placed features a virtual vanilla surface matching the
    * Tellus column's elevation above sea level.
    */
   public static int virtualSurfaceForTellusColumn(int actualSurfaceY, int tellusSeaLevel) {
      return Mth.clamp(VANILLA_SEA_LEVEL + actualSurfaceY - tellusSeaLevel, VANILLA_SEA_LEVEL, VANILLA_MAX_Y);
   }

   /**
    * Locates vanilla Y=0 inside a Tellus terrain shell. Blocks below the
    * returned Y use deepslate while blocks at and above it use stone.
    */
   public static int actualDeepslateBoundaryY(int actualSurfaceY, int actualBottomY, int tellusSeaLevel) {
      int virtualSurfaceY = virtualSurfaceForTellusColumn(actualSurfaceY, tellusSeaLevel);
      int boundaryY = actualYForVirtualFeature(0, virtualSurfaceY, actualSurfaceY, actualBottomY);
      return boundaryY == Integer.MIN_VALUE ? actualBottomY : boundaryY;
   }

   /**
    * Projects a sampled vanilla feature Y into the usable Tellus underground
    * interval. Returns {@link Integer#MIN_VALUE} when vanilla would place the
    * feature above the corresponding terrain surface.
    */
   public static int actualYForVirtualFeature(
      int virtualY,
      int virtualSurfaceY,
      int actualSurfaceY,
      int actualBottomY
   ) {
      if (virtualY > virtualSurfaceY || actualBottomY >= actualSurfaceY) {
         return Integer.MIN_VALUE;
      }

      int virtualDepthRange = Math.max(1, virtualSurfaceY - VANILLA_MIN_Y);
      int virtualDepth = virtualSurfaceY - Math.max(VANILLA_MIN_Y, virtualY);
      int actualDepthRange = actualSurfaceY - actualBottomY;
      int actualDepth = Math.max(1, Mth.floor(virtualDepth / (double)virtualDepthRange * actualDepthRange + 0.5));
      return Mth.clamp(actualSurfaceY - actualDepth, actualBottomY, actualSurfaceY - 1);
   }
}
