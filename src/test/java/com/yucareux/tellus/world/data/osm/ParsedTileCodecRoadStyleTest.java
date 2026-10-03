package com.yucareux.tellus.world.data.osm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsedTileCodecRoadStyleTest {
   @TempDir
   Path tempDir;

   @Test
   void roundTripsRoadSurfaceMetadata() throws IOException {
      RoadFeature feature = new RoadFeature(
         123L,
         RoadClass.MAIN,
         RoadMode.BRIDGE,
         2,
         "primary",
         "asphalt",
         "link",
         12.5,
         4,
         false,
         new double[]{-99.1, -99.09},
         new double[]{19.4, 19.41}
      );
      RoadAreaFeature area = new RoadAreaFeature(
         456L,
         RoadClass.NORMAL,
         "pedestrian",
         "paving_stones",
         "plaza",
         new double[][]{{-99.100, -99.095, -99.095, -99.100, -99.100}},
         new double[][]{{19.400, 19.400, 19.405, 19.405, 19.400}}
      );
      OverpassRoadTile tile = new OverpassRoadTile(List.of(feature), List.of(area), 19.4, -99.1, 19.41, -99.09);
      Path path = this.tempDir.resolve("road.parsed");

      ParsedTileCodec.writeRoadTile(path, tile);
      OverpassRoadTile decoded = ParsedTileCodec.readRoadTile(path);

      assertEquals(1, decoded.features().size());
      RoadFeature decodedFeature = decoded.features().get(0);
      assertEquals("asphalt", decodedFeature.roadSurface());
      assertEquals("link", decodedFeature.subclass());
      assertEquals(12.5, decodedFeature.widthMeters(), 0.0001);
      assertEquals(4, decodedFeature.lanes());
      assertEquals(false, decodedFeature.laneMarkings());
      assertEquals(RoadMode.BRIDGE, decodedFeature.mode());
      assertEquals(2, decodedFeature.bridgeLevel());
      assertEquals(1, decoded.areaFeatures().size());
      RoadAreaFeature decodedArea = decoded.areaFeatures().get(0);
      assertEquals(456L, decodedArea.featureId());
      assertEquals("pedestrian", decodedArea.highwayTag());
      assertEquals("paving_stones", decodedArea.roadSurface());
      assertEquals("plaza", decodedArea.subclass());
      assertEquals(1, decoded.areaFeaturesInBounds(19.399, -99.101, 19.406, -99.094).size());
   }

   @Test
   void roundTripsStreetLightTile() throws IOException {
      OsmStreetLightFeature first = new OsmStreetLightFeature(10L, -99.1, 19.4);
      OsmStreetLightFeature second = new OsmStreetLightFeature(11L, -99.09, 19.41, RoadPointKind.TRAFFIC_SIGNAL);
      OsmStreetLightFeature third = new OsmStreetLightFeature(12L, -99.095, 19.405, RoadPointKind.BENCH);
      OsmStreetLightTile tile = new OsmStreetLightTile(List.of(first, second, third), 19.39, -99.11, 19.42, -99.08);
      Path path = this.tempDir.resolve("street_lights.parsed");

      ParsedTileCodec.writeStreetLightTile(path, tile);
      OsmStreetLightTile decoded = ParsedTileCodec.readStreetLightTile(path);

      assertEquals(3, decoded.features().size());
      assertEquals(10L, decoded.features().get(0).featureId());
      assertEquals(-99.09, decoded.features().get(1).longitude(), 0.0001);
      assertEquals(19.41, decoded.features().get(1).latitude(), 0.0001);
      assertEquals(RoadPointKind.STREET_LIGHT, decoded.features().get(0).kind());
      assertEquals(RoadPointKind.TRAFFIC_SIGNAL, decoded.features().get(1).kind());
      assertEquals(RoadPointKind.BENCH, decoded.features().get(2).kind());
      assertEquals(1, decoded.featuresOfKind(RoadPointKind.TRAFFIC_SIGNAL).size());
      assertEquals(1, decoded.featuresOfKind(RoadPointKind.BENCH).size());
      assertEquals(19.39, decoded.tileSouth(), 0.0001);
      assertEquals(-99.08, decoded.tileEast(), 0.0001);
   }

   @Test
   void mapsArnisStyleInfrastructureClasses() {
      assertEquals(RoadPointKind.BENCH, RoadPointKind.fromInfrastructureClass("bench"));
      assertEquals(RoadPointKind.WASTE_BASKET, RoadPointKind.fromInfrastructureClass("waste_basket"));
      assertEquals(RoadPointKind.RECYCLING, RoadPointKind.fromInfrastructureClass("recycling"));
      assertEquals(RoadPointKind.BICYCLE_PARKING, RoadPointKind.fromInfrastructureClass("bicycle_parking"));
      assertEquals(RoadPointKind.FOUNTAIN, RoadPointKind.fromInfrastructureClass("drinking_water"));
      assertEquals(RoadPointKind.BOLLARD, RoadPointKind.fromInfrastructureClass("bollard"));
   }
}
