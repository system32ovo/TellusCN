package com.yucareux.tellus.network;

import com.yucareux.tellus.world.realtime.TellusRealtimeState;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;

public record TellusWeatherPayload(
   boolean weatherEnabled,
   TellusRealtimeState.PrecipitationMode precipitationMode,
   boolean historicalSnowEnabled,
   int centerX,
   int centerZ,
   int spacingBlocks,
   long temperatureAgeMs,
   float[] temperatureC,
   float[] snowIndex
) {

   private static final int GRID_POINTS = 9;

   public TellusWeatherPayload(FriendlyByteBuf buffer) {
      this(
         buffer.readBoolean(),
         decodePrecipitation(buffer.readByte()),
         buffer.readBoolean(),
         buffer.readVarInt(),
         buffer.readVarInt(),
         buffer.readVarInt(),
         buffer.readLong(),
         readGrid(buffer, Float.NaN),
         readSnowIndex(buffer)
      );
   }

   public TellusWeatherPayload(
      boolean weatherEnabled,
      TellusRealtimeState.PrecipitationMode precipitationMode,
      boolean historicalSnowEnabled,
      int centerX,
      int centerZ,
      int spacingBlocks,
      long temperatureAgeMs,
      float[] temperatureC,
      float[] snowIndex
   ) {
      temperatureC = copyGrid(temperatureC, Float.NaN);
      if (snowIndex != null && snowIndex.length == GRID_POINTS) {
         snowIndex = Arrays.copyOf(snowIndex, GRID_POINTS);
      } else {
         snowIndex = new float[GRID_POINTS];
      }

      this.weatherEnabled = weatherEnabled;
      this.precipitationMode = precipitationMode;
      this.historicalSnowEnabled = historicalSnowEnabled;
      this.centerX = centerX;
      this.centerZ = centerZ;
      this.spacingBlocks = spacingBlocks;
      this.temperatureAgeMs = temperatureAgeMs;
      this.temperatureC = temperatureC;
      this.snowIndex = snowIndex;
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeBoolean(this.weatherEnabled());
      buffer.writeByte(TellusWeatherPayload.encodePrecipitation(this.precipitationMode()));
      buffer.writeBoolean(this.historicalSnowEnabled());
      buffer.writeVarInt(this.centerX());
      buffer.writeVarInt(this.centerZ());
      buffer.writeVarInt(this.spacingBlocks());
      buffer.writeLong(this.temperatureAgeMs());
      writeGrid(buffer, this.temperatureC(), Float.NaN);
      float[] snowIndex = this.snowIndex();
      if (snowIndex == null || snowIndex.length < GRID_POINTS) {
         snowIndex = new float[GRID_POINTS];
      }

      for (int i = 0; i < GRID_POINTS; i++) {
         buffer.writeFloat(snowIndex[i]);
      }
   }

   private static float[] readSnowIndex(FriendlyByteBuf buffer) {
      return readGrid(buffer, 0.0F);
   }

   private static float[] readGrid(FriendlyByteBuf buffer, float fallback) {
      float[] values = new float[GRID_POINTS];
      Arrays.fill(values, fallback);
      for (int i = 0; i < GRID_POINTS; i++) {
         values[i] = buffer.readFloat();
      }
      return values;
   }

   private static void writeGrid(FriendlyByteBuf buffer, float[] values, float fallback) {
      float[] resolved = copyGrid(values, fallback);
      for (int i = 0; i < GRID_POINTS; i++) {
         buffer.writeFloat(resolved[i]);
      }
   }

   private static float[] copyGrid(float[] values, float fallback) {
      float[] copy = new float[GRID_POINTS];
      Arrays.fill(copy, fallback);
      if (values != null) {
         System.arraycopy(values, 0, copy, 0, Math.min(values.length, GRID_POINTS));
      }
      return copy;
   }

   private static byte encodePrecipitation(TellusRealtimeState.PrecipitationMode mode) {
      return mode == null ? 0 : (byte)mode.ordinal();
   }

   private static TellusRealtimeState.PrecipitationMode decodePrecipitation(byte id) {
      TellusRealtimeState.PrecipitationMode[] values = TellusRealtimeState.PrecipitationMode.values();
      int index = id < 0 ? 0 : Math.min(id, values.length - 1);
      return values[index];
   }
}
