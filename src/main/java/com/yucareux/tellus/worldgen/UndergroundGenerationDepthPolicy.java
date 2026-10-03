package com.yucareux.tellus.worldgen;

/**
 * Defines the surface-relative interval used by underground content.
 * The configured underground depth is the terrain shell: caves, features,
 * biomes, and structures may use every block above its protected floor.
 */
public final class UndergroundGenerationDepthPolicy {
   private UndergroundGenerationDepthPolicy() {
   }

   public static int generationDepth(int undergroundDepth) {
      return Math.max(undergroundDepth, 0);
   }

   /**
    * Returns the protected floor below generated underground content. Content
    * may be placed above this Y, but never at or below it.
    */
   public static int generationFloorY(int surfaceY, int undergroundDepth) {
      return surfaceY - generationDepth(undergroundDepth);
   }

   public static int deepestGenerationY(int surfaceY, int undergroundDepth) {
      return generationFloorY(surfaceY, undergroundDepth) + 1;
   }

   /**
    * Returns the deepest usable Y while respecting the world's build floor.
    * The minimum-Y block itself remains protected terrain, matching a terrain
    * shell whose bottom bedrock was clamped to the dimension boundary.
    */
   public static int deepestGenerationY(int surfaceY, int undergroundDepth, int minimumY) {
      return Math.max(minimumY + 1, deepestGenerationY(surfaceY, undergroundDepth));
   }

   public static boolean containsDepth(int depthBelowSurface, int undergroundDepth) {
      return depthBelowSurface >= 0 && depthBelowSurface < generationDepth(undergroundDepth);
   }
}
