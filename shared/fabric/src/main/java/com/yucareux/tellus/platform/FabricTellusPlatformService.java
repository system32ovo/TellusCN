package com.yucareux.tellus.platform;

import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.ManagedTerrainStatusPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

public final class FabricTellusPlatformService implements TellusPlatformService {
   @Override
   public Path gameDir() {
      return FabricLoader.getInstance().getGameDir();
   }

   @Override
   public Path configDir() {
      return FabricLoader.getInstance().getConfigDir();
   }

   @Override
   public boolean isModLoaded(String modId) {
      return FabricLoader.getInstance().isModLoaded(modId);
   }

   @Override
   public void sendWeatherPayload(ServerPlayer player, TellusWeatherPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   @Override
   public void sendGeoTpOpenMapPayload(ServerPlayer player, GeoTpOpenMapPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   @Override
   public void sendManagedTerrainStatusPayload(ServerPlayer player, ManagedTerrainStatusPayload payload) {
      ServerPlayNetworking.send(player, payload);
   }

   @Override
   public void registerDistantHorizonsLifecycle(Runnable onServerStart, Runnable onServerStop, Runnable onPlayerJoin) {
      ServerLifecycleEvents.SERVER_STARTING.register(server -> onServerStart.run());
      ServerLifecycleEvents.SERVER_STOPPING.register(server -> onServerStop.run());
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin.run());
   }
}
