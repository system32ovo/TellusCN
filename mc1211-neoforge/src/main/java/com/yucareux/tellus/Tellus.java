package com.yucareux.tellus;

import com.yucareux.tellus.compat.MinecraftRelease;
import com.yucareux.tellus.network.GeoTpTeleportPayload;
import com.yucareux.tellus.network.ManagedTerrainViewPayload;
import com.yucareux.tellus.network.TellusNeoForgeNetworking;
import com.yucareux.tellus.platform.NeoForgeTellusRuntimePlatform;
import com.yucareux.tellus.worldgen.EarthBiomeSource;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

/** NeoForge entrypoint; loader-independent server behavior lives in {@link TellusCommon}. */
@Mod(Tellus.MOD_ID)
public final class Tellus extends TellusCommon {
   public static ResourceLocation id(String path) {
      return MinecraftRelease.resourceLocation("tellus", path);
   }

   public Tellus(IEventBus modEventBus, Dist dist) {
      TellusCommon.validateRuntime();
      modEventBus.addListener(Tellus::registerBuiltinRegistries);
      modEventBus.addListener(TellusNeoForgeNetworking::registerPayloadHandlers);
      if (dist == Dist.CLIENT) {
         TellusClient.register(modEventBus);
      }
      TellusCommon.initializeRuntime(new NeoForgeTellusRuntimePlatform());
   }

   public static void handleGeoTeleport(GeoTpTeleportPayload payload, IPayloadContext context) {
      if (context.player() instanceof ServerPlayer player) {
         TellusCommon.handleGeoTeleport(payload, player);
      }
   }

   public static void handleManagedTerrainView(ManagedTerrainViewPayload payload, IPayloadContext context) {
      if (context.player() instanceof ServerPlayer player) {
         TellusCommon.handleManagedTerrainView(payload, player);
      }
   }

   private static void registerBuiltinRegistries(RegisterEvent event) {
      event.register(Registries.BIOME_SOURCE, id("earth"), () -> EarthBiomeSource.CODEC);
      event.register(Registries.CHUNK_GENERATOR, id("earth"), () -> EarthChunkGenerator.CODEC);
   }
}
