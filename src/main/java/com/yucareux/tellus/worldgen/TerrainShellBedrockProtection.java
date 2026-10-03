package com.yucareux.tellus.worldgen;

/**
 * Defines the bedrock boundary around Tellus's surface-relative terrain shell.
 * A bottom layer alone is not sufficient on steep terrain: adjacent columns
 * can have different shell bottoms, leaving a vertical opening into the void.
 */
public final class TerrainShellBedrockProtection {
   private TerrainShellBedrockProtection() {
   }

   public static int supportBottomY(int surfaceY, int terrainDepth, int minimumY) {
      long bottomY = (long)surfaceY - Math.max(0, terrainDepth);
      return (int)Math.max(minimumY, bottomY);
   }

   /**
    * Returns the highest block in the current column that must become bedrock
    * to cover void below an adjacent column. A result at or below the current
    * support bottom means the ordinary bottom bedrock layer is sufficient.
    */
   public static int sideSkinTopY(int supportBottomY, int maximumY, int highestNeighborSupportBottomY) {
      if (highestNeighborSupportBottomY <= supportBottomY) {
         return supportBottomY;
      }

      long exposedTopY = (long)highestNeighborSupportBottomY - 1L;
      return (int)Math.min(maximumY, Math.max(Integer.MIN_VALUE, exposedTopY));
   }

   /**
    * Returns the first bedrock block in a curtain hanging from this column's
    * bottom. The curtain closes an air gap when a neighboring surface ends
    * before this column's terrain shell begins.
    */
   public static int voidCurtainBottomY(int supportBottomY, int minimumY, int lowestNeighborSurfaceY) {
      long curtainBottomY = (long)lowestNeighborSurfaceY + 1L;
      if (curtainBottomY >= supportBottomY) {
         return supportBottomY;
      }

      return (int)Math.max(minimumY, curtainBottomY);
   }
}
