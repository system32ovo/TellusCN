package com.yucareux.tellus.world.data.osm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OsmStreetLightTile {
   private static final OsmStreetLightTile EMPTY = new OsmStreetLightTile(List.of());
   private final List<OsmStreetLightFeature> features;
   private final double tileSouth;
   private final double tileWest;
   private final double tileNorth;
   private final double tileEast;

   public OsmStreetLightTile(List<OsmStreetLightFeature> features) {
      this(features, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
   }

   public OsmStreetLightTile(List<OsmStreetLightFeature> features, double tileSouth, double tileWest, double tileNorth, double tileEast) {
      this.features = List.copyOf(Objects.requireNonNull(features, "features"));
      this.tileSouth = tileSouth;
      this.tileWest = tileWest;
      this.tileNorth = tileNorth;
      this.tileEast = tileEast;
   }

   public static OsmStreetLightTile empty() {
      return EMPTY;
   }

   public List<OsmStreetLightFeature> features() {
      return this.features;
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
      return this.features.isEmpty();
   }

   public List<OsmStreetLightFeature> featuresOfKind(RoadPointKind kind) {
      if (kind == null || this.features.isEmpty()) {
         return List.of();
      }

      List<OsmStreetLightFeature> matches = new ArrayList<>();
      for (OsmStreetLightFeature feature : this.features) {
         if (feature.kind() == kind) {
            matches.add(feature);
         }
      }
      return matches.isEmpty() ? List.of() : matches;
   }

   public List<OsmStreetLightFeature> featuresInBounds(double south, double west, double north, double east) {
      if (this.features.isEmpty()) {
         return List.of();
      } else {
         double minSouth = Math.min(south, north);
         double maxNorth = Math.max(south, north);
         double minWest = Math.min(west, east);
         double maxEast = Math.max(west, east);
         List<OsmStreetLightFeature> matches = new ArrayList<>();

         for (OsmStreetLightFeature feature : this.features) {
            if (feature.intersects(minSouth, minWest, maxNorth, maxEast)) {
               matches.add(feature);
            }
         }

         return matches.isEmpty() ? List.of() : matches;
      }
   }
}
