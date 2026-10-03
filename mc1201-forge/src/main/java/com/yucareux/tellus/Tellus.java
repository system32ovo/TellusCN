package com.yucareux.tellus;

import com.yucareux.tellus.compat.MinecraftRelease;
import com.yucareux.tellus.network.GeoTpTeleportPayload;
import com.yucareux.tellus.network.ManagedTerrainViewPayload;
import com.yucareux.tellus.network.TellusForgeNetworking;
import com.yucareux.tellus.platform.ForgeTellusRuntimePlatform;
import com.yucareux.tellus.worldgen.EarthBiomeSource;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.RegisterEvent;

/** Forge entrypoint; loader-independent server behavior lives in {@link TellusCommon}. */
@Mod(Tellus.MOD_ID)
public final class Tellus extends TellusCommon {
   public static ResourceLocation id(String path) {
      return MinecraftRelease.resourceLocation("tellus", path);
   }

   @SuppressWarnings("deprecation")
   public Tellus() {
      TellusCommon.validateRuntime();
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
      modEventBus.addListener(Tellus::registerBuiltinRegistries);
      TellusForgeNetworking.registerMessages();
      if (FMLEnvironment.dist == Dist.CLIENT) {
         TellusClient.register(modEventBus);
      }
      TellusCommon.initializeRuntime(new ForgeTellusRuntimePlatform());
   }

   public static void handleGeoTeleport(GeoTpTeleportPayload payload, NetworkEvent.Context context) {
      ServerPlayer player = context.getSender();
      if (player != null) {
         TellusCommon.handleGeoTeleport(payload, player);
      }
   }

   public static void handleManagedTerrainView(ManagedTerrainViewPayload payload, NetworkEvent.Context context) {
      ServerPlayer player = context.getSender();
      if (player != null) {
         TellusCommon.handleManagedTerrainView(payload, player);
      }
   }

   private static void registerBuiltinRegistries(RegisterEvent event) {
      event.register(Registries.BIOME_SOURCE, id("earth"), () -> EarthBiomeSource.CODEC);
      event.register(Registries.CHUNK_GENERATOR, id("earth"), () -> EarthChunkGenerator.CODEC);
   }
}
