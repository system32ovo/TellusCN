package com.yucareux.tellus.world.data.elevation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TellusRasterReaderTest {
   @Test
   void rejectsUnknownRasterFilter() throws IOException {
      byte[] chunk = chunkWithFilter(99);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         output.write("TELLUS/RASTER".getBytes(StandardCharsets.US_ASCII));
         output.writeByte(0);
         output.writeInt(1);
         output.writeInt(1);
         output.writeByte(2);
         output.writeInt(chunk.length);
         output.write(chunk);
      }

      assertThrows(IOException.class, () -> TellusRasterReader.readShortRaster(new ByteArrayInputStream(bytes.toByteArray())));
   }

   @Test
   void rejectsRasterDimensionsThatOverflowAnArray() throws IOException {
      byte[] bytes = rasterHeader(Integer.MAX_VALUE, 2);

      assertThrows(IOException.class, () -> TellusRasterReader.readShortRaster(new ByteArrayInputStream(bytes)));
   }

   @Test
   void rejectsNegativeChunkLengthBeforeAllocation() throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         output.write(rasterHeader(1, 1));
         output.writeInt(-1);
      }

      assertThrows(IOException.class, () -> TellusRasterReader.readShortRaster(new ByteArrayInputStream(bytes.toByteArray())));
   }

   @Test
   void rejectsAHeaderWithoutTheDeclaredSamples() throws IOException {
      byte[] bytes = rasterHeader(1, 1);

      assertThrows(IOException.class, () -> TellusRasterReader.readShortRaster(new ByteArrayInputStream(bytes)));
   }

   @Test
   void rejectsChunkDimensionsOutsideTheRaster() throws IOException {
      byte[] chunk = chunkHeader(0, 0, 2, 1, 0);
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         output.write(rasterHeader(1, 1));
         output.writeInt(chunk.length);
         output.write(chunk);
      }

      assertThrows(IOException.class, () -> TellusRasterReader.readShortRaster(new ByteArrayInputStream(bytes.toByteArray())));
   }

   private static byte[] chunkWithFilter(int filter) throws IOException {
      return chunkHeader(0, 0, 1, 1, filter);
   }

   private static byte[] rasterHeader(int width, int height) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         output.write("TELLUS/RASTER".getBytes(StandardCharsets.US_ASCII));
         output.writeByte(0);
         output.writeInt(width);
         output.writeInt(height);
         output.writeByte(2);
      }
      return bytes.toByteArray();
   }

   private static byte[] chunkHeader(int x, int y, int width, int height, int filter) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream output = new DataOutputStream(bytes)) {
         output.writeInt(x);
         output.writeInt(y);
         output.writeInt(width);
         output.writeInt(height);
         output.writeByte(filter);
      }
      return bytes.toByteArray();
   }
}
