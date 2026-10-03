package com.yucareux.tellus.world.data.resolve;

import java.util.Locale;

/** The regional biome classes published with RESOLVE Ecoregions 2017. */
public enum ResolveBiome {
   TROPICAL_MOIST_BROADLEAF_FORESTS(1, "Tropical & Subtropical Moist Broadleaf Forests"),
   TROPICAL_DRY_BROADLEAF_FORESTS(2, "Tropical & Subtropical Dry Broadleaf Forests"),
   TROPICAL_CONIFEROUS_FORESTS(3, "Tropical & Subtropical Coniferous Forests"),
   TEMPERATE_BROADLEAF_MIXED_FORESTS(4, "Temperate Broadleaf & Mixed Forests"),
   TEMPERATE_CONIFER_FORESTS(5, "Temperate Conifer Forests"),
   BOREAL_FORESTS_TAIGA(6, "Boreal Forests/Taiga"),
   TROPICAL_GRASSLANDS_SAVANNAS_SHRUBLANDS(7, "Tropical & Subtropical Grasslands, Savannas & Shrublands"),
   TEMPERATE_GRASSLANDS_SAVANNAS_SHRUBLANDS(8, "Temperate Grasslands, Savannas & Shrublands"),
   FLOODED_GRASSLANDS_SAVANNAS(9, "Flooded Grasslands & Savannas"),
   MONTANE_GRASSLANDS_SHRUBLANDS(10, "Montane Grasslands & Shrublands"),
   TUNDRA(11, "Tundra"),
   MEDITERRANEAN_FORESTS_WOODLANDS_SCRUB(12, "Mediterranean Forests, Woodlands & Scrub"),
   DESERTS_XERIC_SHRUBLANDS(13, "Deserts & Xeric Shrublands"),
   MANGROVES(14, "Mangroves"),
   ROCK_AND_ICE(11, "Rock and Ice"),
   UNKNOWN(-1, "Unknown");

   private final int sourceNumber;
   private final String displayName;

   ResolveBiome(int sourceNumber, String displayName) {
      this.sourceNumber = sourceNumber;
      this.displayName = displayName;
   }

   public int sourceNumber() {
      return this.sourceNumber;
   }

   public String displayName() {
      return this.displayName;
   }

   public static ResolveBiome fromSource(int ecoId, int biomeNumber, String biomeName) {
      if (ecoId == 0 || biomeName == null || "N/A".equalsIgnoreCase(biomeName.trim())) {
         return ROCK_AND_ICE;
      }

      return switch (biomeNumber) {
         case 1 -> TROPICAL_MOIST_BROADLEAF_FORESTS;
         case 2 -> TROPICAL_DRY_BROADLEAF_FORESTS;
         case 3 -> TROPICAL_CONIFEROUS_FORESTS;
         case 4 -> TEMPERATE_BROADLEAF_MIXED_FORESTS;
         case 5 -> TEMPERATE_CONIFER_FORESTS;
         case 6 -> BOREAL_FORESTS_TAIGA;
         case 7 -> TROPICAL_GRASSLANDS_SAVANNAS_SHRUBLANDS;
         case 8 -> TEMPERATE_GRASSLANDS_SAVANNAS_SHRUBLANDS;
         case 9 -> FLOODED_GRASSLANDS_SAVANNAS;
         case 10 -> MONTANE_GRASSLANDS_SHRUBLANDS;
         case 11 -> TUNDRA;
         case 12 -> MEDITERRANEAN_FORESTS_WOODLANDS_SCRUB;
         case 13 -> DESERTS_XERIC_SHRUBLANDS;
         case 14 -> MANGROVES;
         default -> fromName(biomeName);
      };
   }

   private static ResolveBiome fromName(String biomeName) {
      if (biomeName == null) {
         return UNKNOWN;
      }

      String normalized = biomeName.trim().toLowerCase(Locale.ROOT);
      for (ResolveBiome biome : values()) {
         if (biome != UNKNOWN && biome.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
            return biome;
         }
      }
      return UNKNOWN;
   }
}
