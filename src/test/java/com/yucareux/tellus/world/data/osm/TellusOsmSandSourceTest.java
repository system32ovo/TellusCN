package com.yucareux.tellus.world.data.osm;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellusOsmSandSourceTest {
   @Test
   void recognizesCurrentOvertureSandClassifications() {
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("subtype", "sand")));
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("class", "sand")));
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("class", "beach")));
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("class", "dune")));
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("surface", "sand")));
      assertTrue(TellusOsmSandSource.isSandLikeFeature(Map.of("surface", "recreation_sand")));
   }

   @Test
   void rejectsNonSandLand() {
      assertFalse(TellusOsmSandSource.isSandLikeFeature(Map.of("subtype", "grass", "class", "park", "surface", "grass")));
      assertFalse(TellusOsmSandSource.isSandLikeFeature(Map.of()));
   }
}
