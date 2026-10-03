package com.yucareux.tellus.worldgen;

/**
 * Keeps large host-rock blobs buried beneath the lowest nearby terrain rather
 * than allowing their footprint to intersect steep mountain faces.
 */
public final class GeologicalStonePlacementPolicy {
   public static final int BLOB_SURFACE_SAMPLE_RADIUS = 5;
   public static final int BLOB_ORIGIN_COVER = 6;
   public static final int NOISE_SURFACE_SAMPLE_RADIUS = 2;
   public static final int NOISE_BLOCK_COVER = 2;
   public static final int REJECTED_PLACEMENT_Y = Integer.MIN_VALUE;

   private GeologicalStonePlacementPolicy() {
   }

   public static int safeBlobOriginY(int sampledY, int minimumNearbySurfaceY, int usableBottomY) {
      int buriedY = Math.min(sampledY, minimumNearbySurfaceY - BLOB_ORIGIN_COVER);
      return buriedY < usableBottomY ? REJECTED_PLACEMENT_Y : buriedY;
   }

   public static boolean isNoiseStoneBuried(int y, int minimumNearbySurfaceY) {
      return y <= minimumNearbySurfaceY - NOISE_BLOCK_COVER;
   }
}
