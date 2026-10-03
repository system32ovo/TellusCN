package com.yucareux.tellus.worldgen;

final class VanillaStructurePlacement {
   private VanillaStructurePlacement() {
   }

   static boolean isWoodlandMansionPath(String path) {
      return "mansion".equals(path) || "woodland_mansion".equals(path);
   }

   static boolean shouldRetargetStronghold(boolean increasedHeight, boolean inOceanColumn, boolean tooShallow) {
      return increasedHeight || inOceanColumn || tooShallow;
   }

   static boolean shouldRetargetBuriedStructure(boolean increasedHeight, int structureTopY, int requiredTopY) {
      return increasedHeight || structureTopY > requiredTopY;
   }

   static boolean shouldRetargetMesaMineshaft(boolean increasedHeight) {
      return increasedHeight;
   }

   static int mineshaftSurfaceClearance(boolean mesa) {
      return mesa ? 4 : 14;
   }

   static int mineshaftDepthBase(boolean mesa) {
      return mesa ? 8 : 30;
   }

   static int mineshaftDepthRange(boolean mesa) {
      return mesa ? 25 : 21;
   }

   static VerticalPlacementBounds verticalPlacementBounds(
      int structureMinY,
      int structureMaxY,
      int lowestSurfaceY,
      int worldMinY,
      int worldMaxY,
      int floorClearance,
      int topCover
   ) {
      int minimumBottomY = Math.max(
         worldMinY + floorClearance,
         UndergroundStructureProtection.minimumStructureY(worldMinY)
      );
      int maximumTopY = Math.min(worldMaxY - topCover, lowestSurfaceY - topCover);
      return new VerticalPlacementBounds(minimumBottomY - structureMinY, maximumTopY - structureMaxY);
   }

   record VerticalPlacementBounds(int minOffsetY, int maxOffsetY) {
      boolean canFit() {
         return this.minOffsetY <= this.maxOffsetY;
      }
   }
}
