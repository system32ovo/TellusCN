package com.yucareux.tellus.world.data.osm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSurfaceStyleTest {
   @Test
   void normalizesSurfaceAndSubclassTokens() {
      assertEquals("fine_gravel", RoadSurfaceStyle.normalizeSurface(" Fine-Gravel "));
      assertEquals("sidewalk", RoadSurfaceStyle.normalizeSubclass(" Sidewalk "));
      assertEquals("", RoadSurfaceStyle.normalizeSurface("null"));
   }

   @Test
   void usesArnisWidthStampForExplicitAndDefaultRoads() {
      RoadFeature wideRoad = road(RoadClass.MAIN, "primary", "asphalt", "", 999.0);
      RoadFeature narrowRoad = road(RoadClass.NORMAL, "residential", "asphalt", "", 0.25);

      assertEquals(17, RoadSurfaceStyle.effectiveRoadWidth(wideRoad, 4, 1.0));
      assertEquals(5, RoadSurfaceStyle.effectiveRoadWidth(narrowRoad, 3, 1.0));
   }

   @Test
   void selectsUnpavedAndPedestrianStyles() {
      assertEquals(
         RoadSurfaceStyle.STYLE_GRAVEL,
         RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "track", "gravel", "", 0.0), 0, 0)
      );
      assertEquals(
         RoadSurfaceStyle.STYLE_DIRT,
         RoadSurfaceStyle.surfaceStyleId(road(RoadClass.DIRT, "track", "earth", "", 0.0), 0, 0)
      );
      assertEquals(
         RoadSurfaceStyle.STYLE_PEDESTRIAN,
         RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "footway", "paved", "", 0.0), 0, 0)
      );
   }

   @Test
   void selectsExactSurfacePaletteFamilies() {
      assertEquals(RoadSurfaceStyle.STYLE_COBBLESTONE, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "service", "cobblestone", "", 0.0), 0, 0));
      assertEquals(RoadSurfaceStyle.STYLE_STONE_PAVERS, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "pedestrian", "paving_stones", "", 0.0), 0, 0));
      assertEquals(RoadSurfaceStyle.STYLE_BRICK, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "pedestrian", "bricks", "", 0.0), 0, 0));
      assertEquals(RoadSurfaceStyle.STYLE_SAND, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.DIRT, "path", "sand", "", 0.0), 0, 0));
      assertEquals(RoadSurfaceStyle.STYLE_WOOD, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.DIRT, "path", "wood", "", 0.0), 0, 0));
      assertEquals(RoadSurfaceStyle.STYLE_CONCRETE, RoadSurfaceStyle.surfaceStyleId(road(RoadClass.NORMAL, "service", "concrete", "", 0.0), 0, 0));
   }

   @Test
   void centerMarkingsOnlyApplyToWidePavedMainRoadDashes() {
      RoadFeature mainRoad = road(RoadClass.MAIN, "primary", "asphalt", "", 0.0);
      RoadFeature gravelRoad = road(RoadClass.MAIN, "primary", "gravel", "", 0.0);
      RoadFeature normalRoad = road(RoadClass.NORMAL, "residential", "asphalt", "", 0.0);

      assertTrue(RoadSurfaceStyle.shouldDrawCenterMarking(mainRoad, 6, 0.0, 0.0));
      assertFalse(RoadSurfaceStyle.shouldDrawCenterMarking(mainRoad, 6, 8.0, 0.0));
      assertFalse(RoadSurfaceStyle.shouldDrawCenterMarking(mainRoad, 4, 0.0, 0.0));
      assertFalse(RoadSurfaceStyle.shouldDrawCenterMarking(gravelRoad, 6, 0.0, 0.0));
      assertFalse(RoadSurfaceStyle.shouldDrawCenterMarking(normalRoad, 6, 0.0, 0.0));
   }

   @Test
   void normalizesLaneMetadata() {
      assertEquals(0, RoadSurfaceStyle.normalizeLaneCount(0));
      assertEquals(1, RoadSurfaceStyle.normalizeLaneCount(1));
      assertEquals(16, RoadSurfaceStyle.normalizeLaneCount(99));
      assertTrue(RoadSurfaceStyle.normalizeLaneMarkings(null));
      assertTrue(RoadSurfaceStyle.normalizeLaneMarkings("yes"));
      assertFalse(RoadSurfaceStyle.normalizeLaneMarkings("no"));
      assertFalse(RoadSurfaceStyle.normalizeLaneMarkings("false"));
      assertFalse(RoadSurfaceStyle.normalizeLaneMarkings("0"));
   }

   @Test
   void laneMarkingsUseExplicitLaneDividersAndDashGaps() {
      RoadFeature defaultMainRoad = road(RoadClass.MAIN, "primary", "asphalt", "", 0.0);
      RoadFeature fourLaneRoad = road(RoadClass.MAIN, "primary", "asphalt", "", 0.0, 4, true);
      RoadFeature disabledRoad = road(RoadClass.MAIN, "primary", "asphalt", "", 0.0, 4, false);
      RoadFeature normalTwoLaneRoad = road(RoadClass.NORMAL, "residential", "asphalt", "", 0.0, 2, true);

      assertEquals(2, RoadSurfaceStyle.effectiveLaneCount(defaultMainRoad, 6));
      assertTrue(RoadSurfaceStyle.shouldDrawLaneMarking(defaultMainRoad, 6, 0.0, 0.0));
      assertFalse(RoadSurfaceStyle.shouldDrawLaneMarking(defaultMainRoad, 6, 8.0, 0.0));
      assertTrue(RoadSurfaceStyle.shouldDrawLaneMarking(fourLaneRoad, 12, 0.0, -3.0));
      assertTrue(RoadSurfaceStyle.shouldDrawLaneMarking(fourLaneRoad, 12, 0.0, 0.0));
      assertTrue(RoadSurfaceStyle.shouldDrawLaneMarking(fourLaneRoad, 12, 0.0, 3.0));
      assertFalse(RoadSurfaceStyle.shouldDrawLaneMarking(fourLaneRoad, 12, 0.0, 1.5));
      assertFalse(RoadSurfaceStyle.shouldDrawLaneMarking(disabledRoad, 12, 0.0, 0.0));
      assertTrue(RoadSurfaceStyle.shouldDrawLaneMarking(normalTwoLaneRoad, 6, 0.0, 0.0));
   }

   private static RoadFeature road(RoadClass roadClass, String highway, String surface, String subclass, double widthMeters) {
      return road(roadClass, highway, surface, subclass, widthMeters, 0, true);
   }

   private static RoadFeature road(RoadClass roadClass, String highway, String surface, String subclass, double widthMeters, int lanes, boolean laneMarkings) {
      return new RoadFeature(
         42L,
         roadClass,
         RoadMode.NORMAL,
         0,
         highway,
         surface,
         subclass,
         widthMeters,
         lanes,
         laneMarkings,
         new double[]{0.0, 0.001},
         new double[]{0.0, 0.001}
      );
   }
}
