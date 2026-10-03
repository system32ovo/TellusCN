package com.yucareux.tellus.world.data.osm;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParsedTileCodecBuildingTest {
   @TempDir
   Path tempDir;

   @Test
   void roundTripsBuildingMetadataV4Fields() throws IOException {
      OsmBuildingMetadata metadata = new OsmBuildingMetadata(
         "building",
         "apartments",
         "residential",
         "Sample",
         7,
         "flat",
         "concrete",
         "dark_gray",
         "brick",
         "red",
         "school",
         "hotel",
         "company",
         "mall",
         "tower",
         "castle",
         "yes",
         2,
         1
      );
      OsmBuildingFeature feature = new OsmBuildingFeature(
         OsmBuildingKind.FOOTPRINT,
         1234L,
         "building-1234",
         false,
         metadata,
         22.4,
         0.0,
         new double[][]{{0.0, 0.001, 0.001, 0.0, 0.0}},
         new double[][]{{0.0, 0.0, 0.001, 0.001, 0.0}}
      );
      OsmBuildingTile tile = new OsmBuildingTile(List.of(feature), 0.0, 0.0, 0.001, 0.001);
      Path path = this.tempDir.resolve("building.parsed");

      ParsedTileCodec.writeBuildingTile(path, tile);
      OsmBuildingTile decoded = ParsedTileCodec.readBuildingTile(path);

      assertEquals(1, decoded.features().size());
      OsmBuildingMetadata decodedMetadata = decoded.features().get(0).metadata();
      assertEquals("concrete", decodedMetadata.roofMaterial());
      assertEquals("dark_gray", decodedMetadata.roofColor());
      assertEquals("brick", decodedMetadata.wallMaterial());
      assertEquals("red", decodedMetadata.wallColor());
      assertEquals("school", decodedMetadata.amenity());
      assertEquals("hotel", decodedMetadata.tourism());
      assertEquals("company", decodedMetadata.office());
      assertEquals("mall", decodedMetadata.shop());
      assertEquals("tower", decodedMetadata.manMade());
      assertEquals("castle", decodedMetadata.historic());
      assertEquals("yes", decodedMetadata.buildingPartType());
      assertEquals(2, decodedMetadata.roofLevels());
      assertEquals(1, decodedMetadata.minLevel());
   }
}
