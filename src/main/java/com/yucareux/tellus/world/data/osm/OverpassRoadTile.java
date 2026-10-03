package com.yucareux.tellus.world.data.osm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.Mth;

public final class OverpassRoadTile {
   private static final int GRID_X = 8;
   private static final int GRID_Y = 8;
   private static final int BUCKET_COUNT = GRID_X * GRID_Y;
   private static final OverpassRoadTile EMPTY = new OverpassRoadTile(List.of(), List.of());
   private final List<RoadFeature> features;
   private final List<RoadAreaFeature> areaFeatures;
   private final double tileSouth;
   private final double tileWest;
   private final double tileNorth;
   private final double tileEast;
   private final int[][] bucketFeatureIndices;

   public OverpassRoadTile(List<RoadFeature> features) {
      this(features, List.of(), Double.NaN, Double.NaN, Double.NaN, Double.NaN);
   }

   public OverpassRoadTile(List<RoadFeature> features, List<RoadAreaFeature> areaFeatures) {
      this(features, areaFeatures, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
   }

   public OverpassRoadTile(List<RoadFeature> features, double tileSouth, double tileWest, double tileNorth, double tileEast) {
      this(features, List.of(), tileSouth, tileWest, tileNorth, tileEast);
   }

   public OverpassRoadTile(
      List<RoadFeature> features, List<RoadAreaFeature> areaFeatures, double tileSouth, double tileWest, double tileNorth, double tileEast
   ) {
      this.features = List.copyOf(Objects.requireNonNull(features, "features"));
      this.areaFeatures = List.copyOf(Objects.requireNonNull(areaFeatures, "areaFeatures"));
      this.tileSouth = tileSouth;
      this.tileWest = tileWest;
      this.tileNorth = tileNorth;
      this.tileEast = tileEast;
      this.bucketFeatureIndices = this.buildSpatialIndex();
   }

   public static OverpassRoadTile empty() {
      return Objects.requireNonNull(EMPTY, "emptyOverpassRoadTile");
   }

   public List<RoadFeature> features() {
      return this.features;
   }

   public List<RoadAreaFeature> areaFeatures() {
      return this.areaFeatures;
   }

   public double tileSouth() {
      return this.tileSouth;
   }

   public double tileWest() {
      return this.tileWest;
   }

   public double tileNorth() {
      return this.tileNorth;
   }

   public double tileEast() {
      return this.tileEast;
   }

   public boolean isEmpty() {
      return this.features.isEmpty() && this.areaFeatures.isEmpty();
   }

   public List<RoadFeature> featuresInBounds(double south, double west, double north, double east) {
      if (this.features.isEmpty()) {
         return List.of();
      } else {
         double minSouth = Math.min(south, north);
         double maxNorth = Math.max(south, north);
         double minWest = Math.min(west, east);
         double maxEast = Math.max(west, east);
         if (this.bucketFeatureIndices != null && this.bucketFeatureIndices.length != 0) {
            if (!(maxEast < this.tileWest) && !(minWest > this.tileEast) && !(maxNorth < this.tileSouth) && !(minSouth > this.tileNorth)) {
               double clampedWest = Mth.clamp(minWest, this.tileWest, this.tileEast);
               double clampedEast = Mth.clamp(maxEast, this.tileWest, this.tileEast);
               double clampedSouth = Mth.clamp(minSouth, this.tileSouth, this.tileNorth);
               double clampedNorth = Mth.clamp(maxNorth, this.tileSouth, this.tileNorth);
               int minX = this.bucketXForLon(clampedWest);
               int maxX = this.bucketXForLon(clampedEast);
               int minY = this.bucketYForLat(clampedNorth);
               int maxY = this.bucketYForLat(clampedSouth);
               if (maxX >= minX && maxY >= minY) {
                  boolean[] seen = new boolean[this.features.size()];
                  List<RoadFeature> matches = new ArrayList<>();

                  for (int y = minY; y <= maxY; y++) {
                     for (int x = minX; x <= maxX; x++) {
                        int bucket = this.bucketIndex(x, y);
                        int[] featureIndexes = this.bucketFeatureIndices[bucket];
                        if (featureIndexes != null && featureIndexes.length != 0) {
                           for (int featureIndex : featureIndexes) {
                              if (featureIndex >= 0 && featureIndex < seen.length && !seen[featureIndex]) {
                                 seen[featureIndex] = true;
                                 RoadFeature feature = this.features.get(featureIndex);
                                 if (feature.intersects(minSouth, minWest, maxNorth, maxEast)) {
                                    matches.add(feature);
                                 }
                              }
                           }
                        }
                     }
                  }

                  return matches.isEmpty() ? List.of() : matches;
               } else {
                  return List.of();
               }
            } else {
               return List.of();
            }
	         } else {
	            List<RoadFeature> matches = new ArrayList<>(this.features.size());

	            for (RoadFeature feature : this.features) {
	               if (feature.intersects(minSouth, minWest, maxNorth, maxEast)) {
	                  matches.add(feature);
	               }
	            }

            return matches.isEmpty() ? List.of() : matches;
         }
      }
   }

   public List<RoadAreaFeature> areaFeaturesInBounds(double south, double west, double north, double east) {
      if (this.areaFeatures.isEmpty()) {
         return List.of();
      }

      double minSouth = Math.min(south, north);
      double maxNorth = Math.max(south, north);
      double minWest = Math.min(west, east);
      double maxEast = Math.max(west, east);
      List<RoadAreaFeature> matches = new ArrayList<>(this.areaFeatures.size());
      for (RoadAreaFeature feature : this.areaFeatures) {
         if (feature.intersects(minSouth, minWest, maxNorth, maxEast)) {
            matches.add(feature);
         }
      }

      return matches.isEmpty() ? List.of() : matches;
   }

   private int[][] buildSpatialIndex() {
      if (!this.features.isEmpty()
         && Double.isFinite(this.tileSouth)
         && Double.isFinite(this.tileWest)
         && Double.isFinite(this.tileNorth)
         && Double.isFinite(this.tileEast)
         && !(this.tileNorth <= this.tileSouth)
         && !(this.tileEast <= this.tileWest)) {
         int[] counts = new int[BUCKET_COUNT];

         for (int featureIndex = 0; featureIndex < this.features.size(); featureIndex++) {
            RoadFeature feature = this.features.get(featureIndex);
            int minX = this.bucketXForLon(feature.minLon());
            int maxX = this.bucketXForLon(feature.maxLon());
            int minY = this.bucketYForLat(feature.maxLat());
            int maxY = this.bucketYForLat(feature.minLat());

            for (int y = minY; y <= maxY; y++) {
               for (int x = minX; x <= maxX; x++) {
                  counts[this.bucketIndex(x, y)]++;
               }
            }
         }

         int[][] buckets = new int[BUCKET_COUNT][];

         for (int i = 0; i < BUCKET_COUNT; i++) {
            if (counts[i] > 0) {
               buckets[i] = new int[counts[i]];
            }
         }

         int[] offsets = new int[BUCKET_COUNT];

         for (int featureIndex = 0; featureIndex < this.features.size(); featureIndex++) {
            RoadFeature feature = this.features.get(featureIndex);
            int minX = this.bucketXForLon(feature.minLon());
            int maxX = this.bucketXForLon(feature.maxLon());
            int minY = this.bucketYForLat(feature.maxLat());
            int maxY = this.bucketYForLat(feature.minLat());

            for (int y = minY; y <= maxY; y++) {
               for (int x = minX; x <= maxX; x++) {
                  int bucket = this.bucketIndex(x, y);
                  int[] values = buckets[bucket];
                  if (values != null) {
                     int offset = offsets[bucket];
                     if (offset < values.length) {
                        values[offset] = featureIndex;
                        offsets[bucket] = offset + 1;
                     }
                  }
               }
            }
         }

         return buckets;
      } else {
         return null;
      }
   }

   private int bucketIndex(int x, int y) {
      return y * GRID_X + x;
   }

   private int bucketXForLon(double lon) {
      double clamped = Mth.clamp(lon, this.tileWest, this.tileEast);
      double range = this.tileEast - this.tileWest;
      if (range <= 1.0E-12) {
         return 0;
      } else {
         double normalized = (clamped - this.tileWest) / range;
         int bucket = (int)Math.floor(normalized * GRID_X);
         if (bucket >= GRID_X) {
            bucket = GRID_X - 1;
         }

         return Mth.clamp(bucket, 0, GRID_X - 1);
      }
   }

   private int bucketYForLat(double lat) {
      double clamped = Mth.clamp(lat, this.tileSouth, this.tileNorth);
      double range = this.tileNorth - this.tileSouth;
      if (range <= 1.0E-12) {
         return 0;
      } else {
         double normalized = (this.tileNorth - clamped) / range;
         int bucket = (int)Math.floor(normalized * GRID_Y);
         if (bucket >= GRID_Y) {
            bucket = GRID_Y - 1;
         }

         return Mth.clamp(bucket, 0, GRID_Y - 1);
      }
   }
}
