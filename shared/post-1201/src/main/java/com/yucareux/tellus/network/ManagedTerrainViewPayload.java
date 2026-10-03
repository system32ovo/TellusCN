package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ManagedTerrainViewPayload(int renderRadiusChunks) implements CustomPacketPayload {
   public static final Type<ManagedTerrainViewPayload> TYPE = new Type<>(Tellus.id("managed_terrain_view"));
   public static final StreamCodec<FriendlyByteBuf, ManagedTerrainViewPayload> CODEC = new StreamCodec<>() {
      @Override public ManagedTerrainViewPayload decode(FriendlyByteBuf buffer) { return new ManagedTerrainViewPayload(buffer.readVarInt()); }
      @Override public void encode(FriendlyByteBuf buffer, ManagedTerrainViewPayload value) { buffer.writeVarInt(value.renderRadiusChunks()); }
   };

   @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
