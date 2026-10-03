package com.yucareux.tellus.worldgen.building;

public record BuildingProfile(
   BuildingProfile.Archetype archetype,
   BuildingProfile.BuildingCategory category,
   BuildingProfile.RoofProfile roofProfile,
   BuildingProfile.ClimateFamily climateFamily,
   int floorCount,
   int storeyHeightBlocks,
   boolean interiorsEnabled,
   int parapetHeight,
   int roofRise,
   int setbackEveryFloors,
   int maxSetback,
   int windowSpacing
) {
   public BuildingProfile(
      BuildingProfile.Archetype archetype,
      BuildingProfile.RoofProfile roofProfile,
      BuildingProfile.ClimateFamily climateFamily,
      int floorCount,
      int storeyHeightBlocks,
      boolean interiorsEnabled,
      int parapetHeight,
      int roofRise,
      int setbackEveryFloors,
      int maxSetback,
      int windowSpacing
   ) {
      this(
         archetype,
         defaultCategoryFor(archetype),
         roofProfile,
         climateFamily,
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

   public BuildingProfile {
      archetype = archetype == null ? Archetype.GENERIC : archetype;
      category = category == null ? defaultCategoryFor(archetype) : category;
      roofProfile = roofProfile == null ? RoofProfile.FLAT : roofProfile;
      climateFamily = climateFamily == null ? ClimateFamily.TEMPERATE : climateFamily;
      floorCount = Math.max(1, floorCount);
      storeyHeightBlocks = Math.max(1, storeyHeightBlocks);
      parapetHeight = Math.max(0, parapetHeight);
      roofRise = Math.max(0, roofRise);
      setbackEveryFloors = Math.max(0, setbackEveryFloors);
      maxSetback = Math.max(0, maxSetback);
      windowSpacing = Math.max(2, windowSpacing);
   }

   private static BuildingCategory defaultCategoryFor(Archetype archetype) {
      return switch (archetype == null ? Archetype.GENERIC : archetype) {
         case HOUSE -> BuildingCategory.HOUSE;
         case APARTMENT -> BuildingCategory.RESIDENTIAL;
         case COMMERCIAL -> BuildingCategory.COMMERCIAL;
         case INDUSTRIAL -> BuildingCategory.INDUSTRIAL;
         case TOWER -> BuildingCategory.TALL_BUILDING;
         case GENERIC -> BuildingCategory.GENERIC;
      };
   }

   public enum Archetype {
      HOUSE,
      APARTMENT,
      COMMERCIAL,
      INDUSTRIAL,
      TOWER,
      GENERIC
   }

   public enum BuildingCategory {
      HOUSE,
      RESIDENTIAL,
      FARM,
      COMMERCIAL,
      OFFICE,
      HOTEL,
      INDUSTRIAL,
      WAREHOUSE,
      SCHOOL,
      HOSPITAL,
      RELIGIOUS,
      HISTORIC,
      TOWER,
      GARAGE,
      SHED,
      GREENHOUSE,
      TALL_BUILDING,
      GLASSY_SKYSCRAPER,
      MODERN_SKYSCRAPER,
      GENERIC
   }

   public enum RoofProfile {
      GABLED_X,
      GABLED_Z,
      HIPPED,
      PYRAMIDAL,
      SKILLION,
      DOME,
      FLAT,
      FLAT_PARAPET,
      FLAT_CROWN,
      FLAT_SKYLIGHT
   }

   public enum ClimateFamily {
      TEMPERATE,
      COLD,
      ARID,
      TROPICAL
   }
}
