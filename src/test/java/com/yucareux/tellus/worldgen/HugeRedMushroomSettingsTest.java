package com.yucareux.tellus.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HugeRedMushroomSettingsTest {
   @Test
   void defaultsToDisabledForNewAndUnmarkedWorlds() {
      EarthGeneratorSettings decoded = requireSuccess(
         EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
      );

      assertFalse(EarthGeneratorSettings.DEFAULT.hugeRedMushrooms());
      assertFalse(decoded.hugeRedMushrooms());
   }

   @Test
   void enabledSettingRoundTripsAndSurvivesOtherSettingChanges() {
      EarthGeneratorSettings decoded = requireSuccess(
         EarthGeneratorSettings.CODEC.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString("{\"huge_red_mushrooms\":true}")
         )
      );
      JsonElement encoded = requireSuccess(
         EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
      );
      JsonObject encodedObject = encoded.getAsJsonObject();

      assertTrue(decoded.hugeRedMushrooms());
      assertTrue(encodedObject.get("huge_red_mushrooms").getAsBoolean());
      assertTrue(decoded.withCustomTrees(false).hugeRedMushrooms());
      assertFalse(decoded.withHugeRedMushrooms(false).hugeRedMushrooms());
   }

   private static <T> T requireSuccess(DataResult<T> result) {
      Optional<T> value = result.resultOrPartial(message -> {
         throw new AssertionError(message);
      });
      assertTrue(value.isPresent());
      return value.get();
   }
}
