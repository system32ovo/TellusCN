package com.yucareux.tellus.world.data.pmtiles;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import com.yucareux.tellus.world.data.source.InputStreamSafety;

/** Allocation and integer-safety checks shared by the PMTiles readers. */
public final class PmTilesSafety {
   public static final int COMPRESSION_NONE = 1;
   public static final int COMPRESSION_GZIP = 2;
   public static final int MAX_COMPRESSED_DIRECTORY_BYTES = 16 * 1024 * 1024;
   public static final int MAX_DECOMPRESSED_DIRECTORY_BYTES = 32 * 1024 * 1024;
   public static final int MAX_COMPRESSED_TILE_BYTES = 32 * 1024 * 1024;
   public static final int MAX_DECOMPRESSED_TILE_BYTES = 64 * 1024 * 1024;
   public static final int MAX_DIRECTORY_ENTRIES = 250_000;

   private PmTilesSafety() {
   }

   public static int checkedLength(long length, int maximum, String label) throws IOException {
      if (length < 0L || length > maximum) {
         throw new IOException(label + " length " + length + " exceeds the " + maximum + " byte safety limit");
      }
      return (int)length;
   }

   public static long checkedAdd(long left, long right, String label) throws IOException {
      if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) {
         throw new IOException("Invalid or overflowing " + label + " offset");
      }
      return left + right;
   }

   public static byte[] decompress(byte[] payload, int compression, int maximumBytes, String label) throws IOException {
      Objects.requireNonNull(payload, "payload");
      if (maximumBytes <= 0) {
         throw new IllegalArgumentException("Decompression limit must be positive");
      }
      return switch (compression) {
         case COMPRESSION_NONE -> {
            if (payload.length > maximumBytes) {
               throw new IOException(label + " exceeds the decompressed-size safety limit");
            }
            yield payload;
         }
         case COMPRESSION_GZIP -> gunzip(payload, maximumBytes, label);
         default -> throw new IOException("Unsupported PMTiles compression type " + compression);
      };
   }

   public static byte[] readBounded(InputStream input, int maximumBytes, String label) throws IOException {
      return InputStreamSafety.readAllBytes(input, maximumBytes, label);
   }

   public static long readVarint(InputStream input) throws IOException {
      Objects.requireNonNull(input, "input");
      long result = 0L;
      for (int byteIndex = 0; byteIndex < 10; byteIndex++) {
         int raw = input.read();
         if (raw == -1) {
            throw new EOFException("Unexpected EOF in PMTiles varint");
         }
         if (byteIndex == 9 && (raw & 0x7F) != 0) {
            throw new IOException("PMTiles varint exceeds the signed 64-bit range");
         }
         result |= (long)(raw & 0x7F) << (byteIndex * 7);
         if ((raw & 0x80) == 0) {
            return result;
         }
      }
      throw new IOException("PMTiles varint exceeds 10 bytes");
   }

   private static byte[] gunzip(byte[] input, int maximumBytes, String label) throws IOException {
      try (
         GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(input));
      ) {
         return readBounded(gzip, maximumBytes, label + " decompressed data");
      }
   }
}
