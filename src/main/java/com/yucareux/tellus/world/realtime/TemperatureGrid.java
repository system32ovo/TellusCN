package com.yucareux.tellus.world.realtime;

import java.util.Arrays;
import net.minecraft.util.Mth;

public final class TemperatureGrid {
   public static final int GRID_SIZE = 3;
   public static final int GRID_POINTS = 9;
   private static final long MAX_AGE_MS = 1200000L;
   private final int centerX;
   private final int centerZ;
   private final int spacingBlocks;
   private final float[] temperatureC;
   private final long updatedAtMs;

   public TemperatureGrid(int centerX, int centerZ, int spacingBlocks, float[] temperatureC, long updatedAtMs) {
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.spacingBlocks = spacingBlocks;
      this.temperatureC = temperatureC == null ? emptySamples() : Arrays.copyOf(temperatureC, GRID_POINTS);
      this.updatedAtMs = updatedAtMs;
   }

   public static TemperatureGrid empty() {
      return new TemperatureGrid(0, 0, 0, null, 0L);
   }

   public int centerX() {
      return this.centerX;
   }

   public int centerZ() {
      return this.centerZ;
   }

   public int spacingBlocks() {
      return this.spacingBlocks;
   }

   public long updatedAtMs() {
      return this.updatedAtMs;
   }

   public boolean isEmpty() {
      return this.spacingBlocks <= 0 || this.updatedAtMs <= 0L;
   }

   public float sample(int blockX, int blockZ) {
      if (this.isEmpty() || System.currentTimeMillis() - this.updatedAtMs > MAX_AGE_MS) {
         return Float.NaN;
      }

      float localX = (float)(blockX - this.centerX) / this.spacingBlocks + 1.0F;
      float localZ = (float)(blockZ - this.centerZ) / this.spacingBlocks + 1.0F;
      if (localX < 0.0F || localX > 2.0F || localZ < 0.0F || localZ > 2.0F) {
         return Float.NaN;
      }

      int ix = Mth.clamp(Mth.floor(localX), 0, 1);
      int iz = Mth.clamp(Mth.floor(localZ), 0, 1);
      float fx = localX - ix;
      float fz = localZ - iz;
      float v00 = this.samplePoint(ix, iz);
      float v10 = this.samplePoint(ix + 1, iz);
      float v01 = this.samplePoint(ix, iz + 1);
      float v11 = this.samplePoint(ix + 1, iz + 1);
      if (!Float.isFinite(v00) || !Float.isFinite(v10) || !Float.isFinite(v01) || !Float.isFinite(v11)) {
         return Float.NaN;
      }

      float v0 = Mth.lerp(fx, v00, v10);
      float v1 = Mth.lerp(fx, v01, v11);
      return Mth.lerp(fz, v0, v1);
   }

   public float[] samples() {
      return Arrays.copyOf(this.temperatureC, GRID_POINTS);
   }

   private float samplePoint(int gridX, int gridZ) {
      int index = gridZ * GRID_SIZE + gridX;
      return index >= 0 && index < this.temperatureC.length ? this.temperatureC[index] : Float.NaN;
   }

   private static float[] emptySamples() {
      float[] values = new float[GRID_POINTS];
      Arrays.fill(values, Float.NaN);
      return values;
   }
}
