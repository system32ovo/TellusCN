package com.yucareux.tellus.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class TellusNeoForgeClientNetworking {
   private TellusNeoForgeClientNetworking() {
   }

   public static void sendToServer(CustomPacketPayload payload) {
      ClientPacketDistributor.sendToServer(payload);
   }
}
