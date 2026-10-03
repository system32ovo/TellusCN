package com.yucareux.tellus.world.data.osm;

import java.util.Locale;

public enum OsmWaterKind {
   UNKNOWN(false, false),
   OCEAN(false, true),
   SEA(false, true),
   RIVER(true, false),
   STREAM(true, false),
   CANAL(true, false),
   DITCH(true, false),
   DRAIN(true, false),
   LAKE(false, false),
   RESERVOIR(false, false),
   POND(false, false),
   BASIN(false, false),
   LAGOON(false, false),
   WETLAND(false, false),
   WATERFALL(false, false);

   private final boolean flowing;
   private final boolean ocean;

   OsmWaterKind(boolean flowing, boolean ocean) {
      this.flowing = flowing;
      this.ocean = ocean;
   }

   public boolean flowing() {
      return this.flowing;
   }

   public boolean ocean() {
      return this.ocean;
   }

   public static OsmWaterKind fromTags(String classTag, String subtype) {
      OsmWaterKind kind = fromTag(subtype);
      return kind != UNKNOWN ? kind : fromTag(classTag);
   }

   public static OsmWaterKind fromOrdinal(int ordinal) {
      OsmWaterKind[] values = values();
      return ordinal >= 0 && ordinal < values.length ? values[ordinal] : UNKNOWN;
   }

   private static OsmWaterKind fromTag(String tag) {
      if (tag == null || tag.isBlank()) {
         return UNKNOWN;
      }

      String normalized = tag.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
      return switch (normalized) {
         case "ocean" -> OCEAN;
         case "sea" -> SEA;
         case "river", "riverbank" -> RIVER;
         case "stream" -> STREAM;
         case "canal" -> CANAL;
         case "ditch" -> DITCH;
         case "drain" -> DRAIN;
         case "lake" -> LAKE;
         case "reservoir" -> RESERVOIR;
         case "pond" -> POND;
         case "basin" -> BASIN;
         case "lagoon" -> LAGOON;
         case "wetland", "marsh", "swamp" -> WETLAND;
         case "waterfall" -> WATERFALL;
         default -> UNKNOWN;
      };
   }
}
