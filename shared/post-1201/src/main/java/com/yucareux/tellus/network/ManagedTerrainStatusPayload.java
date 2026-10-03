package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainDownloadStatus;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ManagedTerrainStatusPayload(ManagedTerrainDownloadStatus status) implements CustomPacketPayload {
   public static final Type<ManagedTerrainStatusPayload> TYPE = new Type<>(Tellus.id("managed_terrain_status"));
   public static final StreamCodec<FriendlyByteBuf, ManagedTerrainStatusPayload> CODEC = new StreamCodec<>() {
      @Override public ManagedTerrainStatusPayload decode(FriendlyByteBuf buffer) { return new ManagedTerrainStatusPayload(readStatus(buffer)); }
      @Override public void encode(FriendlyByteBuf buffer, ManagedTerrainStatusPayload value) { writeStatus(buffer, value.status()); }
   };

   @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

   static ManagedTerrainDownloadStatus readStatus(FriendlyByteBuf buffer) {
      ManagedTerrainDownloadStatus.Stage[] stages = ManagedTerrainDownloadStatus.Stage.values();
      int stage = Math.max(0, Math.min(stages.length - 1, buffer.readUnsignedByte()));
      return new ManagedTerrainDownloadStatus(
         stages[stage], buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
         buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(512)
      );
   }

   static void writeStatus(FriendlyByteBuf buffer, ManagedTerrainDownloadStatus status) {
      buffer.writeByte(status.stage().ordinal());
      buffer.writeVarInt(status.completedCells());
      buffer.writeVarInt(status.totalCells());
      buffer.writeVarInt(status.activeCells());
      buffer.writeVarInt(status.failedCells());
      buffer.writeVarInt(status.degradedCells());
      buffer.writeVarLong(status.bytesRead());
      buffer.writeVarLong(status.bytesExpected());
      buffer.writeVarInt(status.renderRadiusChunks());
      buffer.writeVarInt(status.safetyRingChunks());
      buffer.writeUtf(Objects.requireNonNullElse(status.detail(), ""), 512);
   }
}
