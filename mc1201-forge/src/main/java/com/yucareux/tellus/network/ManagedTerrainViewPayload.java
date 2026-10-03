package com.yucareux.tellus.network;

import net.minecraft.network.FriendlyByteBuf;

public record ManagedTerrainViewPayload(int renderRadiusChunks) {
   public ManagedTerrainViewPayload(FriendlyByteBuf buffer) {
      this(buffer.readVarInt());
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.renderRadiusChunks);
   }
}
