package com.yucareux.tellus.world.data.osm;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public final class RoadFeature {
   private final long wayId;
   private final RoadClass roadClass;
   private final RoadMode mode;
   private final int bridgeLevel;
   private final String highwayTag;
   private final String roadSurface;
   private final String subclass;
   private final double widthMeters;
   private final int lanes;
   private final boolean laneMarkings;
   private final double[] longitudes;
   private final double[] latitudes;
   private final double minLon;
   private final double maxLon;
   private final double minLat;
   private final double maxLat;

   public RoadFeature(long wayId, RoadClass roadClass, RoadMode mode, int bridgeLevel, String highwayTag, double[] longitudes, double[] latitudes) {
      this(wayId, roadClass, mode, bridgeLevel, highwayTag, null, null, 0.0, longitudes, latitudes);
   }

   public RoadFeature(
      long wayId,
      RoadClass roadClass,
      RoadMode mode,
      int bridgeLevel,
      String highwayTag,
      String roadSurface,
      String subclass,
      double widthMeters,
      double[] longitudes,
      double[] latitudes
   ) {
      this(wayId, roadClass, mode, bridgeLevel, highwayTag, roadSurface, subclass, widthMeters, 0, true, longitudes, latitudes);
   }

   public RoadFeature(
      long wayId,
      RoadClass roadClass,
      RoadMode mode,
      int bridgeLevel,
      String highwayTag,
      String roadSurface,
      String subclass,
      double widthMeters,
      int lanes,
      boolean laneMarkings,
      double[] longitudes,
      double[] latitudes
   ) {
      this.wayId = wayId;
      this.roadClass = Objects.requireNonNull(roadClass, "roadClass");
      this.mode = Objects.requireNonNull(mode, "mode");
      this.bridgeLevel = Math.max(0, bridgeLevel);
      this.highwayTag = normalizeHighwayTag(highwayTag);
      this.roadSurface = RoadSurfaceStyle.normalizeSurface(roadSurface);
      this.subclass = RoadSurfaceStyle.normalizeSubclass(subclass);
      this.widthMeters = RoadSurfaceStyle.normalizeWidthMeters(widthMeters);
      this.lanes = RoadSurfaceStyle.normalizeLaneCount(lanes);
      this.laneMarkings = laneMarkings;
	      this.longitudes = Objects.requireNonNull(longitudes, "longitudes").clone();
	      this.latitudes = Objects.requireNonNull(latitudes, "latitudes").clone();
      if (this.longitudes.length == this.latitudes.length && this.longitudes.length >= 2) {
         double lowLon = Double.POSITIVE_INFINITY;
         double highLon = Double.NEGATIVE_INFINITY;
         double lowLat = Double.POSITIVE_INFINITY;
         double highLat = Double.NEGATIVE_INFINITY;

         for (int i = 0; i < this.longitudes.length; i++) {
            double lon = this.longitudes[i];
            double lat = this.latitudes[i];
            lowLon = Math.min(lowLon, lon);
            highLon = Math.max(highLon, lon);
            lowLat = Math.min(lowLat, lat);
            highLat = Math.max(highLat, lat);
         }

         this.minLon = lowLon;
         this.maxLon = highLon;
         this.minLat = lowLat;
         this.maxLat = highLat;
      } else {
         throw new IllegalArgumentException("RoadFeature requires at least two matching lon/lat points");
      }
   }

   public long wayId() {
      return this.wayId;
   }

   public RoadClass roadClass() {
      return this.roadClass;
   }

   public RoadMode mode() {
      return this.mode;
   }

   public int bridgeLevel() {
      return this.bridgeLevel;
   }

   public String highwayTag() {
      return this.highwayTag;
   }

   public String roadSurface() {
      return this.roadSurface;
   }

   public String subclass() {
      return this.subclass;
   }

   public double widthMeters() {
      return this.widthMeters;
   }

   public int lanes() {
      return this.lanes;
   }

   public boolean laneMarkings() {
      return this.laneMarkings;
   }

   public boolean matchesHighwayTag(String highwayTag) {
      return this.highwayTag.equals(normalizeHighwayTag(highwayTag));
   }

   public boolean isSecondaryRoad() {
      return this.matchesHighwayTag("secondary") || this.matchesHighwayTag("secondary_link");
   }

   public int pointCount() {
      return this.longitudes.length;
   }

   public double lonAt(int index) {
      return this.longitudes[index];
   }

   public double latAt(int index) {
      return this.latitudes[index];
   }

   public double[] longitudes() {
      return Arrays.copyOf(this.longitudes, this.longitudes.length);
   }

   public double[] latitudes() {
      return Arrays.copyOf(this.latitudes, this.latitudes.length);
   }

   public double minLon() {
      return this.minLon;
   }

   public double maxLon() {
      return this.maxLon;
   }

   public double minLat() {
      return this.minLat;
   }

   public double maxLat() {
      return this.maxLat;
   }

   public boolean intersects(double south, double west, double north, double east) {
      return this.maxLon >= west && this.minLon <= east && this.maxLat >= south && this.minLat <= north;
   }

   private static String normalizeHighwayTag(String highwayTag) {
      return highwayTag == null ? "" : highwayTag.trim().toLowerCase(Locale.ROOT);
   }
}
