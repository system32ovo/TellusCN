package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.worldgen.arnis.ArnisBuildingRules;
import net.minecraft.world.level.block.state.BlockState;

public final class TellusBuildingFacade {
   private TellusBuildingFacade() {
   }

   public static BlockState resolveFacadeBlock(
      BuildingBlueprint blueprint,
      TellusBuildingMaterials.BuildingMaterialPalette palette,
      int boundaryDistance,
      int worldX,
      int worldZ,
      int floorIndex,
      int floorBottom,
      int floorTop,
      int y
   ) {
      if (TellusBuildingArchitecture.redesigned(blueprint) && blueprint.isFacadeCell(boundaryDistance, floorIndex)) {
         if (TellusBuildingArchitecture.window(blueprint, boundaryDistance, worldX, worldZ, floorIndex, y)) {
            return palette.window();
         }
         int edge = TellusBuildingArchitecture.edgeDistance(blueprint, worldX, worldZ, floorIndex);
         if (edge == 0 || blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER
            || floorIndex == 0 && TellusBuildingArchitecture.entranceBay(blueprint, worldX, worldZ)) {
            return palette.trim();
         }
         return palette.wall();
      }
      return ArnisBuildingRules.wallBlockAt(blueprint, palette, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, y);
   }

   public static boolean shouldPlaceWindow(
      BuildingBlueprint blueprint,
      int boundaryDistance,
      int worldX,
      int worldZ,
      int floorIndex,
      int floorBottom,
      int floorTop,
      int y
   ) {
      if (TellusBuildingArchitecture.redesigned(blueprint)) {
         return TellusBuildingArchitecture.window(blueprint, boundaryDistance, worldX, worldZ, floorIndex, y);
      }
      return ArnisBuildingRules.isWindow(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, y);
   }

   public static boolean shouldPlaceBalcony(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop
   ) {
      return ArnisBuildingRules.isBalcony(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop);
   }

   public static boolean shouldPlaceFireEscape(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex
   ) {
      return ArnisBuildingRules.isFireEscape(blueprint, boundaryDistance, worldX, worldZ, floorIndex);
   }

   public static boolean shouldPlaceAwning(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex) {
      return ArnisBuildingRules.isAwning(blueprint, boundaryDistance, worldX, worldZ, floorIndex);
   }

   public static boolean shouldPlaceRoofDetail(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int salt) {
      if (boundaryDistance < 2) {
         return false;
      }

      BuildingStyle style = blueprint.style();
      int localX = worldX - blueprint.minWorldX();
      int localZ = worldZ - blueprint.minWorldZ();
      int period = switch (style.roofDetail()) {
         case HVAC -> 9;
         case SKYLIGHT -> 7;
         case CROWN, ANTENNA -> 11;
         case PARAPET, SIMPLE -> 0;
      };
      if (period <= 0) {
         return false;
      }

      int hash = localX * 734287 + localZ * 912271 + salt * 31 + (int)(blueprint.blueprintSeed() ^ blueprint.blueprintSeed() >>> 32);
      return Math.floorMod(hash, period) == 0;
   }

   public static boolean shouldPlaceChimney(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ) {
      if (!blueprint.style().chimney() || boundaryDistance < 2) {
         return false;
      }

      int localX = worldX - blueprint.minWorldX();
      int localZ = worldZ - blueprint.minWorldZ();
      int width = blueprint.width();
      int depth = blueprint.depth();
      if (!isInteriorRoofCell(localX, localZ, width, depth)) {
         return false;
      }

      int chimneyCount = chimneyCount(blueprint, width, depth);
      for (int index = 0; index < chimneyCount; index++) {
         if (localX == chimneyLocalX(blueprint, width, depth, index, chimneyCount)
            && localZ == chimneyLocalZ(blueprint, width, depth, index, chimneyCount)) {
            return true;
         }
      }

      return false;
   }

   private static int chimneyCount(BuildingBlueprint blueprint, int width, int depth) {
      if (width < 6 || depth < 6) {
         return 0;
      }

      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      if (category != BuildingProfile.BuildingCategory.HOUSE && category != BuildingProfile.BuildingCategory.FARM) {
         return 0;
      }

      int maxChimneys = width * depth >= 140 && Math.min(width, depth) >= 8 ? 2 : 1;
      long seed = chimneySeed(blueprint, 0);
      int roll = positiveMod(seed, 100);
      if (roll < 40) {
         return 0;
      }

      return roll < 90 ? 1 : maxChimneys;
   }

   private static int chimneyLocalX(BuildingBlueprint blueprint, int width, int depth, int index, int chimneyCount) {
      long seed = chimneySeed(blueprint, index + 1);
      return switch (blueprint.profile().roofProfile()) {
         case GABLED_Z -> interiorCenter(width, seed);
         case HIPPED, PYRAMIDAL -> spacedInteriorCoordinate(width, seed, index, chimneyCount);
         case SKILLION -> width >= depth ? width - 3 : interiorCenter(width, seed);
         default -> spacedInteriorCoordinate(width, seed, index, chimneyCount);
      };
   }

   private static int chimneyLocalZ(BuildingBlueprint blueprint, int width, int depth, int index, int chimneyCount) {
      long seed = Long.rotateLeft(chimneySeed(blueprint, index + 11), 21);
      return switch (blueprint.profile().roofProfile()) {
         case GABLED_X -> interiorCenter(depth, seed);
         case HIPPED, PYRAMIDAL -> spacedInteriorCoordinate(depth, seed, index, chimneyCount);
         case SKILLION -> width >= depth ? interiorCenter(depth, seed) : depth - 3;
         default -> spacedInteriorCoordinate(depth, seed, index, chimneyCount);
      };
   }

   private static boolean isInteriorRoofCell(int localX, int localZ, int width, int depth) {
      return localX >= 2 && localZ >= 2 && localX <= width - 3 && localZ <= depth - 3;
   }

   private static int interiorCenter(int span, long seed) {
      int min = 2;
      int max = span - 3;
      if (min > max) {
         return -1;
      }

      int center = (span - 1) / 2;
      int jitter = positiveMod(seed, 3) - 1;
      return clamp(center + jitter, min, max);
   }

   private static int spacedInteriorCoordinate(int span, long seed, int index, int chimneyCount) {
      int min = 2;
      int max = span - 3;
      if (min > max) {
         return -1;
      }

      int range = max - min + 1;
      int first = positiveMod(seed, range);
      if (index <= 0 || chimneyCount <= 1 || range <= 2) {
         return min + first;
      }

      int offset = Math.max(2, range / 2);
      return min + Math.floorMod(first + offset, range);
   }

   private static long chimneySeed(BuildingBlueprint blueprint, int salt) {
      long seed = blueprint.blueprintSeed();
      seed ^= (long)blueprint.width() * 341873128712L;
      seed ^= (long)blueprint.depth() * 132897987541L;
      seed ^= (long)blueprint.profile().roofProfile().ordinal() * 42317861L;
      seed ^= (long)salt * -7046029254386353131L;
      seed ^= seed >>> 33;
      seed *= -49064778989728563L;
      seed ^= seed >>> 33;
      seed *= -4265267296055464877L;
      seed ^= seed >>> 33;
      return seed;
   }

   private static int positiveMod(long value, int modulus) {
      return Math.floorMod((int)(value ^ value >>> 32), modulus);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   public static boolean shouldPlaceInteriorPartition(int boundaryDistance) {
      return boundaryDistance >= 2;
   }

   public static boolean shouldPlaceFurniture(int boundaryDistance) {
      return boundaryDistance >= 2;
   }
}
