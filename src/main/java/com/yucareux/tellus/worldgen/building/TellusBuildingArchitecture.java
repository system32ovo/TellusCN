package com.yucareux.tellus.worldgen.building;

/** Aligned bays, a legible entrance, and restrained trim for homes and high rises. */
public final class TellusBuildingArchitecture {
   private TellusBuildingArchitecture() { }

   public static boolean redesigned(BuildingBlueprint blueprint) {
      return blueprint.profile().category() == BuildingProfile.BuildingCategory.HOUSE
         || blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER;
   }

   public static boolean entranceBay(BuildingBlueprint blueprint, int x, int z) {
      if (blueprint.entranceWidth() == 0) return false;
      return switch (blueprint.entranceFacing()) {
         case NORTH, SOUTH -> z == blueprint.entranceWorldZ() && Math.abs(x - blueprint.entranceWorldX()) <= 1;
         case EAST, WEST -> x == blueprint.entranceWorldX() && Math.abs(z - blueprint.entranceWorldZ()) <= 1;
         default -> false;
      };
   }

   public static boolean window(BuildingBlueprint blueprint, int boundaryDistance, int x, int z, int floor, int y) {
      if (!blueprint.isFacadeCell(boundaryDistance, floor) || floor == 0 && entranceBay(blueprint, x, z)) {
         return false;
      }
      int row = y - blueprint.floorBottomY(floor);
      int edge = edgeDistance(blueprint, x, z, floor);
      if (edge < 1 || row < 1 || y > blueprint.floorTopY(floor)) {
         return false;
      }
      if (blueprint.profile().category() == BuildingProfile.BuildingCategory.HOUSE) {
         return row >= 2 && edge >= 2 && Math.floorMod(edge - 2, 4) < 2;
      }
      // Floor-to-ceiling lobby glazing and stacked structural mullions above it.
      return floor == 0 ? edge % 5 != 0 : edge % 4 != 0;
   }

   public static int edgeDistance(BuildingBlueprint blueprint, int x, int z, int floor) {
      int inset = blueprint.setbackForFloor(floor);
      int dx = Math.min(x - blueprint.minWorldX() - inset, blueprint.maxWorldX() - inset - x);
      int dz = Math.min(z - blueprint.minWorldZ() - inset, blueprint.maxWorldZ() - inset - z);
      return dx <= dz ? dz : dx;
   }
}
