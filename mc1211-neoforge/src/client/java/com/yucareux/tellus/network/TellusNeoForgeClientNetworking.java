package com.yucareux.tellus.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TellusNeoForgeClientNetworking {
   private TellusNeoForgeClientNetworking() {
   }

   public static void sendToServer(CustomPacketPayload payload) {
      PacketDistributor.sendToServer(payload);
   }
}
