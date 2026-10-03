package com.yucareux.tellus.worldgen;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomBiomeDensityTest {
   private static final int PATCH_GRID_BLOCKS = 512;

   @Test
   void densityControlsDeterministicMonotonicPatchCoverage() {
      EarthGeneratorSettings disabled = settings(0.0, 712367L);
      EarthGeneratorSettings low = settings(0.12, 712367L);
      EarthGeneratorSettings high = settings(0.40, 712367L);
      int samples = 0;
      int lowActive = 0;
      int highActive = 0;

      for (int gridZ = -50; gridZ < 50; gridZ++) {
         for (int gridX = -50; gridX < 50; gridX++) {
            int blockX = gridX * PATCH_GRID_BLOCKS;
            int blockZ = gridZ * PATCH_GRID_BLOCKS;
            boolean lowValue = RandomBiomeMixer.isLandPatchActive(low, blockX, blockZ);
            boolean highValue = RandomBiomeMixer.isLandPatchActive(high, blockX, blockZ);

            assertFalse(RandomBiomeMixer.isLandPatchActive(disabled, blockX, blockZ));
            assertEquals(lowValue, RandomBiomeMixer.isLandPatchActive(low, blockX, blockZ));
            assertTrue(!lowValue || highValue, "Increasing density must not remove an existing patch");
            lowActive += lowValue ? 1 : 0;
            highActive += highValue ? 1 : 0;
            samples++;
         }
      }

      assertEquals(0.12, lowActive / (double)samples, 0.02);
      assertEquals(0.40, highActive / (double)samples, 0.02);
   }

   private static EarthGeneratorSettings settings(double density, long seed) {
      DataResult<EarthGeneratorSettings> result = EarthGeneratorSettings.CODEC.parse(
         JsonOps.INSTANCE,
         JsonParser.parseString(
            "{\"random_biomes\":true,\"random_biome_density\":"
               + density
               + ",\"random_biome_seed\":"
               + seed
               + ",\"random_biome_ids\":[\"desert\"]}"
         )
      );
      Optional<EarthGeneratorSettings> settings = result.resultOrPartial(message -> {
         throw new AssertionError(message);
      });
      return settings.orElseThrow(() -> new AssertionError("Codec operation returned no settings"));
   }
}
