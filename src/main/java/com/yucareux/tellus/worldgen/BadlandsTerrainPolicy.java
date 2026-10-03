package com.yucareux.tellus.worldgen;

import java.util.Locale;

/**
 * Version-neutral badlands decisions shared by biome selection, full chunks,
 * and distant-horizon terrain. Material indices are resolved to version-
 * appropriate block states by each chunk generator.
 */
public final class BadlandsTerrainPolicy {
   public static final int TERRACOTTA = 0;
   public static final int ORANGE_TERRACOTTA = 1;
   public static final int YELLOW_TERRACOTTA = 2;
   public static final int BROWN_TERRACOTTA = 3;
   public static final int RED_TERRACOTTA = 4;
   public static final int LIGHT_GRAY_TERRACOTTA = 5;
   public static final int WHITE_TERRACOTTA = 6;

   public static final int PLATEAU_RED_SAND = 0;
   public static final int PLATEAU_COARSE_DIRT = 1;
   public static final int PLATEAU_TERRACOTTA = 2;
   public static final int PLATEAU_BROWN_TERRACOTTA = 3;

   public static final double CANYON_RELIEF_SAMPLE_METERS = 1600.0;
   public static final double CANYON_RELIEF_CELL_METERS = 800.0;
   public static final double MIN_CANYON_RELIEF_METERS = 120.0;
   private static final int MIN_CLIFF_BAND_DEPTH = 24;
   private static final int MAX_CLIFF_BAND_DEPTH = 65536;
   private static final int[] BAND_PATTERN = new int[]{
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA,
      ORANGE_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA,
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA,
      BROWN_TERRACOTTA, BROWN_TERRACOTTA,
      RED_TERRACOTTA, RED_TERRACOTTA, RED_TERRACOTTA,
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA,
      LIGHT_GRAY_TERRACOTTA, WHITE_TERRACOTTA, LIGHT_GRAY_TERRACOTTA,
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA,
      ORANGE_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA,
      YELLOW_TERRACOTTA, ORANGE_TERRACOTTA, ORANGE_TERRACOTTA,
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA,
      RED_TERRACOTTA, RED_TERRACOTTA,
      BROWN_TERRACOTTA, BROWN_TERRACOTTA,
      TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA, TERRACOTTA
   };
   private static final long BAND_WARP_SALT = 12995809138867221L;
   private static final long BAND_DETAIL_SALT = -6847214812577612469L;
   private static final long PLATEAU_EXPOSURE_SALT = 4386340699155943953L;
   private static final long PLATEAU_DETAIL_SALT = -2985479411584043847L;

   private BadlandsTerrainPolicy() {
   }

   public static boolean isDryCanyonCover(int coverClass) {
      return coverClass == 20 || coverClass == 30 || coverClass == 60 || coverClass == 100;
   }

   public static boolean isAridClimate(String koppenCode) {
      return koppenCode != null && koppenCode.toUpperCase(Locale.ROOT).startsWith("B");
   }

   public static boolean shouldUseCoherentAridClimate(int coverClass, String smoothedKoppenCode) {
      return isDryCanyonCover(coverClass) && isAridClimate(smoothedKoppenCode);
   }

   public static boolean shouldPromoteToBadlands(int coverClass, String koppenCode, double regionalReliefMeters) {
      return isDryCanyonCover(coverClass)
         && isAridClimate(koppenCode)
         && Double.isFinite(regionalReliefMeters)
         && regionalReliefMeters >= MIN_CANYON_RELIEF_METERS;
   }

   public static int regionalSampleCellBlocks(double worldScale) {
      if (!(worldScale > 0.0) || !Double.isFinite(worldScale)) {
         return 16;
      }

      return Math.max(16, (int)Math.min(65536.0, Math.ceil(CANYON_RELIEF_CELL_METERS / worldScale)));
   }

   public static int cliffBandDepth(int baseDepth, int localReliefBlocks) {
      int normalizedBaseDepth = Math.max(1, baseDepth);
      long reliefDepth = Math.max(0L, (long)localReliefBlocks) + 8L;
      long depth = Math.max(Math.max(normalizedBaseDepth, MIN_CLIFF_BAND_DEPTH), reliefDepth);
      return (int)Math.min(MAX_CLIFF_BAND_DEPTH, depth);
   }

   public static int bandMaterialIndex(int worldX, int worldZ, int y) {
      int offset = bandOffset(worldX, worldZ);
      return BAND_PATTERN[Math.floorMod(y + offset, BAND_PATTERN.length)];
   }

   public static int plateauMaterialIndex(int worldX, int worldZ) {
      // Flat badlands accumulate a mostly continuous red-sand cap. Blend a
      // second, shorter wavelength into the exposure field so the occasional
      // bare-terracotta pocket has a weathered edge instead of looking like a
      // broad contour stain. Steep terrain gets the full band palette later.
      double broadExposure = sampleValueNoise(worldX, worldZ, 56, PLATEAU_EXPOSURE_SALT);
      double edgeDetail = sampleValueNoise(worldX, worldZ, 13, PLATEAU_DETAIL_SALT);
      double exposure = broadExposure * 0.65 + edgeDetail * 0.35;
      return exposure >= 0.78 ? PLATEAU_TERRACOTTA : PLATEAU_RED_SAND;
   }

   private static int bandOffset(int worldX, int worldZ) {
      double broad = sampleValueNoise(worldX, worldZ, 96, BAND_WARP_SALT) - 0.5;
      double detail = sampleValueNoise(worldX, worldZ, 32, BAND_DETAIL_SALT) - 0.5;
      return (int)Math.round(broad * 14.0 + detail * 4.0);
   }

   private static double sampleValueNoise(int worldX, int worldZ, int cellSize, long salt) {
      int cellX = Math.floorDiv(worldX, cellSize);
      int cellZ = Math.floorDiv(worldZ, cellSize);
      double fractionX = Math.floorMod(worldX, cellSize) / (double)cellSize;
      double fractionZ = Math.floorMod(worldZ, cellSize) / (double)cellSize;
      double smoothX = smoothstep(fractionX);
      double smoothZ = smoothstep(fractionZ);
      double northWest = cellNoise(cellX, cellZ, salt);
      double northEast = cellNoise(cellX + 1, cellZ, salt);
      double southWest = cellNoise(cellX, cellZ + 1, salt);
      double southEast = cellNoise(cellX + 1, cellZ + 1, salt);
      double north = lerp(smoothX, northWest, northEast);
      double south = lerp(smoothX, southWest, southEast);
      return lerp(smoothZ, north, south);
   }

   private static double cellNoise(int cellX, int cellZ, long salt) {
      long value = salt ^ cellX * -7046029254386353131L ^ cellZ * -4417276706812531889L;
      value = mix64(value);
      return (value >>> 11) * 0x1.0p-53;
   }

   private static long mix64(long value) {
      long mixed = (value ^ value >>> 33) * -49064778989728563L;
      mixed = (mixed ^ mixed >>> 33) * -4265267296055464877L;
      return mixed ^ mixed >>> 33;
   }

   private static double smoothstep(double value) {
      return value * value * (3.0 - 2.0 * value);
   }

   private static double lerp(double delta, double start, double end) {
      return start + delta * (end - start);
   }
}
