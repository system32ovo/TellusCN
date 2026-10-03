package com.yucareux.tellus.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

public final class TellusForgeClientNetworking {
   private TellusForgeClientNetworking() {
   }

   public static boolean canSendToServer() {
      ClientPacketListener connection = Minecraft.getInstance().getConnection();
      return connection != null && TellusForgeNetworking.isRemotePresent(connection.getConnection());
   }

   public static void sendToServer(Object payload) {
      if (canSendToServer()) {
         TellusForgeNetworking.sendToServer(payload);
      }
   }
}
