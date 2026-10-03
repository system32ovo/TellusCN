package com.yucareux.tellus.worldgen.arnis;

import com.yucareux.tellus.worldgen.building.BuildingBlueprint;
import com.yucareux.tellus.worldgen.building.BuildingProfile;
import com.yucareux.tellus.worldgen.building.BuildingStyle;
import com.yucareux.tellus.worldgen.building.TellusBuildingMaterials;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Java port of Arnis building facade rules.
 *
 * <p>This product includes code derived from Arnis.
 * Copyright (c) 2022-2026 Louis Erbkamm (louis-e).
 * Licensed under the Apache License, Version 2.0.
 * Source: https://github.com/louis-e/arnis</p>
 */
public final class ArnisBuildingRules {
   private static final int ARNIS_FLOOR_CYCLE = 4;
   private static final int ARNIS_BAY_CYCLE = 6;

   private ArnisBuildingRules() {
   }

   public static BlockState wallBlockAt(
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
      if (!blueprint.isFacadeCell(boundaryDistance, floorIndex)) {
         return palette.wall();
      }
      if (isFacadeCorner(blueprint, worldX, worldZ, floorIndex)) {
         return palette.accent();
      }
      if (isWindow(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, y)) {
         return palette.window();
      }
      if (isAccentLine(blueprint, floorIndex, floorBottom, floorTop, y)) {
         return palette.accent();
      }
      if (isVerticalAccent(blueprint, worldX, worldZ, floorIndex, floorBottom, floorTop, y)) {
         return palette.accent();
      }
      if (blueprint.style().facadeFamily() == BuildingStyle.FacadeFamily.BRICK_ROW && Math.floorMod(floorIndex + blueprint.style().facadePhase(), 3) == 0) {
         return palette.secondaryWall();
      }
      return palette.wall();
   }

   public static boolean isWindow(
      BuildingBlueprint blueprint,
      int boundaryDistance,
      int worldX,
      int worldZ,
      int floorIndex,
      int floorBottom,
      int floorTop,
      int y
   ) {
      if (!blueprint.isFacadeCell(boundaryDistance, floorIndex) || blueprint.isEntranceCell(worldX, worldZ) && floorIndex == 0) {
         return false;
      }
      if (isFacadeCorner(blueprint, worldX, worldZ, floorIndex)) {
         return false;
      }

      BuildingStyle style = blueprint.style();
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      if (category == BuildingProfile.BuildingCategory.GARAGE || category == BuildingProfile.BuildingCategory.SHED) {
         return false;
      }

      int row = floorRow(y, blueprint.floorY());
      boolean aboveFloor = y > blueprint.floorY() + 1;
      int bay = facadeBay(blueprint, worldX, worldZ);

      if (style.facadeFamily() == BuildingStyle.FacadeFamily.GREENHOUSE || style.wallDepthStyle() == BuildingStyle.WallDepthStyle.GLASS_CURTAIN) {
         return y >= floorBottom + 1 && y <= floorTop && bay != 3;
      }
      if (style.windowPattern() == BuildingStyle.WindowPattern.CURTAIN) {
         return y >= floorBottom + 1 && y <= floorTop && bay != 3;
      }
      if (category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER) {
         if (hasLobbyBase(blueprint) && y <= blueprint.floorY() + 5) {
            return false;
         }
         return aboveFloor && row != 0;
      }
      if (category == BuildingProfile.BuildingCategory.TOWER) {
         return aboveFloor && (row == 1 || row == 2) && Math.floorMod(worldX + worldZ, 4) == 1;
      }
      if (isTallVerticalWindowBuilding(blueprint) && style.windowPattern() == BuildingStyle.WindowPattern.GRID) {
         return aboveFloor && Math.floorMod(worldX + worldZ, 2) == 0;
      }
      if (style.windowPattern() == BuildingStyle.WindowPattern.RIBBON) {
         return aboveFloor && bay < 5 && (row == 1 || row == 2);
      }
      if (style.windowPattern() == BuildingStyle.WindowPattern.INDUSTRIAL_STRIP) {
         return aboveFloor && bay < 2 && (row == 2 || row == 3);
      }
      if (style.windowPattern() == BuildingStyle.WindowPattern.SPARSE) {
         return aboveFloor && bay == 1 && row == 1;
      }

      if (floorIndex == 0
         && (style.groundFloorTreatment() == BuildingStyle.GroundFloorTreatment.STOREFRONT
            || style.groundFloorTreatment() == BuildingStyle.GroundFloorTreatment.LOBBY)) {
         return y >= floorBottom + 1 && y <= Math.min(floorTop, floorBottom + 3) && bay < 4;
      }

      return aboveFloor && row != 0 && bay < 3;
   }

