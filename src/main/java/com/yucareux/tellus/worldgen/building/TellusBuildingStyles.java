package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingMetadata;
import java.util.Locale;

public final class TellusBuildingStyles {
   private static final TellusBuildingStyles.HouseStyle[] TEMPERATE_HOUSE_STYLES = new TellusBuildingStyles.HouseStyle[]{
      TellusBuildingStyles.HouseStyle.WHITE_SLATE,
      TellusBuildingStyles.HouseStyle.GRAY_CHARCOAL,
      TellusBuildingStyles.HouseStyle.BRICK_SLATE,
      TellusBuildingStyles.HouseStyle.PALE_STONE,
      TellusBuildingStyles.HouseStyle.WARM_CLAY
   };
   private static final TellusBuildingStyles.HouseStyle[] COLD_HOUSE_STYLES = new TellusBuildingStyles.HouseStyle[]{
      TellusBuildingStyles.HouseStyle.GRAY_CHARCOAL,
      TellusBuildingStyles.HouseStyle.WHITE_SLATE,
      TellusBuildingStyles.HouseStyle.BRICK_SLATE,
      TellusBuildingStyles.HouseStyle.PALE_STONE
   };
   private static final TellusBuildingStyles.HouseStyle[] ARID_HOUSE_STYLES = new TellusBuildingStyles.HouseStyle[]{
      TellusBuildingStyles.HouseStyle.WARM_CLAY,
      TellusBuildingStyles.HouseStyle.PALE_STONE,
      TellusBuildingStyles.HouseStyle.WHITE_SLATE
   };
   private static final TellusBuildingStyles.HouseStyle[] TROPICAL_HOUSE_STYLES = new TellusBuildingStyles.HouseStyle[]{
      TellusBuildingStyles.HouseStyle.WHITE_SLATE,
      TellusBuildingStyles.HouseStyle.WARM_CLAY,
      TellusBuildingStyles.HouseStyle.PALE_STONE,
      TellusBuildingStyles.HouseStyle.GRAY_CHARCOAL
   };

   private TellusBuildingStyles() {
   }

   public static TellusBuildingStyles.HouseStyle resolveHouseStyle(BuildingProfile profile, long blueprintSeed) {
      TellusBuildingStyles.HouseStyle[] styles = switch (profile.climateFamily()) {
         case COLD -> COLD_HOUSE_STYLES;
         case ARID -> ARID_HOUSE_STYLES;
         case TROPICAL -> TROPICAL_HOUSE_STYLES;
         case TEMPERATE -> TEMPERATE_HOUSE_STYLES;
      };
      long mixedSeed = mixSeed(blueprintSeed, profile.floorCount(), profile.roofProfile().ordinal());
      return styles[Math.floorMod(mixedSeed, styles.length)];
   }

