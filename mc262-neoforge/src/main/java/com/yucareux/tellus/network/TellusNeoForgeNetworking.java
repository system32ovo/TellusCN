package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class TellusNeoForgeNetworking {
   private TellusNeoForgeNetworking() {
   }

   public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("1");
      registrar.playToServer(GeoTpTeleportPayload.TYPE, GeoTpTeleportPayload.CODEC, Tellus::handleGeoTeleport);
      registrar.playToServer(ManagedTerrainViewPayload.TYPE, ManagedTerrainViewPayload.CODEC, Tellus::handleManagedTerrainView);
      registrar.playToClient(GeoTpOpenMapPayload.TYPE, GeoTpOpenMapPayload.CODEC);
      registrar.playToClient(TellusWeatherPayload.TYPE, TellusWeatherPayload.CODEC);
      registrar.playToClient(ManagedTerrainStatusPayload.TYPE, ManagedTerrainStatusPayload.CODEC);
   }

   public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
      PacketDistributor.sendToPlayer(player, payload);
   }
}
