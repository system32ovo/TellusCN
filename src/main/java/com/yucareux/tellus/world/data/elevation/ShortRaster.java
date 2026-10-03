package com.yucareux.tellus.world.data.elevation;

import java.util.Arrays;

final class ShortRaster {
   private final int width;
   private final int height;
   private final short[] data;

   private ShortRaster(int width, int height, short[] data) {
      this.width = width;
      this.height = height;
      this.data = data;
   }

   static ShortRaster create(int width, int height) {
      return new ShortRaster(width, height, new short[checkedSampleCount(width, height)]);
   }

   static ShortRaster wrap(int width, int height, short[] data) {
      if (data.length != checkedSampleCount(width, height)) {
         throw new IllegalArgumentException("Invalid raster buffer");
      } else {
         return new ShortRaster(width, height, data);
      }
   }

   private static int checkedSampleCount(int width, int height) {
      if (width <= 0 || height <= 0) {
         throw new IllegalArgumentException("Raster dimensions must be positive");
      }
      try {
         return Math.multiplyExact(width, height);
      } catch (ArithmeticException error) {
         throw new IllegalArgumentException("Raster dimensions are too large", error);
      }
   }

   int width() {
      return this.width;
   }

   int height() {
      return this.height;
   }

   short get(int x, int y) {
      return this.data[x + y * this.width];
   }

   void set(int x, int y, short value) {
      this.data[x + y * this.width] = value;
   }

   void fill(short value) {
      Arrays.fill(this.data, value);
   }
}
