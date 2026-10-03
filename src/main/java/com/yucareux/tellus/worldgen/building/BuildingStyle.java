package com.yucareux.tellus.worldgen.building;

public record BuildingStyle(
   BuildingStyle.FacadeFamily facadeFamily,
   BuildingStyle.WindowPattern windowPattern,
   BuildingStyle.GroundFloorTreatment groundFloorTreatment,
   BuildingStyle.BalconyProfile balconyProfile,
   BuildingStyle.RoofDetail roofDetail,
   BuildingStyle.WallDepthStyle wallDepthStyle,
   BuildingStyle.MaterialHint wallMaterialHint,
   BuildingStyle.MaterialHint roofMaterialHint,
   boolean garageDoor,
   boolean singleDoor,
   boolean chimney,
   boolean parapet,
   int facadePhase,
   int accentPhase,
   int windowSpacing,
   int verticalAccentSpacing
) {
   public BuildingStyle(
      BuildingStyle.FacadeFamily facadeFamily,
      BuildingStyle.WindowPattern windowPattern,
      BuildingStyle.GroundFloorTreatment groundFloorTreatment,
      BuildingStyle.BalconyProfile balconyProfile,
      BuildingStyle.RoofDetail roofDetail,
      int facadePhase,
      int accentPhase,
      int windowSpacing,
      int verticalAccentSpacing
   ) {
      this(
         facadeFamily,
         windowPattern,
         groundFloorTreatment,
         balconyProfile,
         roofDetail,
         WallDepthStyle.NONE,
         MaterialHint.NONE,
         MaterialHint.NONE,
         false,
         false,
         false,
         false,
         facadePhase,
         accentPhase,
         windowSpacing,
         verticalAccentSpacing
      );
   }

   public BuildingStyle {
      facadeFamily = facadeFamily == null ? FacadeFamily.MASONRY : facadeFamily;
      windowPattern = windowPattern == null ? WindowPattern.PUNCHED : windowPattern;
      groundFloorTreatment = groundFloorTreatment == null ? GroundFloorTreatment.PLAIN : groundFloorTreatment;
      balconyProfile = balconyProfile == null ? BalconyProfile.NONE : balconyProfile;
      roofDetail = roofDetail == null ? RoofDetail.SIMPLE : roofDetail;
      wallDepthStyle = wallDepthStyle == null ? WallDepthStyle.NONE : wallDepthStyle;
      wallMaterialHint = wallMaterialHint == null ? MaterialHint.NONE : wallMaterialHint;
      roofMaterialHint = roofMaterialHint == null ? MaterialHint.NONE : roofMaterialHint;
      facadePhase = Math.max(0, facadePhase);
      accentPhase = Math.max(0, accentPhase);
      windowSpacing = Math.max(2, windowSpacing);
      verticalAccentSpacing = Math.max(2, verticalAccentSpacing);
   }

   public boolean detailedExterior(double worldScale) {
      return worldScale > 0.0 && worldScale <= 15.0;
   }

   public enum FacadeFamily {
      MASONRY,
      BRICK_ROW,
      STUCCO,
      MODERN_GRID,
      CURTAIN_WALL,
      INDUSTRIAL,
      HISTORIC,
      RELIGIOUS,
      FARM,
      GREENHOUSE
   }

   public enum WindowPattern {
      PUNCHED,
      RIBBON,
      GRID,
      CURTAIN,
      INDUSTRIAL_STRIP,
      SPARSE
   }

   public enum GroundFloorTreatment {
      PLAIN,
      RESIDENTIAL,
      STOREFRONT,
      LOBBY,
      LOADING
   }

   public enum BalconyProfile {
      NONE,
      LIGHT,
      FREQUENT,
      FIRE_ESCAPE
   }

   public enum RoofDetail {
      SIMPLE,
      PARAPET,
      HVAC,
      SKYLIGHT,
      CROWN,
      ANTENNA
   }

   public enum WallDepthStyle {
      NONE,
      SUBTLE_PILASTERS,
      MODERN_PILLARS,
      INSTITUTIONAL_BANDS,
      INDUSTRIAL_BEAMS,
      HISTORIC_ORNATE,
      RELIGIOUS_BUTTRESS,
      SKYSCRAPER_FINS,
      GLASS_CURTAIN
   }

   public enum MaterialHint {
      NONE,
      BRICK,
      STONE,
      SANDSTONE,
      WOOD,
      GLASS,
      CONCRETE,
      DARK,
      WHITE,
      RED,
      BROWN,
      GRAY,
      BLUE,
      GREEN,
      METAL
   }
}
