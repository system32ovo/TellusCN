package com.yucareux.tellus.platform;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Loader-neutral client operations used by shared screens. */
public final class TellusClientPlatform {
   private static final TellusClientPlatform.GeoTeleportTransport UNAVAILABLE = new TellusClientPlatform.GeoTeleportTransport(() -> false, (latitude, longitude) -> {
   });
   private static volatile TellusClientPlatform.GeoTeleportTransport geoTeleportTransport = UNAVAILABLE;

   private TellusClientPlatform() {
   }

   public static void configureGeoTeleport(BooleanSupplier available, TellusClientPlatform.GeoTeleportSender sender) {
      geoTeleportTransport = new TellusClientPlatform.GeoTeleportTransport(
         Objects.requireNonNull(available, "available"), Objects.requireNonNull(sender, "sender")
      );
   }

   /** Returns whether the request was accepted by the active loader transport. */
   public static boolean sendGeoTeleport(double latitude, double longitude) {
      TellusClientPlatform.GeoTeleportTransport transport = geoTeleportTransport;
      if (!transport.available().getAsBoolean()) {
         return false;
      }

      transport.sender().send(latitude, longitude);
      return true;
   }

   @FunctionalInterface
   public interface GeoTeleportSender {
      void send(double latitude, double longitude);
   }

   private record GeoTeleportTransport(BooleanSupplier available, TellusClientPlatform.GeoTeleportSender sender) {
   }
}
