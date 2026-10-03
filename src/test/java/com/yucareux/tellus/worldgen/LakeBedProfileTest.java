package com.yucareux.tellus.worldgen;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LakeBedProfileTest {
   @Test
   void preservesShallowShelvesBeforeTransitioningToTheInterior() {
      assertEquals(1, LakeBedProfile.depth(0.0, 100, -200));
      assertEquals(1, LakeBedProfile.depth(5.0, 100, -200));
      assertEquals(3, LakeBedProfile.depth(6.0, 100, -200));
      assertEquals(4, LakeBedProfile.depth(9.0, 100, -200));
   }

   @Test
   void interiorVariationStaysBroadAndSafelyBelowTheSurface() {
      Set<Integer> depths = new HashSet<>();
      int maximumAdjacentChange = 0;
      for (int z = -256; z <= 256; z++) {
         int previous = LakeBedProfile.depth(1_000.0, -256, z);
         for (int x = -255; x <= 256; x++) {
            int depth = LakeBedProfile.depth(1_000.0, x, z);
            depths.add(depth);
            maximumAdjacentChange = Math.max(maximumAdjacentChange, Math.abs(depth - previous));
            previous = depth;
            assertTrue(depth >= LakeBedProfile.minimumInteriorDepth());
            assertTrue(depth <= LakeBedProfile.maximumInteriorDepth());
         }
      }

      assertTrue(depths.size() >= 4, "Expected several broad lake-bed levels");
      assertTrue(maximumAdjacentChange <= 1, "Adjacent columns should not contain random depth spikes");
   }

   @Test
   void basinStopsDeepeningAfterTheShoreTransition() {
      int x = 1_337;
      int z = -9_001;
      assertEquals(
         LakeBedProfile.depth(LakeBedProfile.maximumShoreInfluenceBlocks(), x, z),
         LakeBedProfile.depth(10_000.0, x, z)
      );
   }

   @Test
   void profileIsStableAcrossRepeatedAndNegativeCoordinateSamples() {
      int expected = LakeBedProfile.depth(80.0, -123_456, -654_321);
      assertEquals(expected, LakeBedProfile.depth(80.0, -123_456, -654_321));
   }

   @Test
   void sampledDepthScalesItsMinimumWithTheLodCellFootprint() {
      assertEquals(1, LakeBedProfile.minimumSampledDepth(1));
      assertEquals(2, LakeBedProfile.minimumSampledDepth(4));
      assertEquals(4, LakeBedProfile.minimumSampledDepth(8));
      assertEquals(8, LakeBedProfile.minimumSampledDepth(16));
      assertEquals(8, LakeBedProfile.minimumSampledDepth(64));

      assertTrue(LakeBedProfile.sampledDepth(0, 1, 0, 0) >= 1);
      assertTrue(LakeBedProfile.sampledDepth(0, 16, 0, 0) >= 8);
   }
}