   public static BuildingStyle resolveBuildingStyle(
      BuildingProfile profile, OsmBuildingMetadata metadata, double areaSquareMeters, int widthBlocks, int depthBlocks, long blueprintSeed
   ) {
      long mixedSeed = mixSeed(blueprintSeed, profile.floorCount(), profile.roofProfile().ordinal());
      int facadePhase = positiveMod(mixedSeed, Math.max(2, profile.windowSpacing()));
      int accentPhase = positiveMod(Long.rotateLeft(mixedSeed, 17), Math.max(2, profile.windowSpacing() + 2));
      BuildingProfile.BuildingCategory category = profile.category();
      String type = metadata == null ? "" : text(metadata.combinedTypeText());
      String wallText = metadata == null ? "" : text(metadata.wallMaterial()) + " " + text(metadata.wallColor());
      String roofText = metadata == null ? "" : text(metadata.roofMaterial()) + " " + text(metadata.roofColor());
      String material = wallText + " " + roofText + " " + type;
      boolean brick = containsAny(material, "brick", "red", "brown");
      boolean glass = containsAny(material, "glass", "curtain", "greenhouse");
      boolean concrete = containsAny(material, "concrete", "cement", "office", "commercial", "retail", "hotel");
      boolean compact = widthBlocks <= 8 || depthBlocks <= 8 || areaSquareMeters <= 180.0;
      BuildingStyle.MaterialHint wallHint = materialHint(wallText, type);
      BuildingStyle.MaterialHint roofHint = materialHint(roofText, type);

      BuildingStyle.FacadeFamily facade = switch (category) {
         case HOUSE -> brick ? BuildingStyle.FacadeFamily.BRICK_ROW : BuildingStyle.FacadeFamily.STUCCO;
         case RESIDENTIAL -> brick || positiveMod(mixedSeed, 3) != 0 ? BuildingStyle.FacadeFamily.BRICK_ROW : BuildingStyle.FacadeFamily.MASONRY;
         case FARM, SHED -> BuildingStyle.FacadeFamily.FARM;
         case GREENHOUSE -> BuildingStyle.FacadeFamily.GREENHOUSE;
         case GARAGE -> BuildingStyle.FacadeFamily.MASONRY;
         case COMMERCIAL, HOTEL -> glass || positiveMod(mixedSeed, 2) == 0 ? BuildingStyle.FacadeFamily.MODERN_GRID : BuildingStyle.FacadeFamily.MASONRY;
         case OFFICE -> glass || concrete ? BuildingStyle.FacadeFamily.CURTAIN_WALL : BuildingStyle.FacadeFamily.MODERN_GRID;
         case SCHOOL, HOSPITAL -> BuildingStyle.FacadeFamily.MASONRY;
         case RELIGIOUS -> BuildingStyle.FacadeFamily.RELIGIOUS;
         case HISTORIC -> BuildingStyle.FacadeFamily.HISTORIC;
         case INDUSTRIAL, WAREHOUSE -> BuildingStyle.FacadeFamily.INDUSTRIAL;
         case TOWER, TALL_BUILDING, MODERN_SKYSCRAPER -> BuildingStyle.FacadeFamily.MODERN_GRID;
         case GLASSY_SKYSCRAPER -> BuildingStyle.FacadeFamily.CURTAIN_WALL;
         case GENERIC -> brick ? BuildingStyle.FacadeFamily.BRICK_ROW : BuildingStyle.FacadeFamily.MASONRY;
      };
      BuildingStyle.WindowPattern windows = switch (category) {
         case HOUSE, FARM -> compact ? BuildingStyle.WindowPattern.SPARSE : BuildingStyle.WindowPattern.PUNCHED;
         case RESIDENTIAL, HOTEL -> positiveMod(mixedSeed, 4) == 0 ? BuildingStyle.WindowPattern.RIBBON : BuildingStyle.WindowPattern.PUNCHED;
         case GARAGE, SHED -> BuildingStyle.WindowPattern.SPARSE;
         case GREENHOUSE, GLASSY_SKYSCRAPER -> BuildingStyle.WindowPattern.CURTAIN;
         case COMMERCIAL, OFFICE, SCHOOL, HOSPITAL -> facade == BuildingStyle.FacadeFamily.MODERN_GRID
            ? BuildingStyle.WindowPattern.GRID
            : BuildingStyle.WindowPattern.RIBBON;
         case RELIGIOUS, HISTORIC -> BuildingStyle.WindowPattern.PUNCHED;
         case INDUSTRIAL, WAREHOUSE -> BuildingStyle.WindowPattern.INDUSTRIAL_STRIP;
         case TOWER, TALL_BUILDING, MODERN_SKYSCRAPER -> facade == BuildingStyle.FacadeFamily.CURTAIN_WALL
            ? BuildingStyle.WindowPattern.CURTAIN
            : BuildingStyle.WindowPattern.GRID;
         case GENERIC -> BuildingStyle.WindowPattern.PUNCHED;
      };
      BuildingStyle.GroundFloorTreatment ground = switch (category) {
         case HOUSE, FARM, GREENHOUSE -> BuildingStyle.GroundFloorTreatment.RESIDENTIAL;
         case RESIDENTIAL, HOTEL -> containsAny(type, "shop", "retail", "commercial") || positiveMod(mixedSeed, 5) == 0
            ? BuildingStyle.GroundFloorTreatment.STOREFRONT
            : BuildingStyle.GroundFloorTreatment.LOBBY;
         case COMMERCIAL -> BuildingStyle.GroundFloorTreatment.STOREFRONT;
         case OFFICE, SCHOOL, HOSPITAL, RELIGIOUS, HISTORIC, TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> BuildingStyle.GroundFloorTreatment.LOBBY;
         case GARAGE, SHED, INDUSTRIAL, WAREHOUSE -> BuildingStyle.GroundFloorTreatment.LOADING;
         case GENERIC -> BuildingStyle.GroundFloorTreatment.PLAIN;
      };
      BuildingStyle.BalconyProfile balcony = switch (category) {
         case HOUSE -> BuildingStyle.BalconyProfile.NONE;
         case RESIDENTIAL -> positiveMod(mixedSeed, 4) == 0 ? BuildingStyle.BalconyProfile.FIRE_ESCAPE : BuildingStyle.BalconyProfile.LIGHT;
         case HOTEL -> positiveMod(mixedSeed, 3) == 0 ? BuildingStyle.BalconyProfile.LIGHT : BuildingStyle.BalconyProfile.NONE;
         default -> BuildingStyle.BalconyProfile.NONE;
      };
      BuildingStyle.RoofDetail roof = switch (category) {
         case HOUSE, FARM, GARAGE, SHED -> BuildingStyle.RoofDetail.SIMPLE;
         case GREENHOUSE, INDUSTRIAL, WAREHOUSE -> BuildingStyle.RoofDetail.SKYLIGHT;
         case RESIDENTIAL, COMMERCIAL, OFFICE, HOTEL, SCHOOL, HOSPITAL -> BuildingStyle.RoofDetail.HVAC;
         case RELIGIOUS, HISTORIC, TOWER, TALL_BUILDING, GLASSY_SKYSCRAPER, MODERN_SKYSCRAPER -> positiveMod(mixedSeed, 3) == 0
            ? BuildingStyle.RoofDetail.ANTENNA
            : BuildingStyle.RoofDetail.CROWN;
         case GENERIC -> BuildingStyle.RoofDetail.PARAPET;
      };
      BuildingStyle.WallDepthStyle depthStyle = switch (category) {
         case HOUSE, RESIDENTIAL, HOTEL, FARM -> BuildingStyle.WallDepthStyle.SUBTLE_PILASTERS;
         case COMMERCIAL, OFFICE -> facade == BuildingStyle.FacadeFamily.CURTAIN_WALL
            ? BuildingStyle.WallDepthStyle.GLASS_CURTAIN
            : BuildingStyle.WallDepthStyle.MODERN_PILLARS;
         case SCHOOL, HOSPITAL -> BuildingStyle.WallDepthStyle.INSTITUTIONAL_BANDS;
         case INDUSTRIAL, WAREHOUSE, GARAGE -> BuildingStyle.WallDepthStyle.INDUSTRIAL_BEAMS;
         case RELIGIOUS -> BuildingStyle.WallDepthStyle.RELIGIOUS_BUTTRESS;
         case HISTORIC -> BuildingStyle.WallDepthStyle.HISTORIC_ORNATE;
         case GREENHOUSE -> BuildingStyle.WallDepthStyle.GLASS_CURTAIN;
         case TOWER, TALL_BUILDING, MODERN_SKYSCRAPER -> BuildingStyle.WallDepthStyle.SKYSCRAPER_FINS;
         case GLASSY_SKYSCRAPER -> BuildingStyle.WallDepthStyle.GLASS_CURTAIN;
         case SHED, GENERIC -> BuildingStyle.WallDepthStyle.NONE;
      };
      int verticalAccentSpacing = switch (facade) {
         case CURTAIN_WALL -> 3;
         case MODERN_GRID -> 4;
         case INDUSTRIAL -> 5;
         case RELIGIOUS, HISTORIC -> 5;
         case GREENHOUSE -> 3;
         case FARM -> 6;
         case MASONRY, BRICK_ROW, STUCCO -> 6;
      };
      boolean pitchedRoof = profile.roofProfile() == BuildingProfile.RoofProfile.GABLED_X
         || profile.roofProfile() == BuildingProfile.RoofProfile.GABLED_Z
         || profile.roofProfile() == BuildingProfile.RoofProfile.HIPPED
         || profile.roofProfile() == BuildingProfile.RoofProfile.PYRAMIDAL
         || profile.roofProfile() == BuildingProfile.RoofProfile.SKILLION;
      boolean garageDoor = category == BuildingProfile.BuildingCategory.GARAGE || category == BuildingProfile.BuildingCategory.WAREHOUSE;
      boolean singleDoor = category == BuildingProfile.BuildingCategory.SHED || category == BuildingProfile.BuildingCategory.GREENHOUSE;
      boolean chimney = pitchedRoof && (category == BuildingProfile.BuildingCategory.HOUSE || category == BuildingProfile.BuildingCategory.FARM);
      boolean parapet = profile.parapetHeight() > 0 || profile.roofProfile() == BuildingProfile.RoofProfile.FLAT_PARAPET;
      return new BuildingStyle(
         facade,
         windows,
         ground,
         balcony,
         roof,
         depthStyle,
         wallHint,
         roofHint,
         garageDoor,
         singleDoor,
         chimney,
         parapet,
         facadePhase,
         accentPhase,
         profile.windowSpacing(),
         verticalAccentSpacing
      );
   }