   public static boolean isBalcony(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop
   ) {
      BuildingStyle style = blueprint.style();
      if (style.balconyProfile() == BuildingStyle.BalconyProfile.NONE || floorIndex <= 0 || !blueprint.isFacadeCell(boundaryDistance, floorIndex)) {
         return false;
      }
      if (isFacadeCorner(blueprint, worldX, worldZ, floorIndex)) {
         return false;
      }
      int cadence = style.balconyProfile() == BuildingStyle.BalconyProfile.FREQUENT ? 3 : 5;
      return Math.floorMod(facadeEdgeCoord(blueprint, worldX, worldZ, floorIndex) + style.accentPhase(), cadence) == 0
         && isWindow(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, floorBottom + 2);
   }

   public static boolean isFireEscape(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex) {
      BuildingStyle style = blueprint.style();
      return style.balconyProfile() == BuildingStyle.BalconyProfile.FIRE_ESCAPE
         && floorIndex > 1
         && blueprint.isFacadeCell(boundaryDistance, floorIndex)
         && !isFacadeCorner(blueprint, worldX, worldZ, floorIndex)
         && Math.floorMod(facadeEdgeCoord(blueprint, worldX, worldZ, floorIndex) + style.accentPhase(), 7) == 0;
   }

   public static boolean isAwning(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex) {
      BuildingStyle style = blueprint.style();
      if (floorIndex != 0 || !blueprint.isFacadeCell(boundaryDistance, floorIndex)) {
         return false;
      }
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      if (category == BuildingProfile.BuildingCategory.GREENHOUSE
         || category == BuildingProfile.BuildingCategory.GARAGE
         || category == BuildingProfile.BuildingCategory.SHED
         || category == BuildingProfile.BuildingCategory.RELIGIOUS
         || category == BuildingProfile.BuildingCategory.HISTORIC
         || category == BuildingProfile.BuildingCategory.TOWER) {
         return false;
      }
      return style.groundFloorTreatment() == BuildingStyle.GroundFloorTreatment.STOREFRONT || style.groundFloorTreatment() == BuildingStyle.GroundFloorTreatment.LOBBY;
   }

   public static boolean shouldPlaceResidentialShutter(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop
   ) {
      if (!allowsResidentialWindowDecorations(blueprint, boundaryDistance, floorIndex)) {
         return false;
      }
      int bay = facadeBay(blueprint, worldX, worldZ);
      if (bay != 3 && bay != 5) {
         return false;
      }
      int centerSum = bay == 3 ? worldX + worldZ - 2 : worldX + worldZ + 2;
      return deterministicRoll(blueprint.blueprintSeed(), centerSum, centerSum, 100) < 25
         && hasWindowInFloor(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop);
   }

   public static ResidentialDecoration residentialWindowDecoration(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop
   ) {
      if (!allowsResidentialWindowDecorations(blueprint, boundaryDistance, floorIndex)) {
         return ResidentialDecoration.NONE;
      }
      int bay = facadeBay(blueprint, worldX, worldZ);
      if (bay >= 3 || !hasWindowInFloor(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop)) {
         return ResidentialDecoration.NONE;
      }
      int centerSum = switch (bay) {
         case 0 -> worldX + worldZ + 1;
         case 1 -> worldX + worldZ;
         default -> worldX + worldZ - 1;
      };
      int roll = deterministicRoll(
         blueprint.blueprintSeed(),
         centerSum + floorIndex * 3,
         centerSum + floorIndex * 5,
         100
      );
      if (roll < 15) {
         return ResidentialDecoration.WINDOW_SILL;
      }
      if (roll < 23 && bay == 1) {
         return ResidentialDecoration.BALCONY;
      }
      return ResidentialDecoration.NONE;
   }

   public static boolean shouldPlaceWindowBoxPlant(BuildingBlueprint blueprint, int worldX, int worldZ, int floorIndex) {
      int bay = facadeBay(blueprint, worldX, worldZ);
      if (bay >= 3) {
         return false;
      }
      int roll = deterministicRoll(blueprint.blueprintSeed(), worldX, worldZ + floorIndex, 100);
      return bay == 1 ? roll < 70 : roll < 25;
   }

   public static RooftopEquipment rooftopEquipmentAt(BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ) {
      if (!shouldGenerateRooftopEquipment(blueprint) || boundaryDistance < 2) {
         return RooftopEquipment.NONE;
      }
      if (blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER
         && boundaryDistance < blueprint.setbackForFloor(blueprint.floorCount() - 1) + 2) {
         return RooftopEquipment.NONE;
      }
      int localX = worldX - blueprint.minWorldX();
      int localZ = worldZ - blueprint.minWorldZ();
      if (localX <= 1 || localZ <= 1 || localX >= blueprint.width() - 2 || localZ >= blueprint.depth() - 2) {
         return RooftopEquipment.NONE;
      }
      int roll = deterministicRoll(blueprint.blueprintSeed(), worldX, worldZ, 1200);
      if (roll >= 12) {
         return RooftopEquipment.NONE;
      }
      return switch (roll) {
         case 0, 1, 2 -> RooftopEquipment.HVAC;
         case 3, 4, 5 -> RooftopEquipment.SOLAR_PANEL;
         case 6 -> RooftopEquipment.ANTENNA;
         case 7, 8 -> RooftopEquipment.WATER_TANK;
         case 9, 10 -> RooftopEquipment.VENT_STACK;
         default -> RooftopEquipment.ROOF_ACCESS;
      };
   }

