package com.yucareux.tellus.world.data.canopy;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.ValType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Small, pure-Java bridge to Esri's official LERC WebAssembly decoder.
 *
 * <p>The Living Atlas canopy tiles are single-band unsigned-byte LERC tiles.
 * Keeping this adapter deliberately narrow makes validation cheap and avoids a
 * platform-specific native dependency in the mod.</p>
 */
final class LercU8Decoder {
   private static final String WASM_RESOURCE = "/META-INF/resources/webjars/lerc/4.0.4/lerc-wasm.wasm";
   private static final int INFO_VALUES = 12;
   private static final int RANGE_VALUES = 3;
   private static final int INFO_BYTES = INFO_VALUES * Integer.BYTES;
   private static final int RANGE_BYTES = RANGE_VALUES * Double.BYTES;
   private static final int MAX_PIXELS = 4096 * 4096;

   private Instance instance;

   synchronized DecodedRaster decode(byte[] blob) throws IOException {
      Objects.requireNonNull(blob, "blob");
      if (blob.length == 0) {
         throw new IOException("Empty LERC payload");
      }

      Instance wasm = this.instance();
      Memory memory = wasm.memory();
      int infoAllocation = this.malloc(wasm, normalizedLength(blob.length) + normalizedLength(INFO_BYTES) + normalizedLength(RANGE_BYTES));
      try {
         int blobPointer = infoAllocation;
         int infoPointer = blobPointer + normalizedLength(blob.length);
         int rangePointer = infoPointer + normalizedLength(INFO_BYTES);
         memory.write(blobPointer, blob);
         int infoResult = resultCode(
            wasm.export("i").apply(blobPointer, blob.length, infoPointer, rangePointer, INFO_VALUES, RANGE_VALUES)
         );
         if (infoResult != 0) {
            throw new IOException("LERC blob-info error " + infoResult);
         }

         int dataType = memory.readInt(infoPointer + Integer.BYTES);
         int dimensionCount = memory.readInt(infoPointer + 2 * Integer.BYTES);
         int width = memory.readInt(infoPointer + 3 * Integer.BYTES);
         int height = memory.readInt(infoPointer + 4 * Integer.BYTES);
         int bandCount = memory.readInt(infoPointer + 5 * Integer.BYTES);
         int maskCount = memory.readInt(infoPointer + 8 * Integer.BYTES);
         int depthCount = memory.readInt(infoPointer + 9 * Integer.BYTES);
         int bandCountWithNoData = memory.readInt(infoPointer + 10 * Integer.BYTES);
         long pixelCountLong = (long)width * (long)height;
         if (dataType != 1
            || dimensionCount != 1
            || bandCount != 1
            || depthCount != 1
            || bandCountWithNoData != 0
            || width <= 0
            || height <= 0
            || pixelCountLong > MAX_PIXELS) {
            throw new IOException(
               "Unsupported LERC raster layout: type="
                  + dataType
                  + ", dimensions="
                  + dimensionCount
                  + ", size="
                  + width
                  + "x"
                  + height
                  + ", bands="
                  + bandCount
                  + ", depth="
                  + depthCount
                  + ", noDataBands="
                  + bandCountWithNoData
            );
         }

         int pixelCount = (int)pixelCountLong;
         return this.decodePixels(wasm, blob, width, height, pixelCount, maskCount);
      } finally {
         this.free(wasm, infoAllocation);
      }
   }

   private DecodedRaster decodePixels(
      Instance wasm, byte[] blob, int width, int height, int pixelCount, int maskCount
   ) throws IOException {
      int blobBytes = normalizedLength(blob.length);
      int maskBytes = normalizedLength(pixelCount);
      int dataBytes = normalizedLength(pixelCount);
      int useNoDataBytes = normalizedLength(1);
      int noDataBytes = normalizedLength(Double.BYTES);
      int allocation = this.malloc(wasm, blobBytes + maskBytes + dataBytes + useNoDataBytes + noDataBytes);
      try {
         int blobPointer = allocation;
         int maskPointer = blobPointer + blobBytes;
         int dataPointer = maskPointer + maskBytes;
         int useNoDataPointer = dataPointer + dataBytes;
         int noDataPointer = useNoDataPointer + useNoDataBytes;
         Memory memory = wasm.memory();
         memory.write(blobPointer, blob);
         int decodeResult = resultCode(
            wasm.export("l")
               .apply(
                  blobPointer,
                  blob.length,
                  maskCount,
                  maskPointer,
                  1,
                  width,
                  height,
                  1,
                  1,
                  dataPointer,
                  useNoDataPointer,
                  noDataPointer
               )
         );
         if (decodeResult != 0) {
            throw new IOException("LERC decode error " + decodeResult);
         }

         byte[] pixels = memory.readBytes(dataPointer, pixelCount);
         byte[] mask = maskCount == 0 ? null : memory.readBytes(maskPointer, pixelCount);
         return new DecodedRaster(width, height, pixels, mask);
      } finally {
         this.free(wasm, allocation);
      }
   }

