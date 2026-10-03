package com.yucareux.tellus.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

public final class ForgeTellusRuntimePlatform implements TellusRuntimePlatform {
   @Override
   public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
      MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registration.accept(event.getDispatcher()));
   }

   @Override
   public void onServerStarted(Consumer<MinecraftServer> callback) {
      MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callback.accept(event.getServer()));
   }

   @Override
   public void onServerStopping(Consumer<MinecraftServer> callback) {
      MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> callback.accept(event.getServer()));
   }

   @Override
   public void onServerTick(Consumer<MinecraftServer> callback) {
      MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
         if (event.phase == TickEvent.Phase.END) {
            callback.accept(event.getServer());
         }
      });
   }

   @Override
   public void onChunkUnload(BiConsumer<ServerLevel, ChunkPos> callback) {
      MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
         if (event.getLevel() instanceof ServerLevel level) {
            callback.accept(level, event.getChunk().getPos());
         }
      });
   }

   @Override
   public void onPlayerJoin(BiConsumer<MinecraftServer, ServerPlayer> callback) {
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.serverLevel().getServer();
            if (server != null) {
               callback.accept(server, player);
            }
         }
      });
   }

   @Override
   public void onPlayerDisconnect(Consumer<ServerPlayer> callback) {
      MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
         if (event.getEntity() instanceof ServerPlayer player) {
            callback.accept(player);
         }
      });
   }
}
