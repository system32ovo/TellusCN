package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LodBlockRangeTest {
   @Test
   void rangeCoversTheExactInclusiveDhTileExtent() {
      LodBlockRange range = LodBlockRange.forDhTile(-2, 3, 5, 64);

      assertEquals(-32, range.minX());
      assertEquals(48, range.minZ());
      assertEquals(2015, range.maxX());
      assertEquals(2095, range.maxZ());
   }

   @Test
   void rangeUsesOriginalDhResolutionRatherThanCoarseSampleWidth() {
      LodBlockRange range = LodBlockRange.forDhTile(10, -10, 7, 64);

      assertEquals(160, range.minX());
      assertEquals(-160, range.minZ());
      assertEquals(8351, range.maxX());
      assertEquals(8031, range.maxZ());
   }

   @Test
   void rangeRejectsInvalidDimensionsAndSaturatesOverflow() {
      assertThrows(IllegalArgumentException.class, () -> LodBlockRange.forDhTile(0, 0, -1, 64));
      assertThrows(IllegalArgumentException.class, () -> LodBlockRange.forDhTile(0, 0, 5, 0));
      assertEquals(Integer.MAX_VALUE, LodBlockRange.forDhTile(Integer.MAX_VALUE, 0, 30, 64).maxX());
   }
}
