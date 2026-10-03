package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class OrePlacementDensityPolicyTest {
   @Test
   void neverReducesShallowShellsBelowOnePlacement() {
      assertEquals(1, OrePlacementDensityPolicy.placementSamples(64, 127, RandomSource.create(1L)));
      assertEquals(1, OrePlacementDensityPolicy.placementSamples(127, 127, RandomSource.create(1L)));
   }

   @Test
   void scalesDeepShellsByTheirVirtualDepthRatio() {
      assertEquals(2, OrePlacementDensityPolicy.placementSamples(254, 127, RandomSource.create(1L)));
      assertEquals(4, OrePlacementDensityPolicy.placementSamples(508, 127, RandomSource.create(1L)));
   }
}
