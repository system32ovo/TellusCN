package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainDownloadStatus;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record ManagedTerrainStatusPayload(ManagedTerrainDownloadStatus status) implements FabricPacket {
   public static final PacketType<ManagedTerrainStatusPayload> TYPE = PacketType.create(Tellus.id("managed_terrain_status"), ManagedTerrainStatusPayload::new);

   public ManagedTerrainStatusPayload(FriendlyByteBuf buffer) {
      this(readStatus(buffer));
   }

   @Override
   public void write(FriendlyByteBuf buffer) {
      writeStatus(buffer, this.status);
   }

   @Override
   public PacketType<?> getType() {
      return Objects.requireNonNull(TYPE, "TYPE");
   }

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
