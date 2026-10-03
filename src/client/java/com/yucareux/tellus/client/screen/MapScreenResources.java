package com.yucareux.tellus.client.screen;

import com.yucareux.tellus.client.widget.map.PlaceSearchWidget;
import com.yucareux.tellus.client.widget.map.SlippyMapWidget;

final class MapScreenResources {
   private MapScreenResources() {
   }

   static void close(SlippyMapWidget mapWidget, PlaceSearchWidget searchWidget) {
      try {
         if (mapWidget != null) {
            mapWidget.close();
         }
      } finally {
         if (searchWidget != null) {
            searchWidget.close();
         }
      }
   }
}
