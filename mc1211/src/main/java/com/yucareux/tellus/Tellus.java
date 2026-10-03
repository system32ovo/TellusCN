package com.yucareux.tellus;

import com.yucareux.tellus.compat.MinecraftRelease;
import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.GeoTpTeleportPayload;
import com.yucareux.tellus.network.ManagedTerrainStatusPayload;
import com.yucareux.tellus.network.ManagedTerrainViewPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import com.yucareux.tellus.platform.FabricTellusRuntimePlatform;
import com.yucareux.tellus.worldgen.EarthBiomeSource;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import java.util.Objects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Fabric entrypoint; loader-independent server behavior lives in {@link TellusCommon}. */
public final class Tellus extends TellusCommon implements ModInitializer {
   public static ResourceLocation id(String path) {
      return MinecraftRelease.resourceLocation("tellus", path);
   }

   @Override
   public void onInitialize() {
      TellusCommon.validateRuntime();
      Registry.register(BuiltInRegistries.BIOME_SOURCE, id("earth"), EarthBiomeSource.CODEC);
      Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("earth"), EarthChunkGenerator.CODEC);
      PayloadTypeRegistry.playC2S().register(GeoTpTeleportPayload.TYPE, Objects.requireNonNull(GeoTpTeleportPayload.CODEC.cast(), "geoTpTeleportCodec"));
      PayloadTypeRegistry.playC2S().register(ManagedTerrainViewPayload.TYPE, Objects.requireNonNull(ManagedTerrainViewPayload.CODEC.cast(), "managedTerrainViewCodec"));
      PayloadTypeRegistry.playS2C().register(GeoTpOpenMapPayload.TYPE, Objects.requireNonNull(GeoTpOpenMapPayload.CODEC.cast(), "geoTpOpenMapCodec"));
      PayloadTypeRegistry.playS2C().register(TellusWeatherPayload.TYPE, Objects.requireNonNull(TellusWeatherPayload.CODEC.cast(), "tellusWeatherCodec"));
      PayloadTypeRegistry.playS2C().register(ManagedTerrainStatusPayload.TYPE, Objects.requireNonNull(ManagedTerrainStatusPayload.CODEC.cast(), "managedTerrainStatusCodec"));
      ServerPlayNetworking.registerGlobalReceiver(
         GeoTpTeleportPayload.TYPE, (payload, context) -> TellusCommon.handleGeoTeleport(payload, context.player())
      );
      ServerPlayNetworking.registerGlobalReceiver(
         ManagedTerrainViewPayload.TYPE, (payload, context) -> TellusCommon.handleManagedTerrainView(payload, context.player())
      );
      TellusCommon.initializeRuntime(new FabricTellusRuntimePlatform());
   }
}
