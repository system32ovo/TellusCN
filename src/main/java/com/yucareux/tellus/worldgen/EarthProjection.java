package com.yucareux.tellus.worldgen;

public final class EarthProjection {
   public static final double METERS_PER_DEGREE = 111319.49166666667;
   public static final double EQUATOR_CIRCUMFERENCE_METERS = METERS_PER_DEGREE * 360.0;
   public static final double MAX_MERCATOR_LATITUDE = 85.05112878;
   private static final double EARTH_RADIUS_METERS = METERS_PER_DEGREE * 180.0 / Math.PI;
   private static final double MAX_MERCATOR_Y_RATIO = mercatorYRatio(MAX_MERCATOR_LATITUDE);
   private static final EarthProjection.ProjectionMode PROJECTION_MODE = resolveMode(System.getProperty("tellus.projection.mode", "mercator"));

   private EarthProjection() {
   }

   public static double blocksPerDegree(double worldScale) {
      return worldScale <= 0.0 ? 0.0 : METERS_PER_DEGREE / worldScale;
   }

   public static double blockXToLongitude(double blockX, double worldScale) {
      return worldScale <= 0.0 ? 0.0 : blockX * worldScale / METERS_PER_DEGREE;
   }

   public static double longitudeToBlockX(double longitude, double worldScale) {
      return worldScale <= 0.0 ? 0.0 : longitude * METERS_PER_DEGREE / worldScale;
   }

   public static double worldScaleFromBlocksPerDegree(double blocksPerDegree) {
      return blocksPerDegree <= 0.0 ? 0.0 : METERS_PER_DEGREE / blocksPerDegree;
   }

   public static double latToBlockZ(double latitude, double worldScale) {
      if (worldScale <= 0.0) {
         return 0.0;
      } else if (PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY) {
         return -latitude * blocksPerDegree(worldScale);
      } else {
         double clampedLatitude = clampLatitude(latitude);
         double latitudeRad = Math.toRadians(clampedLatitude);
         double mercatorY = EARTH_RADIUS_METERS * Math.log(Math.tan(Math.PI * 0.25 + latitudeRad * 0.5));
         return -mercatorY / worldScale;
      }
   }

   public static double blockZToLat(double blockZ, double worldScale) {
      if (worldScale <= 0.0) {
         return 0.0;
      } else if (PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY) {
         return -blockZ / blocksPerDegree(worldScale);
      } else {
         double mercatorY = -blockZ * worldScale;
         double latitudeRad = Math.atan(Math.sinh(mercatorY / EARTH_RADIUS_METERS));
         return clampLatitude(Math.toDegrees(latitudeRad));
      }
   }

   public static double groundMetersPerBlockX(double blockZ, double worldScale) {
      return projectedGroundScale(blockZ, worldScale);
   }

   public static double groundMetersPerBlockZ(double blockZ, double worldScale) {
      return PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY
         ? worldScale
         : projectedGroundScale(blockZ, worldScale);
   }

   /**
    * Returns the vertical terrain multiplier needed to keep Mercator-projected
    * landscapes at the same local scale in all three axes.
    *
    * <p>Spherical Mercator enlarges both horizontal axes by {@code sec(latitude)}.
    * Multiplying elevation by the same factor prevents terrain slopes from being
    * flattened by the projection. Legacy projection mode keeps its historical
    * uncorrected vertical scale.</p>
    */
   public static double heightScaleCorrection(double blockZ, double worldScale) {
      if (!(worldScale > 0.0) || PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY) {
         return 1.0;
      }

      // blockZ is already the inverse Web Mercator northing. sec(latitude)
      // reduces exactly to cosh(mercatorY / earthRadius), avoiding an inverse
      // projection and another trigonometric call in every terrain sample.
      double mercatorYRatio = clampMercatorYRatio(-blockZ * worldScale / EARTH_RADIUS_METERS);
      return Math.cosh(mercatorYRatio);
   }

   public static double heightScaleCorrectionAtLatitude(double latitude) {
      if (PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY) {
         return 1.0;
      }

      double latitudeRadians = Math.toRadians(clampLatitude(latitude));
      return 1.0 / Math.cos(latitudeRadians);
   }

   private static double projectedGroundScale(double blockZ, double worldScale) {
      if (!(worldScale > 0.0)) {
         return 0.0;
      }

      return PROJECTION_MODE == EarthProjection.ProjectionMode.LEGACY
         ? worldScale
         : worldScale / heightScaleCorrection(blockZ, worldScale);
   }

   public static double clampLatitude(double latitude) {
      return Math.max(-MAX_MERCATOR_LATITUDE, Math.min(MAX_MERCATOR_LATITUDE, latitude));
   }

   public static String projectionModeId() {
      return PROJECTION_MODE.id();
   }

   private static EarthProjection.ProjectionMode resolveMode(String value) {
      if (value == null) {
         return EarthProjection.ProjectionMode.MERCATOR;
      } else {
         return "legacy".equalsIgnoreCase(value.trim()) ? EarthProjection.ProjectionMode.LEGACY : EarthProjection.ProjectionMode.MERCATOR;
      }
   }

   private static double mercatorYRatio(double latitude) {
      double latitudeRadians = Math.toRadians(latitude);
      return Math.log(Math.tan(Math.PI * 0.25 + latitudeRadians * 0.5));
   }

   private static double clampMercatorYRatio(double value) {
      return Math.max(-MAX_MERCATOR_Y_RATIO, Math.min(MAX_MERCATOR_Y_RATIO, value));
   }

   private static enum ProjectionMode {
      LEGACY("legacy"),
      MERCATOR("mercator");

      private final String id;

      private ProjectionMode(String id) {
         this.id = id;
      }

      private String id() {
         return this.id;
      }
   }
}
