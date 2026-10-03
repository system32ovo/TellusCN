package com.yucareux.tellus.world.data.osm;

public record OsmBuildingMetadata(
   String buildingClass,
   String subtype,
   String use,
   String name,
   int floorCount,
   String roofShape,
   String roofMaterial,
   String roofColor,
   String wallMaterial,
   String wallColor,
   String amenity,
   String tourism,
   String office,
   String shop,
   String manMade,
   String historic,
   String buildingPartType,
   int roofLevels,
   int minLevel
) {
   public OsmBuildingMetadata(
      String buildingClass,
      String subtype,
      String use,
      String name,
      int floorCount,
      String roofShape,
      String roofMaterial,
      String roofColor,
      String wallMaterial,
      String wallColor
   ) {
      this(buildingClass, subtype, use, name, floorCount, roofShape, roofMaterial, roofColor, wallMaterial, wallColor, null, null, null, null, null, null, null, 0, 0);
   }

   public OsmBuildingMetadata {
      floorCount = Math.max(1, floorCount);
      buildingClass = normalize(buildingClass);
      subtype = normalize(subtype);
      use = normalize(use);
      name = normalize(name);
      roofShape = normalize(roofShape);
      roofMaterial = normalize(roofMaterial);
      roofColor = normalize(roofColor);
      wallMaterial = normalize(wallMaterial);
      wallColor = normalize(wallColor);
      amenity = normalize(amenity);
      tourism = normalize(tourism);
      office = normalize(office);
      shop = normalize(shop);
      manMade = normalize(manMade);
      historic = normalize(historic);
      buildingPartType = normalize(buildingPartType);
      roofLevels = Math.max(0, roofLevels);
      minLevel = Math.max(0, minLevel);
   }

   public String primaryType() {
      return firstNonNull(this.use, this.subtype, this.buildingPartType, this.buildingClass, this.amenity, this.tourism, this.office, this.shop, this.manMade, this.historic);
   }

   public String combinedTypeText() {
      StringBuilder builder = new StringBuilder();
      append(builder, this.buildingClass);
      append(builder, this.subtype);
      append(builder, this.use);
      append(builder, this.amenity);
      append(builder, this.tourism);
      append(builder, this.office);
      append(builder, this.shop);
      append(builder, this.manMade);
      append(builder, this.historic);
      append(builder, this.buildingPartType);
      return builder.toString();
   }

   private static String normalize(String value) {
      return value == null || value.isBlank() ? null : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
   }

   private static String firstNonNull(String... values) {
      for (String value : values) {
         if (value != null) {
            return value;
         }
      }
      return null;
   }

   private static void append(StringBuilder builder, String value) {
      if (value != null) {
         if (!builder.isEmpty()) {
            builder.append(' ');
         }
         builder.append(value);
      }
   }
}
