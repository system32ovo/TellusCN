package com.yucareux.tellus.world.data.elevation;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import org.tukaani.xz.SingleXZInputStream;

final class TellusRasterReader {
   private static final byte[] SIGNATURE = "TELLUS/RASTER".getBytes(StandardCharsets.US_ASCII);
   private static final int FORMAT_SHORT = 2;
   private static final int CHUNK_HEADER_BYTES = 17;
   private static final int MAX_DIMENSION = 8192;
   private static final int MAX_SAMPLES = 16 * 1024 * 1024;
   private static final int MAX_COMPRESSED_CHUNK_BYTES = 64 * 1024 * 1024;
   private static final int MAX_XZ_MEMORY_KIB = 64 * 1024;

   private TellusRasterReader() {
   }

   static ShortRaster readShortRaster(InputStream input) throws IOException {
      DataInputStream dataIn = new DataInputStream(input);
      byte[] signature = new byte[SIGNATURE.length];
      dataIn.readFully(signature);
      if (!Arrays.equals(signature, SIGNATURE)) {
         throw new IOException("Invalid tellus raster signature");
      }

      int version = dataIn.readUnsignedByte();
      if (version != 0) {
         throw new IOException("Unsupported tellus raster version " + version);
      }

      int width = dataIn.readInt();
      int height = dataIn.readInt();
      int sampleCount = checkedSampleCount(width, height, "Tellus raster");
      int format = dataIn.readUnsignedByte();
      if (format != FORMAT_SHORT) {
         throw new IOException("Expected short raster format");
      }

      ShortRaster raster = ShortRaster.create(width, height);
      BitSet coveredSamples = new BitSet(sampleCount);
      long decodedSamples = 0L;
      while (true) {
         int chunkLength;
         try {
            chunkLength = dataIn.readInt();
         } catch (EOFException ignored) {
            if (decodedSamples != sampleCount) {
               throw new IOException(
                  "Tellus raster contains " + decodedSamples + " samples but declares " + sampleCount
               );
            }
            return raster;
         }

         if (chunkLength < CHUNK_HEADER_BYTES || chunkLength > MAX_COMPRESSED_CHUNK_BYTES) {
            throw new IOException("Invalid Tellus raster chunk length " + chunkLength);
         }
         byte[] chunkBytes = new byte[chunkLength];
         dataIn.readFully(chunkBytes);
         decodedSamples += readChunk(new ByteArrayInputStream(chunkBytes), raster, coveredSamples);
         if (decodedSamples > sampleCount) {
            throw new IOException("Tellus raster chunks contain more samples than the raster dimensions");
         }
      }
   }

   private static int readChunk(InputStream input, ShortRaster raster, BitSet coveredSamples) throws IOException {
      DataInputStream dataIn = new DataInputStream(input);
      int chunkX = dataIn.readInt();
      int chunkY = dataIn.readInt();
      int chunkWidth = dataIn.readInt();
      int chunkHeight = dataIn.readInt();
      TellusRasterReader.RasterFilter filter = TellusRasterReader.RasterFilter.byId(dataIn.readUnsignedByte());
      int chunkSamples = checkedSampleCount(chunkWidth, chunkHeight, "Tellus raster chunk");
      long maxX = (long)chunkX + chunkWidth;
      long maxY = (long)chunkY + chunkHeight;
      if (chunkX < 0 || chunkY < 0 || maxX > raster.width() || maxY > raster.height()) {
         throw new IOException("Tellus raster chunk exceeds the raster bounds");
      }
      for (int y = 0; y < chunkHeight; y++) {
         int rowStart = (chunkY + y) * raster.width() + chunkX;
         int overlap = coveredSamples.nextSetBit(rowStart);
         if (overlap >= 0 && overlap < rowStart + chunkWidth) {
            throw new IOException("Tellus raster chunks overlap");
         }
         coveredSamples.set(rowStart, rowStart + chunkWidth);
      }

      short[] raw = new short[chunkSamples];
      try (SingleXZInputStream xzIn = new SingleXZInputStream(input, MAX_XZ_MEMORY_KIB);
           DataInputStream xzData = new DataInputStream(xzIn)) {
         for (int i = 0; i < raw.length; i++) {
            raw[i] = xzData.readShort();
         }
         if (xzData.read() != -1) {
            throw new IOException("Tellus raster chunk contains excess decoded samples");
         }
      }
      applyFilter(filter, raw, chunkWidth, chunkHeight);
      copyChunk(raster, raw, chunkX, chunkY, chunkWidth, chunkHeight);
      return chunkSamples;
   }

   private static int checkedSampleCount(int width, int height, String description) throws IOException {
      if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
         throw new IOException(description + " dimensions " + width + "x" + height + " exceed the safety limit");
      }
      long samples = (long)width * height;
      if (samples > MAX_SAMPLES) {
         throw new IOException(description + " sample count " + samples + " exceeds the safety limit");
      }
      return (int)samples;
   }

   private static void applyFilter(TellusRasterReader.RasterFilter filter, short[] raw, int width, int height) {
      if (filter != TellusRasterReader.RasterFilter.NONE) {
         short[] decoded = new short[raw.length];

         for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
               int index = x + y * width;
               int value = raw[index];
               int left = x > 0 ? decoded[index - 1] : 0;
               int up = y > 0 ? decoded[index - width] : 0;
               int upLeft = x > 0 && y > 0 ? decoded[index - width - 1] : 0;
               decoded[index] = (short)filter.apply(value, left, up, upLeft);
            }
         }

         System.arraycopy(decoded, 0, raw, 0, raw.length);
      }
   }

   private static void copyChunk(ShortRaster raster, short[] raw, int chunkX, int chunkY, int width, int height) {
      for (int y = 0; y < height; y++) {
         int destY = chunkY + y;
         for (int x = 0; x < width; x++) {
            raster.set(chunkX + x, destY, raw[x + y * width]);
         }
      }
   }

   private enum RasterFilter {
      NONE {
         @Override
         int apply(int value, int left, int up, int upLeft) {
            return value;
         }
      },
      LEFT {
         @Override
         int apply(int value, int left, int up, int upLeft) {
            return value + left;
         }
      },
      UP {
         @Override
         int apply(int value, int left, int up, int upLeft) {
            return value + up;
         }
      },
      AVERAGE {
         @Override
         int apply(int value, int left, int up, int upLeft) {
            return value + (left + up) / 2;
         }
      },
      PAETH {
         @Override
         int apply(int value, int left, int up, int upLeft) {
            int estimate = left + up - upLeft;
            int deltaLeft = Math.abs(left - estimate);
            int deltaUp = Math.abs(up - estimate);
            int deltaUpLeft = Math.abs(upLeft - estimate);
            if (deltaLeft < deltaUp && deltaLeft < deltaUpLeft) {
               return value + left;
            } else {
               return deltaUp < deltaUpLeft ? value + up : value + upLeft;
            }
         }
      };

      abstract int apply(int var1, int var2, int var3, int var4);

      static TellusRasterReader.RasterFilter byId(int id) throws IOException {
         return switch (id) {
            case 0 -> NONE;
            case 1 -> LEFT;
            case 2 -> UP;
            case 3 -> AVERAGE;
            case 4 -> PAETH;
            default -> throw new IOException("Unsupported tellus raster filter " + id);
         };
      }
   }
}
