package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MapTileImageValidatorTest {
   @Test
   void acceptsBoundedPngHeader() throws Exception {
      byte[] png = pngHeader(256, 256);

      assertArrayEquals(png, MapTileImageValidator.readBounded(new ByteArrayInputStream(png), png.length));
      MapTileImageValidator.validatePng(png, 512, 512);
   }

   @Test
   void rejectsOversizedPayloadsAndImageDimensions() {
      assertThrows(
         IOException.class,
         () -> MapTileImageValidator.readBounded(new ByteArrayInputStream(new byte[17]), 16)
      );
      assertThrows(IOException.class, () -> MapTileImageValidator.validatePng(pngHeader(16_384, 256), 512, 512));
      assertThrows(IOException.class, () -> MapTileImageValidator.validatePng(new byte[24], 512, 512));
   }

   private static byte[] pngHeader(int width, int height) {
      byte[] data = new byte[24];
      byte[] signature = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
      System.arraycopy(signature, 0, data, 0, signature.length);
      writeInt(data, 8, 13);
      data[12] = 'I';
      data[13] = 'H';
      data[14] = 'D';
      data[15] = 'R';
      writeInt(data, 16, width);
      writeInt(data, 20, height);
      return data;
   }

   private static void writeInt(byte[] data, int offset, int value) {
      data[offset] = (byte)(value >>> 24);
      data[offset + 1] = (byte)(value >>> 16);
      data[offset + 2] = (byte)(value >>> 8);
      data[offset + 3] = (byte)value;
   }
}
