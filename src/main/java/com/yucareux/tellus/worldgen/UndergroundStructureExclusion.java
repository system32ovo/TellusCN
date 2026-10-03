package com.yucareux.tellus.worldgen;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * Piece-level exclusion geometry used to keep terrain-adapted underground
 * structures separate from projected caves and cave-biome decoration.
 */
public final class UndergroundStructureExclusion {
   public static final int MINIMUM_TERRAIN_COVER = 8;
   public static final int CARVER_MARGIN = 4;
   public static final int FEATURE_PLACEMENT_MARGIN = 16;

   private UndergroundStructureExclusion() {
   }

   public static boolean protectsProjectedCarving(TerrainAdjustment adjustment) {
      return adjustment != TerrainAdjustment.NONE;
   }

   /**
    * Beard-box structures need a protected roof, but their cavern must remain
    * eligible for ores, geological stone, and deep-dark decoration.
    */
   public static boolean protectsFeaturePlacement(TerrainAdjustment adjustment) {
      return adjustment != TerrainAdjustment.NONE && adjustment != TerrainAdjustment.BEARD_BOX;
   }

   public static boolean isUnderground(int pieceMaxY, int terrainSurfaceY) {
      return pieceMaxY <= terrainSurfaceY - MINIMUM_TERRAIN_COVER;
   }

   public static boolean blocksCarving(List<Box> boxes, int x, int y, int z) {
      if (boxes == null || boxes.isEmpty()) {
         return false;
      }

      for (Box box : boxes) {
         if (box.containsExpanded(x, y, z, box.carvingMargin())) {
            return true;
         }
      }
      return false;
   }

   public static boolean blocksFeaturePlacement(List<Box> boxes, int x, int y, int z) {
      if (boxes == null || boxes.isEmpty()) {
         return false;
      }

      for (Box box : boxes) {
         if (box.blocksFeaturePlacement() && box.containsExpanded(x, y, z, FEATURE_PLACEMENT_MARGIN)) {
            return true;
         }
      }
      return false;
   }

   public record Box(
      int minX,
      int minY,
      int minZ,
      int maxX,
      int maxY,
      int maxZ,
      int carvingMargin,
      boolean blocksFeaturePlacement
   ) {
      public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
         this(minX, minY, minZ, maxX, maxY, maxZ, CARVER_MARGIN, true);
      }

      public Box(
         int minX,
         int minY,
         int minZ,
         int maxX,
         int maxY,
         int maxZ,
         boolean blocksFeaturePlacement
      ) {
         this(minX, minY, minZ, maxX, maxY, maxZ, CARVER_MARGIN, blocksFeaturePlacement);
      }

      public Box {
         if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Underground structure exclusion bounds are inverted");
         }
         if (carvingMargin < 0) {
            throw new IllegalArgumentException("Underground structure carving margin cannot be negative");
         }
      }

      public boolean containsExpanded(int x, int y, int z, int margin) {
         int safeMargin = Math.max(0, margin);
         return (long)x >= (long)this.minX - safeMargin
            && (long)x <= (long)this.maxX + safeMargin
            && (long)y >= (long)this.minY - safeMargin
            && (long)y <= (long)this.maxY + safeMargin
            && (long)z >= (long)this.minZ - safeMargin
            && (long)z <= (long)this.maxZ + safeMargin;
      }

      public boolean intersectsHorizontal(int minX, int minZ, int maxX, int maxZ, int margin) {
         int safeMargin = Math.max(0, margin);
         return (long)this.maxX + safeMargin >= minX
            && (long)this.minX - safeMargin <= maxX
            && (long)this.maxZ + safeMargin >= minZ
            && (long)this.minZ - safeMargin <= maxZ;
      }
   }
}
