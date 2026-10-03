package com.yucareux.tellus.world.data.osm;

import java.util.Locale;

public enum RoadPointKind {
   STREET_LIGHT,
   TRAFFIC_SIGNAL,
   BUS_STOP,
   CROSSING,
   BENCH,
   WASTE_BASKET,
   RECYCLING,
   BICYCLE_PARKING,
   FOUNTAIN,
   BOLLARD;

   public static RoadPointKind fromInfrastructureClass(String classTag) {
      if (classTag == null) {
         return null;
      }

      String normalized = classTag.trim().toLowerCase(Locale.ROOT).replace('-', '_');
      return switch (normalized) {
         case "street_lamp", "street_light" -> STREET_LIGHT;
         case "traffic_signals", "traffic_signal" -> TRAFFIC_SIGNAL;
         case "bus_stop", "bus_station" -> BUS_STOP;
         case "crossing", "crosswalk", "pedestrian_crossing" -> CROSSING;
         case "bench" -> BENCH;
         case "waste_basket", "waste", "trash", "bin" -> WASTE_BASKET;
         case "recycling", "recycling_container" -> RECYCLING;
         case "bicycle_parking", "bike_parking" -> BICYCLE_PARKING;
         case "fountain", "drinking_water" -> FOUNTAIN;
         case "bollard", "gate" -> BOLLARD;
         default -> null;
      };
   }
}
