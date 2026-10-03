package com.yucareux.tellus.world.data.mask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TellusLandMaskSourceTest {
   @Test
   void adaptsZoomToSelectedWorldScaleWithinSourceLimits() {
      assertEquals(17, TellusLandMaskSource.selectZoom(1.0, 0, 17));
      assertEquals(10, TellusLandMaskSource.selectZoom(160.0, 0, 17));
      assertEquals(7, TellusLandMaskSource.selectZoom(1000.0, 0, 17));
      assertEquals(8, TellusLandMaskSource.selectZoom(1000.0, 8, 17));
   }
}
