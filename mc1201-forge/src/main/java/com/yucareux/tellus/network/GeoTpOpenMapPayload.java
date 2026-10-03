package com.yucareux.tellus.network;

import net.minecraft.network.FriendlyByteBuf;

public record GeoTpOpenMapPayload(double latitude, double longitude) {
   public GeoTpOpenMapPayload(FriendlyByteBuf buffer) {
      this(buffer.readDouble(), buffer.readDouble());
   }

   public GeoTpOpenMapPayload(double latitude, double longitude) {
      this.latitude = latitude;
      this.longitude = longitude;
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeDouble(this.latitude());
      buffer.writeDouble(this.longitude());
   }
}