   public static boolean shouldGenerateRooftopEquipment(BuildingBlueprint blueprint) {
      if (!isFlatRoof(blueprint.profile().roofProfile())) {
         return false;
      }
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      if (category == BuildingProfile.BuildingCategory.HOUSE
         || category == BuildingProfile.BuildingCategory.FARM
         || category == BuildingProfile.BuildingCategory.GARAGE
         || category == BuildingProfile.BuildingCategory.SHED
         || category == BuildingProfile.BuildingCategory.GREENHOUSE
         || category == BuildingProfile.BuildingCategory.RELIGIOUS) {
         return false;
      }
      int buildingHeight = Math.max(0, blueprint.roofBaseY() - blueprint.floorY());
      if (buildingHeight >= 8) {
         return true;
      }
      boolean shortFlatBuilding = category == BuildingProfile.BuildingCategory.RESIDENTIAL
         || category == BuildingProfile.BuildingCategory.COMMERCIAL
         || category == BuildingProfile.BuildingCategory.OFFICE
         || category == BuildingProfile.BuildingCategory.HOTEL
         || category == BuildingProfile.BuildingCategory.SCHOOL
         || category == BuildingProfile.BuildingCategory.HOSPITAL
         || category == BuildingProfile.BuildingCategory.GENERIC;
      return buildingHeight >= 4 && shortFlatBuilding && deterministicRoll(blueprint.blueprintSeed(), 421, category.ordinal(), 100) < 35;
   }

   public static FlatRoofEdgeVariation flatRoofEdgeVariation(BuildingBlueprint blueprint) {
      if (!isFlatRoof(blueprint.profile().roofProfile())) {
         return FlatRoofEdgeVariation.NONE;
      }
      if (blueprint.style().parapet()
         || blueprint.profile().roofProfile() == BuildingProfile.RoofProfile.FLAT_PARAPET
         || blueprint.profile().category() == BuildingProfile.BuildingCategory.TALL_BUILDING
         || blueprint.profile().category() == BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER
         || blueprint.profile().category() == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER) {
         return FlatRoofEdgeVariation.PARAPET;
      }
      if (deterministicRoll(blueprint.blueprintSeed(), 911, 0, 100) >= 55) {
         return FlatRoofEdgeVariation.NONE;
      }
      return switch (deterministicRoll(blueprint.blueprintSeed(), 912, 0, 3)) {
         case 0 -> FlatRoofEdgeVariation.WALL_CAP;
         case 1 -> FlatRoofEdgeVariation.SLAB_CAP;
         default -> FlatRoofEdgeVariation.ACCENT_ROW;
      };
   }

   public static int facadeBay(BuildingBlueprint blueprint, int worldX, int worldZ) {
      return Math.floorMod(worldX + worldZ + blueprint.style().facadePhase(), ARNIS_BAY_CYCLE);
   }

   public static int floorRow(int y, int floorBottom) {
      return Math.floorMod(y - floorBottom - 2, ARNIS_FLOOR_CYCLE);
   }

   public static boolean isFacadeCorner(BuildingBlueprint blueprint, int worldX, int worldZ, int floorIndex) {
      int setback = blueprint.setbackForFloor(floorIndex);
      boolean westEast = worldX == blueprint.minWorldX() + setback || worldX == blueprint.maxWorldX() - setback;
      boolean northSouth = worldZ == blueprint.minWorldZ() + setback || worldZ == blueprint.maxWorldZ() - setback;
      return westEast && northSouth;
   }

   public enum ResidentialDecoration {
      NONE,
      WINDOW_SILL,
      BALCONY
   }

   public enum RooftopEquipment {
      NONE,
      HVAC,
      SOLAR_PANEL,
      ANTENNA,
      WATER_TANK,
      VENT_STACK,
      ROOF_ACCESS
   }

   public enum FlatRoofEdgeVariation {
      NONE,
      PARAPET,
      WALL_CAP,
      SLAB_CAP,
      ACCENT_ROW
   }

   private static boolean allowsResidentialWindowDecorations(BuildingBlueprint blueprint, int boundaryDistance, int floorIndex) {
      if (!blueprint.isFacadeCell(boundaryDistance, floorIndex) || blueprint.profile().floorCount() >= 8) {
         return false;
      }
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      return category == BuildingProfile.BuildingCategory.RESIDENTIAL;
   }

