package com.yucareux.tellus.worldgen;

public final class DeepslateSlopePolicy {
   private static final long COVERAGE_SALT = -4942790177534073029L;
   private static final long COVERAGE_BUCKETS = 10000L;
   private static final int MAX_SURFACE_DEPTH = 65536;

   private DeepslateSlopePolicy() {
   }

   public static double coveragePercent(double slopeDegrees) {
      if (!Double.isFinite(slopeDegrees) || slopeDegrees <= 40.0) {
         return 0.0;
      } else if (slopeDegrees >= 65.0) {
         return 100.0;
      } else {
         return (slopeDegrees - 40.0) * 4.0;
      }
   }

   public static boolean shouldCover(int worldX, int worldZ, double slopeDegrees) {
      double coverage = coveragePercent(slopeDegrees);
      if (coverage >= 100.0) {
         return true;
      } else if (coverage <= 0.0) {
         return false;
      }

      long mixed = mix64(COVERAGE_SALT ^ (long)worldX * 341873128712L ^ (long)worldZ * 132897987541L);
      return Math.floorMod(mixed, COVERAGE_BUCKETS) < coverage * (COVERAGE_BUCKETS / 100.0);
   }

   public static int surfaceDepthForRelief(int baseDepth, int localReliefBlocks) {
      int normalizedBaseDepth = Math.min(MAX_SURFACE_DEPTH, Math.max(1, baseDepth));
      if (localReliefBlocks < normalizedBaseDepth) {
         return normalizedBaseDepth;
      }

      return localReliefBlocks >= MAX_SURFACE_DEPTH ? MAX_SURFACE_DEPTH : localReliefBlocks + 1;
   }

   private static long mix64(long value) {
      value ^= value >>> 33;
      value *= -49064778989728563L;
      value ^= value >>> 33;
      value *= -4265267296055464877L;
      return value ^ value >>> 33;
   }
}
