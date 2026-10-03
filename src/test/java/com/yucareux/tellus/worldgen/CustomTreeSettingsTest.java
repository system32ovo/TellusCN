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

class CustomTreeSettingsTest {
   @Test
   void defaultsToCustomTreesForNewAndUnmarkedWorlds() {
      EarthGeneratorSettings decoded = requireSuccess(
         EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}"))
      );

      assertTrue(EarthGeneratorSettings.DEFAULT.customTrees());
      assertTrue(decoded.customTrees());
   }

   @Test
   void minecraftTreesRoundTripAsAWorldSetting() {
      EarthGeneratorSettings decoded = requireSuccess(
         EarthGeneratorSettings.CODEC.parse(
            JsonOps.INSTANCE,
            JsonParser.parseString("{\"custom_trees\":false}")
         )
      );
      JsonElement encoded = requireSuccess(
         EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
      );
      JsonObject encodedObject = encoded.getAsJsonObject();

      assertFalse(decoded.customTrees());
      assertFalse(encodedObject.get("custom_trees").getAsBoolean());
      assertFalse(decoded.withCustomTrees(false).customTrees());
      assertTrue(decoded.withCustomTrees(true).customTrees());
   }

   private static <T> T requireSuccess(DataResult<T> result) {
      Optional<T> value = result.resultOrPartial(message -> {
         throw new AssertionError(message);
      });
      assertTrue(value.isPresent());
      return value.get();
   }
}
