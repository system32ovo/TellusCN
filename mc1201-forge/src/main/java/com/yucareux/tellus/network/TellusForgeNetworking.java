package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TellusForgeNetworking {
   private static final String PROTOCOL_VERSION = "1";
   private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      Tellus.id("main"),
      () -> PROTOCOL_VERSION,
      ignored -> true,
      ignored -> true
   );

   private TellusForgeNetworking() {
   }

   public static void registerMessages() {
      int discriminator = 0;
      CHANNEL.registerMessage(
         discriminator++,
         GeoTpTeleportPayload.class,
         GeoTpTeleportPayload::write,
         GeoTpTeleportPayload::new,
         TellusForgeNetworking::handleGeoTeleport,
         Optional.of(NetworkDirection.PLAY_TO_SERVER)
      );
      CHANNEL.registerMessage(
         discriminator++,
         ManagedTerrainViewPayload.class,
         ManagedTerrainViewPayload::write,
         ManagedTerrainViewPayload::new,
         TellusForgeNetworking::handleManagedTerrainView,
         Optional.of(NetworkDirection.PLAY_TO_SERVER)
      );
      CHANNEL.registerMessage(
         discriminator++,
         GeoTpOpenMapPayload.class,
         GeoTpOpenMapPayload::write,
         GeoTpOpenMapPayload::new,
         TellusForgeNetworking::handleOpenMap,
         Optional.of(NetworkDirection.PLAY_TO_CLIENT)
      );
      CHANNEL.registerMessage(
         discriminator++,
         TellusWeatherPayload.class,
         TellusWeatherPayload::write,
         TellusWeatherPayload::new,
         TellusForgeNetworking::handleWeather,
         Optional.of(NetworkDirection.PLAY_TO_CLIENT)
      );
      CHANNEL.registerMessage(
         discriminator,
         ManagedTerrainStatusPayload.class,
         ManagedTerrainStatusPayload::write,
         ManagedTerrainStatusPayload::new,
         TellusForgeNetworking::handleManagedTerrainStatus,
         Optional.of(NetworkDirection.PLAY_TO_CLIENT)
      );
   }

   public static void sendToPlayer(ServerPlayer player, Object payload) {
      Connection connection = player.connection.connection;
      if (CHANNEL.isRemotePresent(connection)) {
         CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
      }
   }

   public static void sendToServer(Object payload) {
      CHANNEL.sendToServer(payload);
   }

   public static boolean isRemotePresent(Connection connection) {
      return connection != null && CHANNEL.isRemotePresent(connection);
   }

   private static void handleGeoTeleport(GeoTpTeleportPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> Tellus.handleGeoTeleport(payload, context));
      context.setPacketHandled(true);
   }

   private static void handleManagedTerrainView(ManagedTerrainViewPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(() -> Tellus.handleManagedTerrainView(payload, context));
      context.setPacketHandled(true);
   }

   private static void handleOpenMap(GeoTpOpenMapPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> com.yucareux.tellus.TellusClient.handleOpenMapPayload(payload)
         )
      );
      context.setPacketHandled(true);
   }

   private static void handleWeather(TellusWeatherPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> com.yucareux.tellus.TellusClient.handleWeatherPayload(payload)
         )
      );
      context.setPacketHandled(true);
   }

   private static void handleManagedTerrainStatus(
      ManagedTerrainStatusPayload payload,
      Supplier<NetworkEvent.Context> contextSupplier
   ) {
      NetworkEvent.Context context = contextSupplier.get();
      context.enqueueWork(
         () -> DistExecutor.unsafeRunWhenOn(
            Dist.CLIENT,
            () -> () -> com.yucareux.tellus.TellusClient.handleManagedTerrainStatusPayload(payload)
         )
      );
      context.setPacketHandled(true);
   }
}
