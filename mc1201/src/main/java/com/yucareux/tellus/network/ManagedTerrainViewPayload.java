package com.yucareux.tellus.network;

import com.yucareux.tellus.Tellus;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;

public record ManagedTerrainViewPayload(int renderRadiusChunks) implements FabricPacket {
   public static final PacketType<ManagedTerrainViewPayload> TYPE = PacketType.create(Tellus.id("managed_terrain_view"), ManagedTerrainViewPayload::new);

   public ManagedTerrainViewPayload(FriendlyByteBuf buffer) {
      this(buffer.readVarInt());
   }

   @Override
   public void write(FriendlyByteBuf buffer) {
      buffer.writeVarInt(this.renderRadiusChunks);
   }

   @Override
   public PacketType<?> getType() {
      return Objects.requireNonNull(TYPE, "TYPE");
   }
}
