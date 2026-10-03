package com.yucareux.tellus.world.data.osm;

public final class OsmStreetLightFeature {
   private final long featureId;
   private final double longitude;
   private final double latitude;
   private final RoadPointKind kind;

   public OsmStreetLightFeature(long featureId, double longitude, double latitude) {
      this(featureId, longitude, latitude, RoadPointKind.STREET_LIGHT);
   }

   public OsmStreetLightFeature(long featureId, double longitude, double latitude, RoadPointKind kind) {
      if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
         throw new IllegalArgumentException("Street light feature requires finite coordinates");
      }

      this.featureId = featureId;
      this.longitude = longitude;
      this.latitude = latitude;
      this.kind = kind == null ? RoadPointKind.STREET_LIGHT : kind;
   }

   public long featureId() {
      return this.featureId;
   }

   public double longitude() {
      return this.longitude;
   }

   public double latitude() {
      return this.latitude;
   }

   public RoadPointKind kind() {
      return this.kind;
   }

   public boolean intersects(double south, double west, double north, double east) {
      return this.longitude >= west && this.longitude <= east && this.latitude >= south && this.latitude <= north;
   }
}
