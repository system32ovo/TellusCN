package com.yucareux.tellus.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalHeightSettingsTest {
   @Test
   void experimentalIncreaseHeightRoundTripsAndForcesAltitudeSettings() {
      JsonElement input = JsonParser.parseString(
         """
         {
           "world_scale": 30.0,
           "terrestrial_height_scale": 12.0,
           "oceanic_height_scale": 8.0,
           "height_offset": 64,
           "min_altitude": -512,
           "max_altitude": 1024,
           "experimental_increase_height": true,
           "experimental_height_coordinate_profile": "global_mercator_land_v2"
         }
         """
      );

      EarthGeneratorSettings decoded = requireSuccess(EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, input));
      JsonObject encoded = requireSuccess(EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, decoded)).getAsJsonObject();
      EarthGeneratorSettings.HeightLimits limits = EarthGeneratorSettings.resolveHeightLimits(decoded);

      assertTrue(decoded.experimentalIncreaseHeight());
      assertEquals(1.0, decoded.terrestrialHeightScale());
      assertEquals(1.0, decoded.oceanicHeightScale());
      assertEquals(30.0, decoded.effectiveVerticalWorldScale());
      assertEquals(EarthGeneratorSettings.EXPERIMENTAL_HEIGHT_OFFSET, decoded.heightOffset());
      assertEquals(EarthGeneratorSettings.EXPERIMENTAL_HEIGHT_OFFSET, decoded.effectiveHeightOffset());
      assertEquals(EarthGeneratorSettings.AUTO_ALTITUDE, decoded.minAltitude());
      assertEquals(EarthGeneratorSettings.AUTO_ALTITUDE, decoded.maxAltitude());
      assertTrue(decoded.usesTerrainShell());
      assertFalse(decoded.suppressesUndergroundGenerationForTerrainShell());
      assertTrue(encoded.get("experimental_increase_height").getAsBoolean());
      assertFalse(encoded.has("sea_level"));
      assertEquals(HighYPackedCoordinateProfile.PROFILE_ID, encoded.get("experimental_height_coordinate_profile").getAsString());
      assertEquals(EarthGeneratorSettings.DEFAULT_UNDERGROUND_DEPTH, decoded.undergroundDepth());
      assertTrue(limits.minY() <= EarthGeneratorSettings.EXPERIMENTAL_HEIGHT_OFFSET - decoded.undergroundDepth());
      assertEquals(HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y, limits.minY());
      assertEquals(HighYPackedCoordinateProfile.TELLUS_DIMENSION_Y_SIZE, limits.height());
      assertEquals(HighYPackedCoordinateProfile.TELLUS_DIMENSION_MAX_Y, limits.minY() + limits.height() - 1);
      assertEquals(-632, limits.minY() + WaterSurfaceResolver.oceanFloorSupportBlocks());
   }

   @Test
   void rejectsUnmarkedLegacyIncreaseHeightWorld() {
      JsonElement input = JsonParser.parseString(
         """
         {
           "world_scale": 1.0,
           "experimental_increase_height": true
         }
         """
      );

      DataResult<EarthGeneratorSettings> result = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, input);
      assertTrue(result.error().isPresent());
      assertTrue(result.error().get().message().contains("missing experimental_height_coordinate_profile"));
   }

   @Test
   void rejectsLegacyPackedCoordinateProfile() {
      JsonElement input = JsonParser.parseString(
         """
         {
           "world_scale": 1.0,
           "experimental_increase_height": true,
           "experimental_height_coordinate_profile": "high_y_26x24z14y_shifted"
         }
         """
      );

      DataResult<EarthGeneratorSettings> result = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, input);
      assertTrue(result.error().isPresent());
      assertTrue(result.error().get().message().contains("incompatible coordinate profile"));
   }

   @Test
   void rejectsPreviousGlobalDenseProfile() {
      JsonElement input = JsonParser.parseString(
         """
         {
           "world_scale": 1.0,
           "experimental_increase_height": true,
           "experimental_height_coordinate_profile": "global_mercator_dense_v1"
         }
         """
      );

      DataResult<EarthGeneratorSettings> result = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, input);
      assertTrue(result.error().isPresent());
      assertTrue(result.error().get().message().contains("incompatible coordinate profile"));
   }

   private static <T> T requireSuccess(DataResult<T> result) {
      Optional<T> value = result.resultOrPartial(message -> {
         throw new AssertionError(message);
      });
      assertTrue(value.isPresent());
      return value.get();
   }
}
