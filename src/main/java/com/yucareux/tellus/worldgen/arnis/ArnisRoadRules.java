package com.yucareux.tellus.worldgen.arnis;

import com.yucareux.tellus.world.data.osm.RoadClass;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.world.data.osm.RoadSurfaceStyle;
import java.util.Locale;
import net.minecraft.util.Mth;

/**
 * Java port of Arnis highway width, surface, and lane-marking rules.
 *
 * <p>This product includes code derived from Arnis.
 * Copyright (c) 2022-2026 Louis Erbkamm (louis-e).
 * Licensed under the Apache License, Version 2.0.
 * Source: https://github.com/louis-e/arnis</p>
 */
public final class ArnisRoadRules {
   private static final int MAX_BLOCK_RANGE = 8;

   private ArnisRoadRules() {
   }

   public static String normalizeSurface(String surface) {
      String normalized = normalizeToken(surface);
      return normalized == null ? "" : normalized.replace('-', '_');
   }

   public static String normalizeSubclass(String subclass) {
      String normalized = normalizeToken(subclass);
      return normalized == null ? "" : normalized.replace('-', '_');
   }

   public static double normalizeWidthMeters(double widthMeters) {
      return Double.isFinite(widthMeters) && widthMeters >= 0.5 ? Mth.clamp(widthMeters, 0.5, 80.0) : 0.0;
   }

   public static int normalizeLaneCount(int lanes) {
      return lanes >= 1 ? Mth.clamp(lanes, 1, 16) : 0;
   }

   public static int effectiveRoadWidth(RoadFeature road, int baseWidth, double worldScale) {
      double safeScale = worldScale > 0.0 ? worldScale : 1.0;
      if (road != null && road.widthMeters() > 0.0) {
         int measured = Math.max(1, (int)Math.round(road.widthMeters() / safeScale));
         return Mth.clamp(measured, 1, MAX_BLOCK_RANGE * 2 + 1);
      }

      int blockRange = road == null ? fallbackBlockRange(baseWidth) : highwayBlockRange(road.highwayTag(), road.lanes(), safeScale);
      return Math.max(1, blockRange * 2 + 1);
   }

   public static byte surfaceStyleId(RoadFeature road, int worldX, int worldZ) {
      if (road == null) {
         return RoadSurfaceStyle.STYLE_PAVED_DARK;
      }
      return surfaceStyleId(road.roadClass(), road.highwayTag(), road.roadSurface(), road.subclass(), worldX, worldZ);
   }

   public static byte surfaceStyleId(
      RoadClass roadClass, String highwayTag, String roadSurface, String subclass, int worldX, int worldZ
   ) {
      String surface = normalizeSurface(roadSurface);
      if (isCobblestone(surface)) {
         return RoadSurfaceStyle.STYLE_COBBLESTONE;
      } else if (isStonePaver(surface)) {
         return RoadSurfaceStyle.STYLE_STONE_PAVERS;
      } else if (isBrick(surface)) {
         return RoadSurfaceStyle.STYLE_BRICK;
      } else if (isWood(surface)) {
         return RoadSurfaceStyle.STYLE_WOOD;
      } else if (isSandy(surface)) {
         return RoadSurfaceStyle.STYLE_SAND;
      } else if (isConcrete(surface)) {
         return RoadSurfaceStyle.STYLE_CONCRETE;
      } else if (isPedestrianLike(highwayTag, subclass)) {
         return RoadSurfaceStyle.STYLE_PEDESTRIAN;
      } else if (isUnpaved(surface) || roadClass == RoadClass.DIRT && surface.isEmpty()) {
         return prefersGravel(surface) ? RoadSurfaceStyle.STYLE_GRAVEL : RoadSurfaceStyle.STYLE_DIRT;
      }
      return RoadSurfaceStyle.STYLE_PAVED_DARK;
   }

   public static boolean shouldDrawCenterMarking(RoadFeature road, int roadWidth, double station, double distanceSq) {
      return shouldDrawCenterMarking(road, roadWidth, station, distanceSq, 0.22);
   }

   public static boolean shouldDrawCenterMarking(RoadFeature road, int roadWidth, double station, double distanceSq, double maxCenterDistanceSq) {
      double centerDistanceSq = Double.isFinite(maxCenterDistanceSq) && maxCenterDistanceSq > 0.0 ? maxCenterDistanceSq : 0.22;
      if (road == null || road.roadClass() != RoadClass.MAIN || roadWidth < 5 || distanceSq > centerDistanceSq) {
         return false;
      } else if (isPedestrianLike(road) || isUnpaved(road.roadSurface()) || !road.laneMarkings()) {
         return false;
      }
      int dash = (int)Math.floor(Math.max(0.0, station) / 5.0) % 2;
      return dash == 0;
   }

   public static boolean shouldDrawLaneMarking(RoadFeature road, int roadWidth, double station, double lateralDistance) {
      return shouldDrawLaneMarking(road, roadWidth, station, lateralDistance, Math.abs(lateralDistance), 0.42);
   }

