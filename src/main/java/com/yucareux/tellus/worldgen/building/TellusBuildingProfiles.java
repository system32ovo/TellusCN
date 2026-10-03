package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.OsmBuildingMetadata;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

public final class TellusBuildingProfiles {
   private static final double DEFAULT_STOREY_METERS = 3.2;

   private TellusBuildingProfiles() {
   }

   public static BuildingProfile resolveProfile(OsmBuildingFeature feature, double worldScale, Holder<Biome> biome, boolean interiorsEnabled) {
      OsmBuildingMetadata metadata = feature.metadata();
      int floorCount = Math.max(Math.max(1, metadata.floorCount()), inferFloorCount(feature.heightMeters()));
      double area = feature.areaSquareMeters();
      BuildingProfile.BuildingCategory category = resolveCategory(metadata, area, floorCount, feature.heightMeters(), feature);
      if (category == BuildingProfile.BuildingCategory.TALL_BUILDING
         || category == BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER
         || category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER
         || category == BuildingProfile.BuildingCategory.TOWER) {
         floorCount = Math.max(floorCount, inferFloorCount(feature.heightMeters()));
      } else if (category == BuildingProfile.BuildingCategory.GARAGE
         || category == BuildingProfile.BuildingCategory.SHED
         || category == BuildingProfile.BuildingCategory.GREENHOUSE) {
         floorCount = 1;
      }

      BuildingProfile.Archetype archetype = archetypeForCategory(category);
      BuildingProfile.ClimateFamily climate = climateFamily(biome);
      int storeyHeightBlocks = interiorsEnabled ? 4 : Math.max(1, (int)Math.round(DEFAULT_STOREY_METERS / Math.max(1.0, worldScale)));
      BuildingProfile.RoofProfile roofProfile = resolveRoofProfile(metadata, category, archetype, feature);
      int parapetHeight = switch (category) {
         case HOUSE, FARM, GARAGE, SHED, GREENHOUSE -> 0;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> 2;
         default -> 1;
      };
      int roofRise = switch (roofProfile) {
         case GABLED_X, GABLED_Z, HIPPED, PYRAMIDAL, SKILLION, DOME -> pitchedRoofRise(feature, worldScale, floorCount, storeyHeightBlocks);
         case FLAT_CROWN -> 2;
         case FLAT_SKYLIGHT -> 1;
         case FLAT, FLAT_PARAPET -> 0;
      };
      int setbackEveryFloors = 0;
      int maxSetback = 0;
      if (floorCount >= 12 || archetype == BuildingProfile.Archetype.TOWER) {
         setbackEveryFloors = category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER ? 6 : 8;
         maxSetback = category == BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER ? 4 : 3;
      }

      int windowSpacing = switch (category) {
         case GREENHOUSE -> 3;
         case TOWER, GLASSY_SKYSCRAPER -> 4;
         case HOUSE, RESIDENTIAL, HOTEL, FARM, COMMERCIAL, OFFICE, SCHOOL, HOSPITAL, RELIGIOUS, HISTORIC, TALL_BUILDING, MODERN_SKYSCRAPER,
            INDUSTRIAL, WAREHOUSE, GARAGE, SHED, GENERIC -> 6;
      };
      return new BuildingProfile(
         archetype,
         category,
         roofProfile,
         climate,
         floorCount,
         storeyHeightBlocks,
         interiorsEnabled,
         parapetHeight,
         roofRise,
         setbackEveryFloors,
         maxSetback,
         windowSpacing
      );
   }

   public static int inferFloorCount(double heightMeters) {
      return Math.max(1, (int)Math.round(heightMeters / DEFAULT_STOREY_METERS));
   }

