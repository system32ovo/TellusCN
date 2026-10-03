package com.yucareux.tellus.preload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import org.junit.jupiter.api.Test;

class TerrainPreloadSettingsOverridesTest {
   @Test
   void appliesOreAndGeologyIndependently() {
      TerrainPreloadSettingsOverrides overrides = TerrainPreloadSettingsOverrides.from(EarthGeneratorSettings.DEFAULT)
         .withOreDistribution(false)
         .withGeologicalStonePatches(true);

      EarthGeneratorSettings applied = overrides.apply(EarthGeneratorSettings.DEFAULT);

      assertFalse(applied.oreDistribution());
      assertTrue(applied.geologicalStonePatches());
   }
}
