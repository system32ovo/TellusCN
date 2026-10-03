package com.yucareux.tellus.world.data.osm;

import com.yucareux.tellus.worldgen.EarthProjection;
import com.yucareux.tellus.worldgen.ScanlinePolygonRasterizer;
import io.github.sebasbaumh.mapbox.vectortile.VectorTile.Tile;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellusOsmWaterGeometryTest {
   private static final List<Integer> SQUARE = List.of(9, 0, 0, 26, 8192, 0, 0, 8192, 8191, 0, 15);
   // A counter-wound island hole inside the square, with deltas continuing from (0, 4096).
   private static final List<Integer> ISLAND_HOLE = List.of(9, 2048, 6143, 26, 0, 4096, 4096, 0, 0, 4095, 15);

   @TempDir
   Path tempDir;

   @ParameterizedTest(name = "{0}, scale 1:{1}")
   @MethodSource("greekLocations")
   void keepsReportedIslandsDryAndSurroundingSeaWet(Location location, double scale) throws Exception {
      String resource = "greek-islands/" + location.name() + "-13-" + location.tileX() + "-" + location.tileY() + ".mvt";
      OsmWaterTile tile;
      try (InputStream input = getClass().getResourceAsStream(resource)) {
         assertNotNull(input, resource);
         tile = TellusOsmWaterSource.parseVectorTile(input.readAllBytes(), 13, location.tileX(), location.tileY());
      }

      assertWaterAt(tile, location.latitude(), location.longitude(), scale, location.ocean());
      Path cache = this.tempDir.resolve("water.bin");
      ParsedTileCodec.writeWaterTile(cache, tile);
      assertWaterAt(ParsedTileCodec.readWaterTile(cache), location.latitude(), location.longitude(), scale, location.ocean());
   }

   static Stream<Arguments> greekLocations() {
      return Stream.of(
         new Location("kefalonia", 4564, 3154, 38.20, 20.58, false),
         new Location("zakynthos", 4568, 3166, 37.78, 20.77, false),
         new Location("delos", 4671, 3177, 37.3923, 25.269, false),
         new Location("mainland", 4585, 3168, 37.70, 21.50, false),
         new Location("mykonos", 4672, 3175, 37.45, 25.35, false),
         new Location("ionian-sea", 4562, 3166, 37.78, 20.50, true)
      ).flatMap(location -> Stream.of(1.0, 5.0, 15.0, 30.0, 100.0).map(scale -> Arguments.of(location, scale)));
   }

   @ParameterizedTest
   @ValueSource(strings = {"sea", "ocean", "bay", "strait", "cape"})
   void excludesPhysicalLabelPolygonsFromWaterGeometry(String classTag) {
      OsmWaterTile tile = parseFeature(classTag, "physical", Tile.GeomType.POLYGON, SQUARE);

      assertTrue(tile.isEmpty());
   }

   @Test
   void preservesOceanIslandHolesUnderOverlappingSeaLabels() {
      Tile.Layer.Builder ocean = layer("ocean", "ocean", Tile.GeomType.POLYGON, SQUARE);
      ocean.setFeatures(0, ocean.getFeatures(0).toBuilder().addAllGeometry(ISLAND_HOLE));
      // Both geometries live in the same MVT layer. The named sea covers the island hole.
      ocean.addValues(value("sea")).addValues(value("physical"));
      ocean.addFeatures(Tile.Feature.newBuilder().setId(2).setType(Tile.GeomType.POLYGON)
         .addAllTags(List.of(0, 2, 1, 3)).addAllGeometry(SQUARE));
      OsmWaterTile tile = TellusOsmWaterSource.parseVectorTile(Tile.newBuilder().addLayers(ocean).build().toByteArray(), 0, 0, 0);

      assertEquals(1, tile.features().size());
      assertEquals(2, tile.features().get(0).partCount());
      assertWaterAt(tile, 0.0, 0.0, 5.0, false);
      assertWaterAt(tile, 0.0, -135.0, 5.0, true);
   }

   @ParameterizedTest
   @CsvSource({"lake,lake", "river,river", "reservoir,human_made"})
   void preservesInlandWaterPolygons(String classTag, String subtype) {
      OsmWaterTile tile = parseFeature(classTag, subtype, Tile.GeomType.POLYGON, SQUARE);

      assertEquals(1, tile.features().size());
      assertTrue(tile.features().get(0).containsLonLat(0.0, 0.0));
      assertFalse(tile.features().get(0).oceanHint());
   }

   @Test
   void preservesPhysicalWaterfallPointsAsProtectionMarkers() {
      OsmWaterTile tile = parseFeature("waterfall", "physical", Tile.GeomType.POINT, List.of(9, 4096, 4096));

      assertEquals(1, tile.features().size());
      assertTrue(tile.features().get(0).waterfallMarker());
      assertFalse(tile.features().get(0).containsLonLat(0.0, 0.0));
      assertTrue(parseFeature("waterfall", "physical", Tile.GeomType.POLYGON, SQUARE).isEmpty());
   }

   @Test
   void invalidatesParsedCacheThatLostPhysicalSubtype() throws Exception {
      Path path = this.tempDir.resolve("water-v3.bin");
      try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
         output.writeInt(1465349234);
         output.writeInt(3);
         output.writeDouble(-85.0);
         output.writeDouble(-180.0);
         output.writeDouble(85.0);
         output.writeDouble(180.0);
         output.writeInt(0);
      }

      assertThrows(java.io.IOException.class, () -> ParsedTileCodec.readWaterTile(path));
   }

   private static OsmWaterTile parseFeature(String classTag, String subtype, Tile.GeomType type, List<Integer> geometry) {
      byte[] payload = Tile.newBuilder().addLayers(layer(classTag, subtype, type, geometry)).build().toByteArray();
      return TellusOsmWaterSource.parseVectorTile(payload, 0, 0, 0);
   }

   private static Tile.Layer.Builder layer(String classTag, String subtype, Tile.GeomType type, List<Integer> geometry) {
      return Tile.Layer.newBuilder().setName("water").setVersion(2).setExtent(4096)
         .addKeys("class").addKeys("subtype").addValues(value(classTag)).addValues(value(subtype))
         .addFeatures(Tile.Feature.newBuilder().setId(1).setType(type).addAllTags(List.of(0, 0, 1, 1)).addAllGeometry(geometry));
   }

   private static Tile.Value value(String value) {
      return Tile.Value.newBuilder().setStringValue(value).build();
   }

   private static void assertWaterAt(OsmWaterTile tile, double latitude, double longitude, double scale, boolean expectedOcean) {
      int x = (int)Math.round(longitude * EarthProjection.blocksPerDegree(scale));
      int z = (int)Math.round(EarthProjection.latToBlockZ(latitude, scale));
      boolean previewWater = tile.features().stream().anyMatch(feature -> feature.containsLonLat(longitude, latitude));
      boolean lodOcean = tile.features().stream().anyMatch(feature -> feature.oceanHint() && feature.containsBlock(x, z, scale));
      boolean[] rasterWater = {false};
      for (OsmWaterFeature feature : tile.features()) {
         if (feature.lineGeometry() || feature.pointGeometry()) {
            continue;
         }
         double[][] xs = new double[feature.partCount()][];
         double[][] zs = new double[feature.partCount()][];
         for (int part = 0; part < feature.partCount(); part++) {
            xs[part] = new double[feature.pointCount(part)];
            zs[part] = new double[feature.pointCount(part)];
            for (int point = 0; point < feature.pointCount(part); point++) {
               xs[part][point] = feature.lonAt(part, point) * EarthProjection.blocksPerDegree(scale);
               zs[part][point] = EarthProjection.latToBlockZ(feature.latAt(part, point), scale);
            }
         }
         ScanlinePolygonRasterizer.fill(xs, zs, x, z, x, z, (worldX, worldZ) -> rasterWater[0] = true);
      }
      assertAll(
         () -> assertEquals(expectedOcean, previewWater, "preview water"),
         () -> assertEquals(expectedOcean, lodOcean, "LOD ocean"),
         () -> assertEquals(expectedOcean, rasterWater[0], "terrain water raster")
      );
   }

   private record Location(String name, int tileX, int tileY, double latitude, double longitude, boolean ocean) {
      @Override
      public String toString() {
         return this.name;
      }
   }
}
