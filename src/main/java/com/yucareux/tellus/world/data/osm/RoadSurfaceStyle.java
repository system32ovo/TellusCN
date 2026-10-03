package com.yucareux.tellus.world.data.osm;

import com.yucareux.tellus.worldgen.arnis.ArnisRoadRules;

public final class RoadSurfaceStyle {
   public static final byte STYLE_PAVED_DARK = 1;
   public static final byte STYLE_PAVED_LIGHT = 2;
   public static final byte STYLE_PAVED_SMOOTH = 3;
   public static final byte STYLE_PEDESTRIAN = 4;
   public static final byte STYLE_GRAVEL = 5;
   public static final byte STYLE_DIRT = 6;
   public static final byte STYLE_COBBLESTONE = 7;
   public static final byte STYLE_STONE_PAVERS = 8;
   public static final byte STYLE_BRICK = 9;
   public static final byte STYLE_SAND = 10;
   public static final byte STYLE_WOOD = 11;
   public static final byte STYLE_CONCRETE = 12;

   private RoadSurfaceStyle() {
   }

   public static String normalizeSurface(String surface) {
      return ArnisRoadRules.normalizeSurface(surface);
   }

   public static String normalizeSubclass(String subclass) {
      return ArnisRoadRules.normalizeSubclass(subclass);
   }

   public static double normalizeWidthMeters(double widthMeters) {
      return ArnisRoadRules.normalizeWidthMeters(widthMeters);
   }

   public static int normalizeLaneCount(int lanes) {
      return ArnisRoadRules.normalizeLaneCount(lanes);
   }

   public static int effectiveRoadWidth(RoadFeature road, int baseWidth, double worldScale) {
      return ArnisRoadRules.effectiveRoadWidth(road, baseWidth, worldScale);
   }

   public static byte surfaceStyleId(RoadFeature road, int worldX, int worldZ) {
      return ArnisRoadRules.surfaceStyleId(road, worldX, worldZ);
   }

   public static byte surfaceStyleId(
      RoadClass roadClass, String highwayTag, String roadSurface, String subclass, int worldX, int worldZ
   ) {
      return ArnisRoadRules.surfaceStyleId(roadClass, highwayTag, roadSurface, subclass, worldX, worldZ);
   }

   public static boolean shouldDrawCenterMarking(RoadFeature road, int roadWidth, double station, double distanceSq) {
      return ArnisRoadRules.shouldDrawCenterMarking(road, roadWidth, station, distanceSq);
   }

   public static boolean shouldDrawCenterMarking(RoadFeature road, int roadWidth, double station, double distanceSq, double maxCenterDistanceSq) {
      return ArnisRoadRules.shouldDrawCenterMarking(road, roadWidth, station, distanceSq, maxCenterDistanceSq);
   }

   public static boolean shouldDrawLaneMarking(RoadFeature road, int roadWidth, double station, double lateralDistance) {
      return ArnisRoadRules.shouldDrawLaneMarking(road, roadWidth, station, lateralDistance);
   }

   public static boolean shouldDrawLaneMarking(
      RoadFeature road, int roadWidth, double station, double lateralDistance, double distanceFromCenter, double markingTolerance
   ) {
      return ArnisRoadRules.shouldDrawLaneMarking(road, roadWidth, station, lateralDistance, distanceFromCenter, markingTolerance);
   }

   public static int effectiveLaneCount(RoadFeature road, int roadWidth) {
      return ArnisRoadRules.effectiveLaneCount(road, roadWidth);
   }

   public static boolean normalizeLaneMarkings(String value) {
      return ArnisRoadRules.normalizeLaneMarkings(value);
   }

   public static boolean isPedestrianLike(RoadFeature road) {
      return ArnisRoadRules.isPedestrianLike(road);
   }

   public static boolean isPedestrianLike(String highwayTag, String subclass) {
      return ArnisRoadRules.isPedestrianLike(highwayTag, subclass);
   }

   public static boolean isUnpaved(String surface) {
      return ArnisRoadRules.isUnpaved(surface);
   }
}