   private static boolean hasWindowInFloor(
      BuildingBlueprint blueprint, int boundaryDistance, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop
   ) {
      int startY = Math.max(floorBottom + 1, blueprint.floorY() + 2);
      int endY = Math.min(floorTop, blueprint.roofBaseY(boundaryDistance) - 2);
      for (int y = startY; y <= endY; y++) {
         if (floorRow(y, blueprint.floorY()) != 0 && isWindow(blueprint, boundaryDistance, worldX, worldZ, floorIndex, floorBottom, floorTop, y)) {
            return true;
         }
      }
      return false;
   }

   private static boolean isFlatRoof(BuildingProfile.RoofProfile roofProfile) {
      return roofProfile == BuildingProfile.RoofProfile.FLAT
         || roofProfile == BuildingProfile.RoofProfile.FLAT_PARAPET
         || roofProfile == BuildingProfile.RoofProfile.FLAT_CROWN
         || roofProfile == BuildingProfile.RoofProfile.FLAT_SKYLIGHT;
   }

   private static int deterministicRoll(long seed, int x, int z, int modulus) {
      long mixed = seed ^ (long)x * 341873128712L ^ (long)z * 132897987541L;
      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      mixed *= -4265267296055464877L;
      mixed ^= mixed >>> 33;
      return Math.floorMod((int)(mixed ^ mixed >>> 32), Math.max(1, modulus));
   }

   private static boolean isAccentLine(BuildingBlueprint blueprint, int floorIndex, int floorBottom, int floorTop, int y) {
      if (y <= floorBottom || y > floorTop) {
         return false;
      }
      int row = floorRow(y, blueprint.floorY());
      BuildingStyle style = blueprint.style();
      return row == 0
         && (style.facadeFamily() == BuildingStyle.FacadeFamily.MODERN_GRID
            || style.facadeFamily() == BuildingStyle.FacadeFamily.HISTORIC
            || style.wallDepthStyle() == BuildingStyle.WallDepthStyle.INSTITUTIONAL_BANDS
            || style.wallDepthStyle() == BuildingStyle.WallDepthStyle.SKYSCRAPER_FINS
            || floorIndex == blueprint.floorCount() - 1 && style.roofDetail() != BuildingStyle.RoofDetail.SIMPLE);
   }

   private static boolean isVerticalAccent(BuildingBlueprint blueprint, int worldX, int worldZ, int floorIndex, int floorBottom, int floorTop, int y) {
      if (y <= floorBottom || y >= floorTop) {
         return false;
      }
      BuildingStyle style = blueprint.style();
      int bay = facadeBay(blueprint, worldX, worldZ);
      return switch (style.wallDepthStyle()) {
         case NONE -> false;
         case SUBTLE_PILASTERS -> bay == 3 && floorIndex % 2 == 0;
         case MODERN_PILLARS -> bay == 3 || bay == 5;
         case INSTITUTIONAL_BANDS -> bay == 3 || floorRow(y, blueprint.floorY()) == 0;
         case INDUSTRIAL_BEAMS -> isFacadeCorner(blueprint, worldX, worldZ, floorIndex);
         case HISTORIC_ORNATE -> bay == 3 || floorRow(y, blueprint.floorY()) == 1;
         case RELIGIOUS_BUTTRESS -> bay == 0;
         case SKYSCRAPER_FINS -> bay == 3;
         case GLASS_CURTAIN -> isFacadeCorner(blueprint, worldX, worldZ, floorIndex);
      };
   }

   private static int facadeEdgeCoord(BuildingBlueprint blueprint, int worldX, int worldZ, int floorIndex) {
      int setback = blueprint.setbackForFloor(floorIndex);
      int minWorldX = blueprint.minWorldX() + setback;
      int maxWorldX = blueprint.maxWorldX() - setback;
      int minWorldZ = blueprint.minWorldZ() + setback;
      if (worldX == minWorldX || worldX == maxWorldX) {
         return Math.max(0, worldZ - minWorldZ);
      }
      return Math.max(0, worldX - minWorldX);
   }

   private static boolean isTallVerticalWindowBuilding(BuildingBlueprint blueprint) {
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      return category == BuildingProfile.BuildingCategory.TALL_BUILDING
         || category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER
         || category == BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER;
   }

   private static boolean hasLobbyBase(BuildingBlueprint blueprint) {
      long mixed = blueprint.blueprintSeed() + 6143L;
      mixed ^= mixed >>> 33;
      mixed *= -49064778989728563L;
      mixed ^= mixed >>> 33;
      return Math.floorMod((int)(mixed ^ mixed >>> 32), 10) < 7;
   }
}
