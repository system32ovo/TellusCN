package com.yucareux.tellus.world.data.pmtiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PmTilesSafetyTest {
   @Test
   void readsValidVarintsAndRejectsOverflow() throws Exception {
      assertEquals(300L, PmTilesSafety.readVarint(new ByteArrayInputStream(new byte[]{(byte)0xAC, 0x02})));
      assertThrows(
         IOException.class,
         () -> PmTilesSafety.readVarint(
            new ByteArrayInputStream(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
               (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, 0x01})
         )
      );
   }

   @Test
   void boundsGzipExpansionAndOffsetArithmetic() throws Exception {
      ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
         gzip.write(new byte[1024]);
      }

      assertThrows(
         IOException.class,
         () -> PmTilesSafety.decompress(compressed.toByteArray(), PmTilesSafety.COMPRESSION_GZIP, 128, "test payload")
      );
      assertThrows(IOException.class, () -> PmTilesSafety.checkedAdd(Long.MAX_VALUE, 1L, "test"));
      assertThrows(IOException.class, () -> PmTilesSafety.checkedLength(129L, 128, "test"));
      assertThrows(
         IOException.class,
         () -> PmTilesSafety.readBounded(new ByteArrayInputStream(new byte[129]), 128, "test stream")
      );
   }
}
