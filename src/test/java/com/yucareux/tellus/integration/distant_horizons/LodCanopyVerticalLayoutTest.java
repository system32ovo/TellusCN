package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LodCanopyVerticalLayoutTest {
   private static final int MIN_Y = -64;
   private static final int ABSOLUTE_TOP = 512;

   @Test
   void oneCrownKeepsOneElevationAcrossSlopedTerrain() {
      int anchorTop = layerTop(100);
      int leafOffset = 12;
      int leafHeight = 6;

      LodCanopyVerticalLayout.Span downhill = LodCanopyVerticalLayout.visibleSpan(
         layerTop(72), anchorTop, leafOffset, leafHeight, ABSOLUTE_TOP
      );
      LodCanopyVerticalLayout.Span uphill = LodCanopyVerticalLayout.visibleSpan(
         layerTop(108), anchorTop, leafOffset, leafHeight, ABSOLUTE_TOP
      );

      assertEquals(downhill, uphill);
      assertEquals(anchorTop + leafOffset, downhill.bottom());
      assertEquals(anchorTop + leafOffset + leafHeight, downhill.top());
   }

   @Test
   void higherTerrainClipsTheCrownWithoutLiftingItsTop() {
      int anchorTop = layerTop(100);
      int expectedCrownTop = anchorTop + 18;

      LodCanopyVerticalLayout.Span partiallyBuried = LodCanopyVerticalLayout.visibleSpan(
         layerTop(115), anchorTop, 12, 6, ABSOLUTE_TOP
      );
      LodCanopyVerticalLayout.Span fullyBuried = LodCanopyVerticalLayout.visibleSpan(
         layerTop(118), anchorTop, 12, 6, ABSOLUTE_TOP
      );

      assertTrue(partiallyBuried.visible());
      assertEquals(layerTop(115), partiallyBuried.bottom());
      assertEquals(expectedCrownTop, partiallyBuried.top());
      assertFalse(fullyBuried.visible());
   }

   @Test
   void unanchoredCanopiesRetainTheLegacyLocalSurfaceBase() {
      int localSurfaceTop = layerTop(140);

      assertEquals(
         localSurfaceTop,
         LodCanopyVerticalLayout.anchorLayerTop(
            LodCanopyVerticalLayout.UNANCHORED_SURFACE_Y,
            MIN_Y,
            ABSOLUTE_TOP,
            localSurfaceTop
         )
      );
   }

   private static int layerTop(int surfaceY) {
      return LodCanopyVerticalLayout.anchorLayerTop(surfaceY, MIN_Y, ABSOLUTE_TOP, 0);
   }
}