   public static int previewColor(BuildingProfile profile, long blueprintSeed) {
      return switch (profile.category()) {
         case HOUSE -> resolveHouseStyle(profile, blueprintSeed).previewColor();
         case RESIDENTIAL -> 12566463;
         case FARM -> 11635885;
         case COMMERCIAL -> 10000536;
         case OFFICE -> 10395294;
         case HOTEL -> 12632256;
         case INDUSTRIAL -> 7697781;
         case WAREHOUSE -> 6842472;
         case SCHOOL -> 12434877;
         case HOSPITAL -> 14737632;
         case RELIGIOUS -> 11184810;
         case HISTORIC -> 8879216;
         case TOWER -> 7833914;
         case GARAGE -> 7829367;
         case SHED -> 9199947;
         case GREENHOUSE -> 10092543;
         case TALL_BUILDING -> 9145227;
         case GLASSY_SKYSCRAPER -> 7650782;
         case MODERN_SKYSCRAPER -> 8750479;
         case GENERIC -> 11119017;
      };
   }

   private static long mixSeed(long blueprintSeed, int floorCount, int roofProfileOrdinal) {
      long seed = blueprintSeed ^ (long)floorCount * 341873128712L ^ (long)roofProfileOrdinal * 132897987541L;
      seed ^= seed >>> 33;
      seed *= -49064778989728563L;
      seed ^= seed >>> 33;
      seed *= -4265267296055464877L;
      seed ^= seed >>> 33;
      return seed;
   }