   static BuildingProfile.BuildingCategory resolveCategory(
      OsmBuildingMetadata metadata, double areaSquareMeters, int floorCount, double heightMeters, OsmBuildingFeature feature
   ) {
      String type = metadata == null ? "" : metadata.combinedTypeText();
      String wallMaterial = metadata == null ? "" : text(metadata.wallMaterial());
      String roofMaterial = metadata == null ? "" : text(metadata.roofMaterial());
      String materialText = wallMaterial + " " + roofMaterial;
      if (text(metadata == null ? null : metadata.manMade()).contains("tower")
         || containsAny(type, "water_tower", "communications_tower", "observation_tower")) {
         return BuildingProfile.BuildingCategory.TOWER;
      }
      if (metadata != null && metadata.historic() != null || containsAny(type, "castle", "ruins", "monument", "heritage")) {
         return BuildingProfile.BuildingCategory.HISTORIC;
      }
      if (containsAny(type, "place_of_worship", "church", "chapel", "mosque", "synagogue", "temple", "cathedral", "religious")) {
         return BuildingProfile.BuildingCategory.RELIGIOUS;
      }
      if (containsAny(type, "hospital", "clinic", "healthcare")) {
         return BuildingProfile.BuildingCategory.HOSPITAL;
      }
      if (containsAny(type, "school", "university", "college", "kindergarten", "education")) {
         return BuildingProfile.BuildingCategory.SCHOOL;
      }
      if (containsAny(type, "greenhouse", "glasshouse")) {
         return BuildingProfile.BuildingCategory.GREENHOUSE;
      }
      if (containsAny(type, "garage", "garages", "carport", "parking")) {
         return BuildingProfile.BuildingCategory.GARAGE;
      }
      if (containsAny(type, "shed", "hut", "kiosk")) {
         return BuildingProfile.BuildingCategory.SHED;
      }
      if (containsAny(type, "farm_auxiliary", "farm", "barn", "stable", "cowshed", "sty")) {
         return BuildingProfile.BuildingCategory.FARM;
      }
      if (containsAny(type, "warehouse", "hangar", "depot", "storage")) {
         return BuildingProfile.BuildingCategory.WAREHOUSE;
      }
      if (containsAny(type, "industrial", "factory", "manufacture", "works", "plant")) {
         return BuildingProfile.BuildingCategory.INDUSTRIAL;
      }

      if ((heightMeters >= 160.0 && hasSkyscraperProportions(feature, heightMeters)) || containsAny(type, "skyscraper")) {
         return skyscraperCategory(type, materialText, feature == null ? 0L : feature.featureId());
      }
      if (heightMeters > 28.0 || floorCount > 7 || containsAny(type, "highrise", "high_rise", "tower")) {
         return BuildingProfile.BuildingCategory.TALL_BUILDING;
      }

      if (containsAny(type, "hotel", "motel", "guest_house", "hostel", "aparthotel")) {
         return BuildingProfile.BuildingCategory.HOTEL;
      }
      if (metadata != null && metadata.office() != null || containsAny(type, "office", "business")) {
         return BuildingProfile.BuildingCategory.OFFICE;
      }
      if (metadata != null && metadata.shop() != null || containsAny(type, "shop", "retail", "commercial", "mall", "supermarket", "market", "store")) {
         return BuildingProfile.BuildingCategory.COMMERCIAL;
      }
      if (containsAny(type, "apartments", "apartment", "residential", "flat", "condo", "dormitory", "terrace")) {
         return areaSquareMeters <= 220.0 && floorCount <= 3 ? BuildingProfile.BuildingCategory.HOUSE : BuildingProfile.BuildingCategory.RESIDENTIAL;
      }
      if (containsAny(type, "house", "home", "detached", "semidetached", "bungalow", "cabin", "villa", "dwelling")) {
         return BuildingProfile.BuildingCategory.HOUSE;
      }

      if (areaSquareMeters <= 220.0 && floorCount <= 3) {
         return BuildingProfile.BuildingCategory.HOUSE;
      }
      if (areaSquareMeters >= 900.0 && floorCount <= 3) {
         return BuildingProfile.BuildingCategory.WAREHOUSE;
      }
      if (areaSquareMeters <= 650.0 && floorCount <= 6) {
         return BuildingProfile.BuildingCategory.RESIDENTIAL;
      }
      return areaSquareMeters > 0.0 ? BuildingProfile.BuildingCategory.COMMERCIAL : BuildingProfile.BuildingCategory.GENERIC;
   }

   private static BuildingProfile.BuildingCategory skyscraperCategory(String type, String materialText, long featureId) {
      if (containsAny(materialText + " " + type, "glass", "curtain")) {
         return BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER;
      }
      long mixed = mixSeed(featureId);
      return Math.floorMod((int)(mixed ^ mixed >>> 32), 3) == 0
         ? BuildingProfile.BuildingCategory.GLASSY_SKYSCRAPER
         : BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER;
   }

   private static boolean hasSkyscraperProportions(OsmBuildingFeature feature, double heightMeters) {
      if (feature == null || heightMeters < 40.0) {
         return false;
      }
      double metersPerLonDegree = 111319.49166666667 * Math.cos(Math.toRadians(feature.centroidLat()));
      double widthMeters = Math.abs(feature.maxLon() - feature.minLon()) * Math.max(1.0, metersPerLonDegree);
      double depthMeters = Math.abs(feature.maxLat() - feature.minLat()) * 111319.49166666667;
      double longestSide = Math.max(1.0, Math.max(widthMeters, depthMeters));
      return heightMeters / longestSide >= 2.0;
   }

   private static BuildingProfile.Archetype archetypeForCategory(BuildingProfile.BuildingCategory category) {
      return switch (category) {
         case HOUSE, FARM, GARAGE, SHED, GREENHOUSE -> BuildingProfile.Archetype.HOUSE;
         case RESIDENTIAL, HOTEL -> BuildingProfile.Archetype.APARTMENT;
         case COMMERCIAL, OFFICE, SCHOOL, HOSPITAL, RELIGIOUS, HISTORIC -> BuildingProfile.Archetype.COMMERCIAL;
         case INDUSTRIAL, WAREHOUSE -> BuildingProfile.Archetype.INDUSTRIAL;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BuildingProfile.Archetype.TOWER;
         case GENERIC -> BuildingProfile.Archetype.GENERIC;
      };
   }

