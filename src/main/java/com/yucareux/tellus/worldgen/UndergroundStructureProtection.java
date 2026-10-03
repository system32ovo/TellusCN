package com.yucareux.tellus.worldgen;

import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

/**
 * Geometry policy for the protective terrain capsule placed around buried
 * structures that extend below Tellus's configured terrain shell.
 */
final class UndergroundStructureProtection {
   static final int BEDROCK_THICKNESS = 1;
   static final int STONE_THICKNESS = 4;
   static final int TOTAL_THICKNESS = BEDROCK_THICKNESS + STONE_THICKNESS;
   static final int CAVERN_HORIZONTAL_CLEARANCE = 12;
   // Ancient-city pieces only need a compact buffer. A city-wide carve
   // exclusion creates flat cave floors and ceilings around the whole city.
   static final int CAVERN_PIECE_CARVER_MARGIN = 1;
   // Preserve the block directly below every city piece so its foundations
   // remain seated on the cavern floor.
   static final int CAVERN_CLEARANCE_BELOW = 0;
   static final int CAVERN_CLEARANCE_ABOVE = 12;
   static final int CAVERN_SURFACE_COVER = 16;
   static final int CAVERN_PIECE_SUPPORT_MARGIN = 1;

   private UndergroundStructureProtection() {
   }

   static boolean usesBoundedCavernEnvelope(TerrainAdjustment adjustment) {
      return adjustment == TerrainAdjustment.BEARD_BOX;
   }

   static int horizontalProtectionThickness(boolean cavernEnvelope) {
      return cavernEnvelope ? CAVERN_PIECE_SUPPORT_MARGIN : TOTAL_THICKNESS;
   }

   static int horizontalClearanceRadius(TerrainAdjustment adjustment) {
      return usesBoundedCavernEnvelope(adjustment) ? CAVERN_HORIZONTAL_CLEARANCE : 6;
   }

   static int clearanceBelow(TerrainAdjustment adjustment) {
      return usesBoundedCavernEnvelope(adjustment) ? CAVERN_CLEARANCE_BELOW : 0;
   }

   static int clearanceFloorOffset(TerrainAdjustment adjustment) {
      return usesBoundedCavernEnvelope(adjustment) ? 1 : 0;
   }

   static int clearanceAbove(TerrainAdjustment adjustment) {
      return usesBoundedCavernEnvelope(adjustment) ? CAVERN_CLEARANCE_ABOVE : 4;
   }

   static int clearanceCeilingY(TerrainAdjustment adjustment, int pieceMaxY, int terrainSurfaceY) {
      int requestedCeiling = pieceMaxY + clearanceAbove(adjustment);
      return usesBoundedCavernEnvelope(adjustment)
         ? Math.min(requestedCeiling, terrainSurfaceY - CAVERN_SURFACE_COVER)
         : requestedCeiling;
   }

   static int minimumStructureY(int worldMinY) {
      return worldMinY + TOTAL_THICKNESS;
   }

   static int protectionBottomY(int structureMinY) {
      return structureMinY - TOTAL_THICKNESS;
   }

   static int terrainShellBottomY(int surfaceY, int undergroundDepth) {
      return surfaceY - Math.max(0, undergroundDepth);
   }

   static boolean needsTerrainExtension(int structureMinY, int surfaceY, int undergroundDepth) {
      return protectionBottomY(structureMinY) < terrainShellBottomY(surfaceY, undergroundDepth);
   }

   static int protectionFillTopY(
      int terrainShellBottomY, int structureMinY, boolean cavernEnvelope, boolean preserveStructureCores
   ) {
      if (!cavernEnvelope) {
         return terrainShellBottomY;
      }

      // Before placement, seed the bounding-box floor as the city's terrain
      // contact. After placement, restore only the foundation below it.
      int highestSupportY = preserveStructureCores ? structureMinY - 1 : structureMinY;
      return Math.min(terrainShellBottomY, highestSupportY);
   }

   /**
    * The capsule has an outer bedrock floor and side wall. Its top remains open
    * so the extended volume joins the normal terrain shell and cave network.
    */
   static boolean isOuterBedrockSkin(
      int x, int y, int z, int expandedMinX, int protectionBottomY, int expandedMinZ, int expandedMaxX, int expandedMaxZ
   ) {
      return isOuterBedrockSkin(
         x,
         y,
         z,
         expandedMinX,
         protectionBottomY,
         expandedMinZ,
         expandedMaxX,
         expandedMaxZ,
         true
      );
   }

   static boolean isOuterBedrockSkin(
      int x,
      int y,
      int z,
      int expandedMinX,
      int protectionBottomY,
      int expandedMinZ,
      int expandedMaxX,
      int expandedMaxZ,
      boolean includeSideWalls
   ) {
      return y == protectionBottomY
         || includeSideWalls
            && (x == expandedMinX || x == expandedMaxX || z == expandedMinZ || z == expandedMaxZ);
   }
}