   private static int positiveMod(long value, int modulus) {
      return Math.floorMod((int)(value ^ value >>> 32), modulus);
   }

   private static String text(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }

   private static boolean containsAny(String value, String... parts) {
      if (value == null) {
         return false;
      }
      for (String part : parts) {
         if (value.contains(part)) {
            return true;
         }
      }
      return false;
   }

   private static BuildingStyle.MaterialHint materialHint(String text, String type) {
      String value = text(text) + " " + text(type);
      if (containsAny(value, "glass", "greenhouse")) {
         return BuildingStyle.MaterialHint.GLASS;
      }
      if (containsAny(value, "brick", "brickwork")) {
         return BuildingStyle.MaterialHint.BRICK;
      }
      if (containsAny(value, "sandstone", "sand", "beige", "cream")) {
         return BuildingStyle.MaterialHint.SANDSTONE;
      }
      if (containsAny(value, "wood", "timber", "plank", "log")) {
         return BuildingStyle.MaterialHint.WOOD;
      }
      if (containsAny(value, "metal", "steel", "iron", "aluminium", "aluminum")) {
         return BuildingStyle.MaterialHint.METAL;
      }
      if (containsAny(value, "concrete", "cement", "render", "stucco")) {
         return BuildingStyle.MaterialHint.CONCRETE;
      }
      if (containsAny(value, "stone", "granite", "limestone", "marble")) {
         return BuildingStyle.MaterialHint.STONE;
      }
      if (containsAny(value, "white", "quartz")) {
         return BuildingStyle.MaterialHint.WHITE;
      }
      if (containsAny(value, "red")) {
         return BuildingStyle.MaterialHint.RED;
      }
      if (containsAny(value, "brown")) {
         return BuildingStyle.MaterialHint.BROWN;
      }
      if (containsAny(value, "grey", "gray")) {
         return BuildingStyle.MaterialHint.GRAY;
      }
      if (containsAny(value, "black", "dark")) {
         return BuildingStyle.MaterialHint.DARK;
      }
      if (containsAny(value, "blue")) {
         return BuildingStyle.MaterialHint.BLUE;
      }
      if (containsAny(value, "green")) {
         return BuildingStyle.MaterialHint.GREEN;
      }
      return BuildingStyle.MaterialHint.NONE;
   }

   public enum HouseStyle {
      WHITE_SLATE(14543032),
      WARM_CLAY(14140346),
      GRAY_CHARCOAL(12763842),
      BRICK_SLATE(11758425),
      PALE_STONE(14342095);

      private final int previewColor;

      HouseStyle(int previewColor) {
         this.previewColor = previewColor;
      }

      public int previewColor() {
         return this.previewColor;
      }
   }
}
