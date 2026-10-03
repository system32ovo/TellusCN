package com.yucareux.tellus.worldgen.road;

import static org.junit.jupiter.api.Assertions.*;

import com.yucareux.tellus.world.data.osm.OsmStreetLightFeature;
import com.yucareux.tellus.world.data.osm.RoadClass;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.world.data.osm.RoadMode;
import com.yucareux.tellus.world.data.osm.RoadPointKind;
import com.yucareux.tellus.world.data.osm.RoadSurfaceStyle;
import com.yucareux.tellus.worldgen.EarthProjection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class StreetLightPlannerTest {
   @Test
   void localStreetsHaveEvenSpacingOnOneVergeWithArmsFacingTraffic() {
      var road = road(1, "residential", RoadMode.NORMAL, 7, -200, 8, 200, 8);
      var lamps = plan(List.of(road), List.of());
      assertTrue(lamps.size() >= 12);
      for (int i = 0; i < lamps.size(); i++) {
         var lamp = lamps.get(i);
         assertEquals(StreetLightStyle.RESIDENTIAL, lamp.style());
         assertEquals(6, lamp.height());
         assertEquals(1, lamp.arm());
         assertEquals(13, lamp.worldZ());
         assertEquals(Direction.NORTH, lamp.facing());
         if (i > 0) assertEquals(28, lamp.worldX() - lamps.get(i - 1).worldX());
      }
      assertOutsideRoads(lamps, List.of(road), 1.0);
   }

   @Test
   void collectorsStaggerWhileWideBoulevardsHaveOpposingPairs() {
      var collector = plan(List.of(road(1, "tertiary", RoadMode.NORMAL, 9, -200, 0, 200, 0)), List.of());
      assertTrue(collector.size() >= 10);
      for (int i = 1; i < collector.size(); i++) {
         assertEquals(34, collector.get(i).worldX() - collector.get(i - 1).worldX());
         assertEquals(-collector.get(i - 1).worldZ(), collector.get(i).worldZ());
      }
      var boulevard = plan(List.of(road(2, "primary", RoadMode.NORMAL, 15, -200, 0, 200, 0)), List.of());
      assertTrue(boulevard.size() >= 18);
      assertEquals(0, boulevard.size() % 2);
      for (int i = 0; i < boulevard.size(); i += 2) {
         assertEquals(boulevard.get(i).worldX(), boulevard.get(i + 1).worldX());
         assertEquals(-boulevard.get(i).worldZ(), boulevard.get(i + 1).worldZ());
         assertEquals(StreetLightStyle.BOULEVARD, boulevard.get(i).style());
         assertEquals(9, boulevard.get(i).height());
         assertEquals(3, boulevard.get(i).arm());
      }
   }

   @Test
   void intersectionsHaveClearApproachesAndPolesNeverOccupyEitherCarriageway() {
      var roads = List.of(road(1, "residential", RoadMode.NORMAL, 7, -200, 0, 200, 0),
         road(2, "secondary", RoadMode.NORMAL, 11, 0, -200, 0, 200));
      var lamps = plan(roads, List.of(point(500, 0, 0)));
      assertTrue(lamps.size() > 15);
      assertOutsideRoads(lamps, roads, 1.0);
      assertTrue(lamps.stream().noneMatch(lamp -> Math.hypot(lamp.roadX(), lamp.roadZ()) < 9 - 0.000001), lamps.toString());
      assertTrue(lamps.stream().noneMatch(StreetLightPlanner.Lamp::mapped));
   }

   @Test
   void diagonalCurvesHairpinsAndOverlappingWaysRemainOutsideEveryRoad() {
      var roads = List.of(road(1, "residential", RoadMode.NORMAL, 7, -190, -120, -80, 50, 70, 60, 180, -140),
         road(2, "secondary", RoadMode.NORMAL, 12, -170, 160, 160, -160),
         road(3, "residential", RoadMode.NORMAL, 7, -190, -120, -80, 50, 70, 60, 180, -140),
         road(4, "residential", RoadMode.NORMAL, 6, -190, 170, 160, 170, 180, 155, 160, 140, -190, 140));
      var lamps = plan(roads, List.of());
      assertTrue(lamps.size() > 20);
      assertOutsideRoads(lamps, roads, 1.0);
      assertNoClusters(lamps);
   }

   @Test
   void mappedLocationsArePreferredAndBadCoordinatesAreMovedOffTheAsphalt() {
      var road = road(1, "residential", RoadMode.NORMAL, 7, -200, 0, 200, 0);
      var lamps = plan(List.of(road), List.of(point(10, 16, -5), point(11, 90, 0), point(12, 91, 0), point(13, 92, 0)));
      assertTrue(lamps.stream().anyMatch(lamp -> lamp.mapped() && lamp.worldX() == 16 && lamp.worldZ() == -5));
      assertEquals(2, lamps.stream().filter(StreetLightPlanner.Lamp::mapped).count());
      assertOutsideRoads(lamps, List.of(road), 1.0);
      assertNoClusters(lamps);
   }

   @Test
   void chunksAndInputOrderingCannotMoveDuplicateOrDropPolesAtSeams() {
      var roads = List.of(road(1, "residential", RoadMode.NORMAL, 7, -200, 8, 200, 8),
         road(2, "primary", RoadMode.NORMAL, 14, -180, -160, 170, 170));
      var points = List.of(point(10, 15.9, 8), point(11, 16.1, 8), point(12, -16, 8));
      var expected = plan(roads, points);
      List<StreetLightPlanner.Lamp> chunked = new ArrayList<>();
      for (int z = -256; z <= 255; z += 16) {
         for (int x = -256; x <= 255; x += 16) {
            var reversed = new ArrayList<>(roads);
            Collections.reverse(reversed);
            chunked.addAll(StreetLightPlanner.plan(reversed, points, 1.0, 6, 4, 2, x, z, x + 15, z + 15));
         }
      }
      chunked.sort(Comparator.comparingInt(StreetLightPlanner.Lamp::worldX).thenComparingInt(StreetLightPlanner.Lamp::worldZ));
      assertEquals(expected, chunked);
      assertEquals(chunked.size(), new HashSet<>(chunked).size());
      assertNoClusters(chunked);
   }

   @Test
   void reversedWaysAndRotatedRoundaboutsKeepTheirStations() {
      var forward = road(1, "residential", RoadMode.NORMAL, 7, -170, -80, -40, 30, 180, 70);
      var backward = road(1, "residential", RoadMode.NORMAL, 7, 180, 70, -40, 30, -170, -80);
      assertEquals(plan(List.of(forward), List.of()), plan(List.of(backward), List.of()));
      var ring = road(2, "tertiary", RoadMode.NORMAL, 7, -100, -100, 100, -100, 100, 100, -100, 100, -100, -100);
      var reversedRing = road(2, "tertiary", RoadMode.NORMAL, 7, 100, 100, 100, -100, -100, -100, -100, 100, 100, 100);
      assertEquals(plan(List.of(ring), List.of()), plan(List.of(reversedRing), List.of()));
   }

   @Test
   void ruralHighwaysPathsBridgesAndTunnelsDoNotGetBlanketStreetLighting() {
      for (String tag : List.of("motorway", "trunk", "path", "track")) {
         assertTrue(plan(List.of(road(1, tag, RoadMode.NORMAL, 8, -200, 0, 200, 0)), List.of()).isEmpty(), tag);
      }
      for (RoadMode mode : List.of(RoadMode.BRIDGE, RoadMode.TUNNEL)) {
         assertTrue(plan(List.of(road(1, "residential", mode, 8, -200, 0, 200, 0)), List.of(point(1, 0, 0))).isEmpty());
      }
      var highway = plan(List.of(road(1, "motorway", RoadMode.NORMAL, 15, -200, 0, 200, 0)), List.of(point(1, 0, 9)));
      assertEquals(1, highway.size());
      assertEquals(StreetLightStyle.HIGHWAY, highway.get(0).style());
      assertEquals(11, highway.get(0).height());
      var pedestrian = plan(List.of(road(1, "pedestrian", RoadMode.NORMAL, 4, -200, 0, 200, 0)), List.of());
      assertFalse(pedestrian.isEmpty());
      assertTrue(pedestrian.stream().allMatch(lamp -> lamp.style() == StreetLightStyle.PATH && lamp.arm() == 0));
      var signal = new OsmStreetLightFeature(1, 0, 0, RoadPointKind.TRAFFIC_SIGNAL);
      assertTrue(plan(List.of(road(1, "motorway", RoadMode.NORMAL, 15, -200, 0, 200, 0)), List.of(signal)).isEmpty());
   }

   @Test
   void compressedWorldsRemainSparseAndNeverPutPolesOnTheRoad() {
      var roads = List.of(road(1, "residential", RoadMode.NORMAL, 7, -200, 0, 200, 0));
      for (double scale : List.of(1.0, 2.0, 4.0, 8.0)) {
         var lamps = StreetLightPlanner.plan(roads, List.of(), scale, 6, 4, 2, -256, -256, 255, 255);
         assertFalse(lamps.isEmpty());
         assertOutsideRoads(lamps, roads, scale);
         assertTrue(lamps.stream().allMatch(lamp -> lamp.height() >= 3 && lamp.spacing() >= 12));
         assertTrue(lamps.stream().allMatch(lamp -> lamp.arm() == 0 || lamp.height() > 3));
      }
      assertTrue(StreetLightPlanner.plan(roads, List.of(), 30.0, 6, 4, 2, -256, -256, 255, 255).isEmpty());
   }

   private static void assertNoClusters(List<StreetLightPlanner.Lamp> lamps) {
      for (int i = 0; i < lamps.size(); i++) for (int j = i + 1; j < lamps.size(); j++) {
         var a = lamps.get(i);
         var b = lamps.get(j);
         assertTrue(Math.hypot(a.worldX() - b.worldX(), a.worldZ() - b.worldZ()) >= 6, a + " / " + b);
      }
   }

   static void assertOutsideRoads(List<StreetLightPlanner.Lamp> lamps, List<RoadFeature> roads, double scale) {
      for (var lamp : lamps) for (var road : roads) {
         int width = RoadSurfaceStyle.effectiveRoadWidth(road, road.roadClass().baseWidth(), scale);
         double radius = Math.max(0.5, (width - 1) * 0.5);
         for (int i = 1; i < road.pointCount(); i++) {
            double x1 = road.lonAt(i - 1) * EarthProjection.blocksPerDegree(scale);
            double z1 = EarthProjection.latToBlockZ(road.latAt(i - 1), scale);
            double x2 = road.lonAt(i) * EarthProjection.blocksPerDegree(scale);
            double z2 = EarthProjection.latToBlockZ(road.latAt(i), scale);
            double distance = java.awt.geom.Line2D.ptSegDist(x1, z1, x2, z2, lamp.worldX(), lamp.worldZ());
            assertTrue(distance >= radius + 0.8, lamp + " intersects road " + road.wayId());
         }
      }
   }

   static List<StreetLightPlanner.Lamp> plan(List<RoadFeature> roads, List<OsmStreetLightFeature> points) {
      return StreetLightPlanner.plan(roads, points, 1.0, 6, 4, 2, -256, -256, 255, 255);
   }

   static OsmStreetLightFeature point(long id, double x, double z) {
      return new OsmStreetLightFeature(id, EarthProjection.blockXToLongitude(x, 1.0), EarthProjection.blockZToLat(z, 1.0));
   }

   static RoadFeature road(long id, String tag, RoadMode mode, double width, double... coordinates) {
      double[] longitudes = new double[coordinates.length / 2];
      double[] latitudes = new double[coordinates.length / 2];
      for (int i = 0; i < longitudes.length; i++) {
         longitudes[i] = EarthProjection.blockXToLongitude(coordinates[2 * i], 1.0);
         latitudes[i] = EarthProjection.blockZToLat(coordinates[2 * i + 1], 1.0);
      }
      RoadClass roadClass = switch (tag) {
         case "motorway", "trunk", "primary", "secondary", "tertiary" -> RoadClass.MAIN;
         case "path", "track" -> RoadClass.DIRT;
         default -> RoadClass.NORMAL;
      };
      return new RoadFeature(id, roadClass, mode, 0, tag, "asphalt", "", width, longitudes, latitudes);
   }
}
