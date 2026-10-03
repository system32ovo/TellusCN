package com.yucareux.tellus.network;

import net.minecraft.network.FriendlyByteBuf;

public record GeoTpTeleportPayload(double latitude, double longitude) {
   public GeoTpTeleportPayload(FriendlyByteBuf buffer) {
      this(buffer.readDouble(), buffer.readDouble());
   }

   public GeoTpTeleportPayload(double latitude, double longitude) {
      this.latitude = latitude;
      this.longitude = longitude;
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeDouble(this.latitude());
      buffer.writeDouble(this.longitude());
   }
}