   private static BuildingProfile.RoofProfile resolveRoofProfile(
      OsmBuildingMetadata metadata, BuildingProfile.BuildingCategory category, BuildingProfile.Archetype archetype, OsmBuildingFeature feature
   ) {
      String roofShape = metadata == null ? null : metadata.roofShape();
      if (roofShape != null) {
         String normalized = roofShape.toLowerCase(Locale.ROOT);
         if (containsAny(normalized, "dome", "round", "onion")) {
            return BuildingProfile.RoofProfile.DOME;
         }
         if (containsAny(normalized, "pyramid", "pyramidal")) {
            return BuildingProfile.RoofProfile.PYRAMIDAL;
         }
         if (containsAny(normalized, "skillion", "shed")) {
            return BuildingProfile.RoofProfile.SKILLION;
         }
         if (normalized.contains("hip")) {
            return BuildingProfile.RoofProfile.HIPPED;
         }
         if (normalized.contains("gabled") || normalized.contains("gable")) {
            return feature.widthLongerThanDepth() ? BuildingProfile.RoofProfile.GABLED_X : BuildingProfile.RoofProfile.GABLED_Z;
         }
         if (normalized.contains("flat")) {
            if (containsAny(normalized, "parapet") || metadata.roofLevels() > 0) {
               return BuildingProfile.RoofProfile.FLAT_PARAPET;
            }
            return archetype == BuildingProfile.Archetype.TOWER ? BuildingProfile.RoofProfile.FLAT_CROWN : BuildingProfile.RoofProfile.FLAT;
         }
      }

      return switch (category) {
         case HOUSE -> feature.areaSquareMeters() <= 400.0 && Math.floorMod(feature.featureId(), 4) == 0
            ? BuildingProfile.RoofProfile.HIPPED
            : feature.widthLongerThanDepth() ? BuildingProfile.RoofProfile.GABLED_X : BuildingProfile.RoofProfile.GABLED_Z;
         case FARM -> feature.widthLongerThanDepth() ? BuildingProfile.RoofProfile.GABLED_X : BuildingProfile.RoofProfile.GABLED_Z;
         case RESIDENTIAL -> shouldAutoPitchResidentialRoof(feature)
            ? (feature.widthLongerThanDepth() ? BuildingProfile.RoofProfile.GABLED_X : BuildingProfile.RoofProfile.GABLED_Z)
            : BuildingProfile.RoofProfile.FLAT;
         case GARAGE, SHED -> BuildingProfile.RoofProfile.SKILLION;
         case GREENHOUSE, INDUSTRIAL, WAREHOUSE -> BuildingProfile.RoofProfile.FLAT_SKYLIGHT;
         case RELIGIOUS -> Math.floorMod((int)feature.featureId(), 3) == 0 ? BuildingProfile.RoofProfile.DOME : BuildingProfile.RoofProfile.GABLED_X;
         case HISTORIC -> BuildingProfile.RoofProfile.HIPPED;
         case TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BuildingProfile.RoofProfile.FLAT_CROWN;
         case HOTEL, COMMERCIAL, OFFICE, SCHOOL, HOSPITAL, GENERIC -> BuildingProfile.RoofProfile.FLAT;
      };
   }

   private static int pitchedRoofRise(OsmBuildingFeature feature, double worldScale, int floorCount, int storeyHeightBlocks) {
      double scaledFootprint = Math.sqrt(Math.max(1.0, feature.areaSquareMeters())) / Math.max(1.0, worldScale);
      int spanRise = (int)Math.round(scaledFootprint / 3.5);
      int wallCap = Math.max(2, (int)Math.round(floorCount * storeyHeightBlocks * 0.6));
      return Math.max(2, Math.min(Math.min(6, wallCap), Math.max(spanRise, (int)Math.round(feature.heightMeters() / 8.0))));
   }

   private static boolean shouldAutoPitchResidentialRoof(OsmBuildingFeature feature) {
      if (feature == null || feature.areaSquareMeters() > 800.0) {
         return false;
      }

      long mixed = mixSeed(feature.featureId() ^ 0x5A17D00FL);
      return Math.floorMod((int)(mixed ^ mixed >>> 32), 10) != 0;
   }

   public static BuildingProfile.ClimateFamily climateFamily(Holder<Biome> biome) {
      if (biome == null) {
         return BuildingProfile.ClimateFamily.TEMPERATE;
      }

      Biome value = biome.value();
      float temperature = value.getBaseTemperature();
      boolean precipitation = value.hasPrecipitation();
      if (temperature <= 0.3F) {
         return BuildingProfile.ClimateFamily.COLD;
      }
      if (temperature >= 1.2F && !precipitation) {
         return BuildingProfile.ClimateFamily.ARID;
      }
      if (temperature >= 1.0F && precipitation) {
         return BuildingProfile.ClimateFamily.TROPICAL;
      }
      return BuildingProfile.ClimateFamily.TEMPERATE;
   }

   private static boolean containsAny(String value, String... parts) {
      for (String part : parts) {
         if (value.contains(part)) {
            return true;
         }
      }
      return false;
   }

   private static String text(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }

   private static long mixSeed(long seed) {
      seed ^= seed >>> 33;
      seed *= -49064778989728563L;
      seed ^= seed >>> 33;
      seed *= -4265267296055464877L;
      seed ^= seed >>> 33;
      return seed;
   }
}
