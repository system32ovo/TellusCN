package com.yucareux.tellus.world.data.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Applies allocation bounds before native image decoders inspect downloaded map tiles. */
public final class MapTileImageValidator {
   private static final byte[] PNG_SIGNATURE = new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

   private MapTileImageValidator() {
   }

   public static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
      Objects.requireNonNull(input, "input");
      if (maxBytes <= 0) {
         throw new IllegalArgumentException("Map tile byte limit must be positive");
      }
      return InputStreamSafety.readAllBytes(input, maxBytes, "Map tile");
   }

   public static void validatePng(byte[] data, int maxWidth, int maxHeight) throws IOException {
      Objects.requireNonNull(data, "data");
      if (maxWidth <= 0 || maxHeight <= 0) {
         throw new IllegalArgumentException("Map tile dimension limits must be positive");
      }
      if (data.length < 24) {
         throw new IOException("Map tile is not a complete PNG header");
      }
      for (int index = 0; index < PNG_SIGNATURE.length; index++) {
         if (data[index] != PNG_SIGNATURE[index]) {
            throw new IOException("Map tile is not a PNG image");
         }
      }
      if (readInt(data, 8) != 13
         || data[12] != 'I'
         || data[13] != 'H'
         || data[14] != 'D'
         || data[15] != 'R') {
         throw new IOException("Map tile PNG is missing its IHDR header");
      }

      int width = readInt(data, 16);
      int height = readInt(data, 20);
      if (width <= 0 || height <= 0 || width > maxWidth || height > maxHeight) {
         throw new IOException(
            "Map tile dimensions " + width + "x" + height + " exceed the " + maxWidth + "x" + maxHeight + " safety limit"
         );
      }
   }

   private static int readInt(byte[] data, int offset) {
      return (data[offset] & 0xFF) << 24
         | (data[offset + 1] & 0xFF) << 16
         | (data[offset + 2] & 0xFF) << 8
         | data[offset + 3] & 0xFF;
   }
}
