package com.yucareux.tellus.worldgen;

/**
 * Shared DEM slope sampling rules for terrain materials.
 */
public final class TerrainSlopePolicy {
   /**
    * The preview grid is sampled every two blocks. Keeping full terrain on the
    * same radius prevents the material decision from changing between the two.
    */
   public static final int SAMPLE_RADIUS_BLOCKS = 2;

   private TerrainSlopePolicy() {
   }

   public static double localSlopeDegrees(
      double centerElevationMeters,
      double eastElevationMeters,
      double westElevationMeters,
      double northElevationMeters,
      double southElevationMeters,
      double eastWestSampleDistanceMeters,
      double northSouthSampleDistanceMeters
   ) {
      if (!Double.isFinite(centerElevationMeters)
         || !(eastWestSampleDistanceMeters > 0.0)
         || !(northSouthSampleDistanceMeters > 0.0)) {
         return Double.NaN;
      }

      double gradientX = strongestOneSidedGradient(
         centerElevationMeters, eastElevationMeters, westElevationMeters, eastWestSampleDistanceMeters
      );
      double gradientZ = strongestOneSidedGradient(
         centerElevationMeters, southElevationMeters, northElevationMeters, northSouthSampleDistanceMeters
      );
      if (!Double.isFinite(gradientX) && !Double.isFinite(gradientZ)) {
         return Double.NaN;
      }

      double resolvedGradientX = Double.isFinite(gradientX) ? gradientX : 0.0;
      double resolvedGradientZ = Double.isFinite(gradientZ) ? gradientZ : 0.0;
      return Math.toDegrees(Math.atan(Math.hypot(resolvedGradientX, resolvedGradientZ)));
   }

   private static double strongestOneSidedGradient(
      double centerElevationMeters,
      double positiveElevationMeters,
      double negativeElevationMeters,
      double sampleDistanceMeters
   ) {
      double strongest = Double.NaN;
      if (Double.isFinite(positiveElevationMeters)) {
         strongest = Math.abs(positiveElevationMeters - centerElevationMeters) / sampleDistanceMeters;
      }
      if (Double.isFinite(negativeElevationMeters)) {
         double negativeGradient = Math.abs(centerElevationMeters - negativeElevationMeters) / sampleDistanceMeters;
         strongest = Double.isFinite(strongest) ? Math.max(strongest, negativeGradient) : negativeGradient;
      }
      return strongest;
   }
}
