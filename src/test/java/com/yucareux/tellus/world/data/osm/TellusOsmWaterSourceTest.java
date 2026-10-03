package com.yucareux.tellus.world.data.osm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TellusOsmWaterSourceTest {
   @Test
   void adaptsZoomToSelectedWorldScaleWithinSourceLimits() {
      assertEquals(14, TellusOsmWaterSource.selectQueryZoomForScale(1.0, 0, 14, 96));
      assertEquals(9, TellusOsmWaterSource.selectQueryZoomForScale(500.0, 0, 14, 96));
      assertEquals(8, TellusOsmWaterSource.selectQueryZoomForScale(1000.0, 0, 14, 96));
      assertEquals(8, TellusOsmWaterSource.selectQueryZoomForScale(1000.0, 8, 14, 96));
   }
}
