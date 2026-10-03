package com.yucareux.tellus.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.yucareux.tellus.compat.MinecraftVersionCompat;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NeoForgeTellusRuntimePlatform implements TellusRuntimePlatform {
   @Override
   public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
      NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registration.accept(event.getDispatcher()));
   }

   @Override
   public void onServerStarted(Consumer<MinecraftServer> callback) {
      NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callback.accept(event.getServer()));
   }

   @Override
   public void onServerStopping(Consumer<MinecraftServer> callback) {
      NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> callback.accept(event.getServer()));
   }

   @Override
   public void onServerTick(Consumer<MinecraftServer> callback) {
      NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer()));
   }

   @Override
   public void onChunkUnload(BiConsumer<ServerLevel, ChunkPos> callback) {
      NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
         if (event.getLevel() instanceof ServerLevel level) {
            callback.accept(level, event.getChunk().getPos());
         }
      });
   }

   @Override
   public void onPlayerJoin(BiConsumer<MinecraftServer, ServerPlayer> callback) {
      NeoForge.EVENT_BUS.addListener((PlayerLoggedInEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = MinecraftVersionCompat.serverLevel(player).getServer();
            if (server != null) {
               callback.accept(server, player);
            }
         }
      });
   }

   @Override
   public void onPlayerDisconnect(Consumer<ServerPlayer> callback) {
      NeoForge.EVENT_BUS.addListener((PlayerLoggedOutEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer player) {
            callback.accept(player);
         }
      });
   }
}