   private Instance instance() throws IOException {
      if (this.instance != null) {
         return this.instance;
      }

      try (InputStream input = LercU8Decoder.class.getResourceAsStream(WASM_RESOURCE)) {
         if (input == null) {
            throw new IOException("Bundled LERC decoder is missing: " + WASM_RESOURCE);
         }

         ValType[] none = new ValType[0];
         ValType[] i32 = new ValType[]{ValType.I32};
         ImportValues imports = ImportValues.builder()
            .addFunction(
               new HostFunction(
                  "a",
                  "a",
                  FunctionType.of(new ValType[]{ValType.I32, ValType.I32, ValType.I32, ValType.I32}, none),
                  (wasm, arguments) -> {
                     throw new IllegalStateException("LERC WebAssembly assertion failed");
                  }
               ),
               new HostFunction(
                  "a",
                  "b",
                  FunctionType.of(new ValType[]{ValType.I32, ValType.I32, ValType.I32}, none),
                  (wasm, arguments) -> {
                     throw new IllegalStateException("LERC WebAssembly exception");
                  }
               ),
               new HostFunction(
                  "a",
                  "c",
                  FunctionType.of(i32, i32),
                  (wasm, arguments) -> new long[]{wasm.export("n").apply(arguments[0] + 24L)[0] + 24L}
               ),
               new HostFunction(
                  "a",
                  "d",
                  FunctionType.empty(),
                  (wasm, arguments) -> {
                     throw new IllegalStateException("LERC WebAssembly aborted");
                  }
               ),
               new HostFunction(
                  "a",
                  "e",
                  FunctionType.of(i32, i32),
                  (wasm, arguments) -> new long[]{growMemory(wasm.memory(), arguments[0]) ? 1L : 0L}
               ),
               new HostFunction(
                  "a",
                  "f",
                  FunctionType.of(new ValType[]{ValType.I32, ValType.I32, ValType.I32}, none),
                  (wasm, arguments) -> {
                     wasm.memory().copy((int)arguments[0], (int)arguments[1], (int)arguments[2]);
                     return new long[0];
                  }
               )
            )
            .build();
         this.instance = Instance.builder(Parser.parse(input)).withImportValues(imports).build();
         this.instance.export("h").apply();
         return this.instance;
      } catch (IOException error) {
         throw error;
      } catch (RuntimeException error) {
         throw new IOException("Unable to initialize the bundled LERC decoder", error);
      }
   }

   private int malloc(Instance wasm, int size) throws IOException {
      long pointer = wasm.export("n").apply(size)[0];
      if (pointer <= 0L || pointer > Integer.MAX_VALUE) {
         throw new IOException("LERC decoder could not allocate " + size + " bytes");
      }
      return (int)pointer;
   }

   private void free(Instance wasm, int pointer) {
      if (pointer > 0) {
         wasm.export("o").apply(pointer);
      }
   }

   private static boolean growMemory(Memory memory, long requestedBytes) {
      if (requestedBytes <= 0L || requestedBytes > Integer.MAX_VALUE) {
         return false;
      }
      long currentBytes = (long)memory.pages() * Memory.PAGE_SIZE;
      if (requestedBytes <= currentBytes) {
         return true;
      }
      long missing = requestedBytes - currentBytes;
      int pages = (int)((missing + Memory.PAGE_SIZE - 1L) / Memory.PAGE_SIZE);
      try {
         return memory.grow(pages) >= 0;
      } catch (RuntimeException error) {
         return false;
      }
   }

   private static int resultCode(long[] values) {
      return values.length == 0 ? 0 : (int)values[0];
   }

   private static int normalizedLength(int length) {
      return ((length >> 3) << 3) + 16;
   }

   record DecodedRaster(int width, int height, byte[] pixels, byte[] mask) {
      DecodedRaster {
         Objects.requireNonNull(pixels, "pixels");
         if (pixels.length != width * height) {
            throw new IllegalArgumentException("Unexpected decoded LERC pixel count");
         }
         if (mask != null && mask.length != pixels.length) {
            throw new IllegalArgumentException("Unexpected decoded LERC mask count");
         }
      }

      boolean valid(int index) {
         return index >= 0 && index < this.pixels.length && (this.mask == null || this.mask[index] != 0);
      }
   }
}
