package com.yucareux.tellus.worldgen;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.yucareux.tellus.preload.TerrainPreloadArea;
import com.yucareux.tellus.preload.TerrainPreloadSettingsOverrides;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalHeightHorizontalRangeTest {
   private static final double REPORTED_LATITUDE = 66.5380298;
   private static final double REPORTED_LONGITUDE = -65.3203097;

   @Test
   void acceptsReportedNorthernSpawnAndPreloadAreaAtOneToOne() {
      EarthGeneratorSettings oneToOne = settings(REPORTED_LATITUDE, REPORTED_LONGITUDE, 1.0, true);
      TerrainPreloadArea area = TerrainPreloadArea.centered(REPORTED_LATITUDE, REPORTED_LONGITUDE, 4092, 1.0);

      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(oneToOne));
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validatePreloadAreaOrThrow(oneToOne, area));
   }

   @Test
   void preloadSettingsKeepTheSelectedOneToOneScale() {
      EarthGeneratorSettings base = settings(REPORTED_LATITUDE, REPORTED_LONGITUDE, 30.0, true);

      EarthGeneratorSettings selected = TerrainPreloadSettingsOverrides.from(base)
         .withWorldScale(1.0)
         .apply(base, REPORTED_LATITUDE, REPORTED_LONGITUDE);

      assertEquals(1.0, selected.worldScale(), 0.0001);
   }

   @Test
   void standardHeightOneToOneOverrideDoesNotSilentlyFallBackToDefaultScale() {
      EarthGeneratorSettings standardHeight = settings(REPORTED_LATITUDE, REPORTED_LONGITUDE, 30.0, false);

      EarthGeneratorSettings selected = TerrainPreloadSettingsOverrides.from(standardHeight)
         .withWorldScale(1.0)
         .apply(standardHeight, REPORTED_LATITUDE, REPORTED_LONGITUDE);

      assertEquals(1.0, selected.worldScale(), 0.0001);
   }

   @Test
   void acceptsAllOneToOneMercatorCorners() {
      double maxLatitude = EarthProjection.MAX_MERCATOR_LATITUDE;
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(settings(maxLatitude, -180.0, 1.0, true)));
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(settings(maxLatitude, 180.0, 1.0, true)));
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(settings(-maxLatitude, -180.0, 1.0, true)));
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(settings(-maxLatitude, 180.0, 1.0, true)));
   }

   @Test
   void rejectsScaleBelowOneAtMercatorEdgeWithSafeScaleDiagnostic() {
      EarthGeneratorSettings underscaled = settings(EarthProjection.MAX_MERCATOR_LATITUDE, 180.0, 0.999, true);

      IllegalStateException error = assertThrows(
         IllegalStateException.class, () -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(underscaled)
      );

      assertTrue(error.getMessage().contains("configured spawn"));
      assertTrue(error.getMessage().contains("increase world scale to at least 1.000"));
   }

   @Test
   void rejectsPreloadAreaThatCrossesTheMercatorWorldEdge() {
      double maxLatitude = EarthProjection.MAX_MERCATOR_LATITUDE;
      EarthGeneratorSettings oneToOne = settings(maxLatitude, 0.0, 1.0, true);
      TerrainPreloadArea area = TerrainPreloadArea.centered(maxLatitude, 0.0, 2, 1.0);

      IllegalStateException error = assertThrows(
         IllegalStateException.class, () -> ExperimentalHeightSupport.validatePreloadAreaOrThrow(oneToOne, area)
      );

      assertTrue(error.getMessage().contains("preload area 2x2 chunks"));
      assertTrue(error.getMessage().contains("increase world scale"));
   }

   @Test
   void horizontalLimitsDoNotApplyWhenIncreaseHeightIsDisabled() {
      EarthGeneratorSettings standardHeight = settings(REPORTED_LATITUDE, REPORTED_LONGITUDE, 0.1, false);
      TerrainPreloadArea area = TerrainPreloadArea.centered(REPORTED_LATITUDE, REPORTED_LONGITUDE, 4092, 0.1);

      assertDoesNotThrow(() -> ExperimentalHeightSupport.validateConfiguredSpawnOrThrow(standardHeight));
      assertDoesNotThrow(() -> ExperimentalHeightSupport.validatePreloadAreaOrThrow(standardHeight, area));
   }

   private static EarthGeneratorSettings settings(
      double latitude, double longitude, double worldScale, boolean experimentalIncreaseHeight
   ) {
      JsonObject input = new JsonObject();
      input.addProperty("world_scale", worldScale);
      input.addProperty("spawn_latitude", latitude);
      input.addProperty("spawn_longitude", longitude);
      input.addProperty("experimental_increase_height", experimentalIncreaseHeight);
      if (experimentalIncreaseHeight) {
         input.addProperty("experimental_height_coordinate_profile", HighYPackedCoordinateProfile.PROFILE_ID);
      }
      return requireSuccess(EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, input));
   }

   private static <T> T requireSuccess(DataResult<T> result) {
      Optional<T> value = result.resultOrPartial(message -> {
         throw new AssertionError(message);
      });
      assertTrue(value.isPresent());
      return value.get();
   }
}