   public static boolean shouldDrawLaneMarking(
      RoadFeature road, int roadWidth, double station, double lateralDistance, double distanceFromCenter, double markingTolerance
   ) {
      if (road == null || roadWidth < 5 || !road.laneMarkings()) {
         return false;
      }
      if (isPedestrianLike(road) || isUnpaved(road.roadSurface())) {
         return false;
      }
      if (road.roadClass() != RoadClass.MAIN && road.lanes() <= 1) {
         return false;
      }

      int laneCount = effectiveLaneCount(road, roadWidth);
      if (laneCount < 2) {
         return false;
      }

      int dash = (int)Math.floor(Math.max(0.0, station) / 5.0) % 2;
      if (dash != 0) {
         return false;
      }

      double roadWidthBlocks = Math.max(1.0, roadWidth);
      double laneWidth = roadWidthBlocks / laneCount;
      double halfWidth = roadWidthBlocks * 0.5;
      double tolerance = Double.isFinite(markingTolerance) && markingTolerance > 0.0 ? markingTolerance : 0.42;
      for (int lane = 1; lane < laneCount; lane++) {
         double dividerOffset = lane * laneWidth - halfWidth;
         if (Math.abs(lateralDistance - dividerOffset) <= tolerance && distanceFromCenter <= halfWidth + 0.75) {
            return true;
         }
      }
      return false;
   }

   public static int effectiveLaneCount(RoadFeature road, int roadWidth) {
      if (road == null) {
         return 1;
      }
      if (road.lanes() > 0) {
         return road.lanes();
      }
      String highway = normalizeSubclass(road.highwayTag());
      return switch (highway) {
         case "motorway", "primary", "trunk", "secondary", "tertiary" -> 2;
         default -> road.roadClass() == RoadClass.MAIN && roadWidth >= 5 ? 2 : 1;
      };
   }

   public static boolean normalizeLaneMarkings(String value) {
      String normalized = normalizeToken(value);
      return normalized == null || !("no".equals(normalized) || "false".equals(normalized) || "0".equals(normalized));
   }

   public static boolean isPedestrianLike(RoadFeature road) {
      return road != null && isPedestrianLike(road.highwayTag(), road.subclass());
   }

   public static boolean isPedestrianLike(String highwayTag, String subclass) {
      String highway = normalizeSubclass(highwayTag);
      String normalizedSubclass = normalizeSubclass(subclass);
      return "pedestrian".equals(highway)
         || "footway".equals(highway)
         || "path".equals(highway)
         || "steps".equals(highway)
         || "crosswalk".equals(normalizedSubclass)
         || "sidewalk".equals(normalizedSubclass);
   }

   public static boolean isUnpaved(String surface) {
      return switch (normalizeSurface(surface)) {
         case "unpaved", "gravel", "fine_gravel", "pebblestone", "compacted", "dirt", "earth", "ground", "grass", "mud", "woodchips" -> true;
         default -> false;
      };
   }

   private static int highwayBlockRange(String highwayTag, int lanes, double worldScale) {
      String highway = normalizeSubclass(highwayTag);
      int blockRange = switch (highway) {
         case "footway", "pedestrian", "path", "track", "secondary_link", "tertiary_link", "escape", "steps" -> 1;
         case "motorway", "primary", "trunk" -> 5;
         case "secondary" -> 4;
         case "tertiary", "service" -> 2;
         default -> {
            if (lanes == 2) {
               yield 3;
            } else if (lanes > 2) {
               yield 4;
            }
            yield 2;
         }
      };
      if (worldScale > 1.0) {
         blockRange = (int)Math.floor(blockRange / worldScale);
      }
      return Mth.clamp(blockRange, 0, MAX_BLOCK_RANGE);
   }

   private static int fallbackBlockRange(int baseWidth) {
      return Mth.clamp(Math.max(0, (baseWidth - 1) / 2), 0, MAX_BLOCK_RANGE);
   }

   private static boolean prefersGravel(String surface) {
      return switch (normalizeSurface(surface)) {
         case "gravel", "fine_gravel", "pebblestone", "compacted", "unpaved" -> true;
         default -> false;
      };
   }

   private static boolean isConcrete(String surface) {
      return switch (normalizeSurface(surface)) {
         case "concrete", "concrete_plates", "cement" -> true;
         default -> false;
      };
   }

   private static boolean isCobblestone(String surface) {
      return switch (normalizeSurface(surface)) {
         case "cobblestone", "unhewn_cobblestone" -> true;
         default -> false;
      };
   }

   private static boolean isStonePaver(String surface) {
      return switch (normalizeSurface(surface)) {
         case "paving_stones", "sett", "flagstones", "stone", "paved_stone" -> true;
         default -> false;
      };
   }

   private static boolean isBrick(String surface) {
      return switch (normalizeSurface(surface)) {
         case "brick", "bricks", "clay_pavers" -> true;
         default -> false;
      };
   }

   private static boolean isSandy(String surface) {
      return switch (normalizeSurface(surface)) {
         case "sand", "sandy" -> true;
         default -> false;
      };
   }

   private static boolean isWood(String surface) {
      return switch (normalizeSurface(surface)) {
         case "wood", "wooden", "boards", "boardwalk" -> true;
         default -> false;
      };
   }

   private static String normalizeToken(String value) {
      if (value == null) {
         return null;
      }
      String trimmed = value.trim().toLowerCase(Locale.ROOT);
      return trimmed.isEmpty() || "null".equals(trimmed) ? null : trimmed;
   }
}
