package com.yucareux.tellus.worldgen.road;

import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.worldgen.arnis.ArnisRoadRules;

/** Deliberately sized families, rather than a random fixture on every road. */
public enum StreetLightStyle {
   PATH(4, 0, 24, false),
   RESIDENTIAL(6, 1, 28, false),
   COLLECTOR(8, 2, 34, false),
   BOULEVARD(9, 3, 40, true),
   HIGHWAY(11, 3, 52, true);

   private final int height;
   private final int arm;
   private final int spacing;
   private final boolean paired;

   StreetLightStyle(int height, int arm, int spacing, boolean paired) {
      this.height = height;
      this.arm = arm;
      this.spacing = spacing;
      this.paired = paired;
   }

   public int height(double scale) {
      // Cantilevers must remain above the generator's three-block road clearance at compressed scales.
      return Math.max(this.arm == 0 ? 3 : 4, (int)Math.round(this.height / Math.max(1.0, scale)));
   }

   public int arm(double scale) {
      return this.arm == 0 ? 0 : Math.max(1, (int)Math.round(this.arm / Math.max(1.0, scale)));
   }

   public int spacing(double scale) {
      return Math.max(12, (int)Math.round(this.spacing / Math.max(1.0, scale)));
   }

   public boolean paired() {
      return this.paired;
   }

   public static StreetLightStyle forRoad(RoadFeature road, int width) {
      if (ArnisRoadRules.isPedestrianLike(road)) return PATH;
      return switch (road.highwayTag()) {
         case "motorway", "motorway_link", "trunk", "trunk_link" -> HIGHWAY;
         case "primary", "secondary" -> width >= 11 ? BOULEVARD : COLLECTOR;
         case "tertiary", "primary_link", "secondary_link", "tertiary_link" -> COLLECTOR;
         default -> width >= 13 ? BOULEVARD : RESIDENTIAL;
      };
   }
}
