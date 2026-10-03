package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighYPackedCoordinateProfileTest {
   @Test
   void roundTripsRepresentativeTellusCoordinatesAcrossUnsignedLongRange() {
      assertPackedRoundTrip(0, -128, 0);
      assertPackedRoundTrip(-7_271_424, 8_848, -10_025_677);
      assertPackedRoundTrip(9_672_000, 8_848, 3_248_000);
      assertPackedRoundTrip(15_827_000, -128, 1_271_000);
      assertPackedRoundTrip(-17_330_081, 10_447, -2_144_470);
      assertPackedRoundTrip(16_483_591, -641, -1_942_616);
      assertPackedRoundTrip(16_483_591, HighYPackedCoordinateProfile.Y_MIN, -1_942_616);
      assertPackedRoundTrip(HighYPackedCoordinateProfile.X_MAX, HighYPackedCoordinateProfile.Y_MAX, HighYPackedCoordinateProfile.Z_MAX);
      assertPackedRoundTrip(HighYPackedCoordinateProfile.X_MIN, HighYPackedCoordinateProfile.Y_MIN, HighYPackedCoordinateProfile.Z_MIN);
   }

   @Test
   void exposesFullOneToOneMercatorAndTrueHeightBoundaries() {
      assertEquals(-20_037_509, HighYPackedCoordinateProfile.X_MIN);
      assertEquals(20_037_508, HighYPackedCoordinateProfile.X_MAX);
      assertEquals(-20_037_509, HighYPackedCoordinateProfile.Z_MIN);
      assertEquals(20_037_508, HighYPackedCoordinateProfile.Z_MAX);
      assertEquals(40_075_018L, HighYPackedCoordinateProfile.X_SIZE);
      assertEquals(-832, HighYPackedCoordinateProfile.Y_MIN);
      assertEquals(10_639, HighYPackedCoordinateProfile.Y_MAX);
      assertEquals(11_472, HighYPackedCoordinateProfile.Y_SIZE);
      assertEquals(-640, HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y);
      assertEquals(10_447, HighYPackedCoordinateProfile.TELLUS_DIMENSION_MAX_Y);
      assertEquals(11_088, HighYPackedCoordinateProfile.TELLUS_DIMENSION_Y_SIZE);
      assertEquals(-4_080, HighYPackedCoordinateProfile.DIMENSION_MIN_Y);
      assertEquals(12_271, HighYPackedCoordinateProfile.DIMENSION_MAX_Y);
      assertEquals(16_352, HighYPackedCoordinateProfile.DIMENSION_Y_SIZE);
      assertTrue(HighYPackedCoordinateProfile.containsHorizontal(-7_271_424, -10_025_677));
      assertTrue(HighYPackedCoordinateProfile.containsHorizontal(HighYPackedCoordinateProfile.X_MIN, HighYPackedCoordinateProfile.Z_MAX));
      assertFalse(HighYPackedCoordinateProfile.containsHorizontal(HighYPackedCoordinateProfile.X_MIN - 1, 0));
      assertFalse(HighYPackedCoordinateProfile.containsHorizontal(0, HighYPackedCoordinateProfile.Z_MAX + 1));
   }

   @Test
   void denseDomainFitsInUnsignedLong() {
      long last = HighYPackedCoordinateProfile.pack(
         HighYPackedCoordinateProfile.X_MAX,
         HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y - 1,
         HighYPackedCoordinateProfile.Z_MAX
      );

      assertTrue(last < 0L);
      assertEquals(
         HighYPackedCoordinateProfile.HORIZONTAL_POSITION_COUNT * HighYPackedCoordinateProfile.Y_SIZE - 1L,
         last
      );
      assertTrue(HighYPackedCoordinateProfile.isCanonicalPackedValue(last));
      assertFalse(HighYPackedCoordinateProfile.isCanonicalPackedValue(-1L));
   }

   @Test
   void preservesLowFourYBitsUsedByBlockPosFlatIndex() {
      int x = -7_271_424;
      int z = -10_025_677;
      for (
         int y = HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y + 1;
         y <= HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y + 31;
         y++
      ) {
         long packed = HighYPackedCoordinateProfile.pack(x, y, z);
         long flat = packed & -16L;
         assertEquals(x, HighYPackedCoordinateProfile.unpackX(flat));
         assertEquals(Math.floorDiv(y, 16) * 16, HighYPackedCoordinateProfile.unpackY(flat));
         assertEquals(z, HighYPackedCoordinateProfile.unpackZ(flat));
      }

      for (int y = HighYPackedCoordinateProfile.Y_MIN + 1; y <= HighYPackedCoordinateProfile.Y_MIN + 31; y++) {
         long packed = HighYPackedCoordinateProfile.pack(x, y, z);
         long flat = packed & -16L;
         assertEquals(x, HighYPackedCoordinateProfile.unpackX(flat));
         assertEquals(Math.floorDiv(y, 16) * 16, HighYPackedCoordinateProfile.unpackY(flat));
         assertEquals(z, HighYPackedCoordinateProfile.unpackZ(flat));
      }
   }

   @Test
   void usesContiguousPackedValuesForPlayableDimension() {
      int x = 16_483_591;
      int z = -1_942_616;
      int[] ys = {-640, -384, 0, 10_020, 10_447};
      long column = ((long)z - HighYPackedCoordinateProfile.Z_MIN) * HighYPackedCoordinateProfile.X_SIZE
         + ((long)x - HighYPackedCoordinateProfile.X_MIN);

      for (int y : ys) {
         long expectedPacked = column * HighYPackedCoordinateProfile.Y_SIZE
            + (long)y - HighYPackedCoordinateProfile.TELLUS_DIMENSION_MIN_Y;
         assertEquals(expectedPacked, HighYPackedCoordinateProfile.pack(x, y, z));
      }
   }

   @Test
   void rejectsCoordinatesOutsideCanonicalDomainInsteadOfAliasing() {
      assertThrows(
         IllegalArgumentException.class,
         () -> HighYPackedCoordinateProfile.pack(HighYPackedCoordinateProfile.X_MAX + 1, 0, 0)
      );
      assertThrows(
         IllegalArgumentException.class,
         () -> HighYPackedCoordinateProfile.pack(0, HighYPackedCoordinateProfile.Y_MAX + 1, 0)
      );
      assertThrows(
         IllegalArgumentException.class,
         () -> HighYPackedCoordinateProfile.pack(0, HighYPackedCoordinateProfile.Y_MIN - 1, 0)
      );
   }

   private static void assertPackedRoundTrip(int x, int y, int z) {
      long packed = HighYPackedCoordinateProfile.pack(x, y, z);
      assertTrue(HighYPackedCoordinateProfile.isCanonicalPackedValue(packed));
      assertEquals(x, HighYPackedCoordinateProfile.unpackX(packed));
      assertEquals(y, HighYPackedCoordinateProfile.unpackY(packed));
      assertEquals(z, HighYPackedCoordinateProfile.unpackZ(packed));
   }
}
