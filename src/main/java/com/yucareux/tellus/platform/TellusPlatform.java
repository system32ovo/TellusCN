package com.yucareux.tellus.platform;

import com.yucareux.tellus.network.GeoTpOpenMapPayload;
import com.yucareux.tellus.network.ManagedTerrainStatusPayload;
import com.yucareux.tellus.network.TellusWeatherPayload;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ServiceLoader;
import net.minecraft.server.level.ServerPlayer;

public final class TellusPlatform {
   private static final TellusPlatformService SERVICE = loadService();

   private TellusPlatform() {
   }

   public static Path gameDir() {
      String override = System.getProperty("tellus.gameDir");
      if (override != null && !override.isBlank()) {
         return Path.of(override).toAbsolutePath().normalize();
      }
      return SERVICE.gameDir();
   }

   public static Path configDir() {
      String override = System.getProperty("tellus.configDir");
      if (override != null && !override.isBlank()) {
         return Path.of(override).toAbsolutePath().normalize();
      }
      return SERVICE.configDir();
   }

   public static boolean isModLoaded(String modId) {
      return SERVICE.isModLoaded(Objects.requireNonNull(modId, "modId"));
   }

   public static void sendWeatherPayload(ServerPlayer player, TellusWeatherPayload payload) {
      SERVICE.sendWeatherPayload(Objects.requireNonNull(player, "player"), Objects.requireNonNull(payload, "payload"));
   }

   public static void sendGeoTpOpenMapPayload(ServerPlayer player, GeoTpOpenMapPayload payload) {
      SERVICE.sendGeoTpOpenMapPayload(Objects.requireNonNull(player, "player"), Objects.requireNonNull(payload, "payload"));
   }

   public static void sendManagedTerrainStatusPayload(ServerPlayer player, ManagedTerrainStatusPayload payload) {
      SERVICE.sendManagedTerrainStatusPayload(Objects.requireNonNull(player, "player"), Objects.requireNonNull(payload, "payload"));
   }

   public static void registerDistantHorizonsLifecycle(Runnable onServerStart, Runnable onServerStop, Runnable onPlayerJoin) {
      SERVICE.registerDistantHorizonsLifecycle(
         Objects.requireNonNull(onServerStart, "onServerStart"),
         Objects.requireNonNull(onServerStop, "onServerStop"),
         Objects.requireNonNull(onPlayerJoin, "onPlayerJoin")
      );
   }

   private static TellusPlatformService loadService() {
      return ServiceLoader.load(TellusPlatformService.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No Tellus platform service was registered"));
   }
}
