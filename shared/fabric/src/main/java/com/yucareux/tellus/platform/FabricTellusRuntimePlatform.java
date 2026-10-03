package com.yucareux.tellus.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class FabricTellusRuntimePlatform implements TellusRuntimePlatform {
   @Override
   public void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
      CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registration.accept(dispatcher));
   }

   @Override
   public void onServerStarted(Consumer<MinecraftServer> callback) {
      ServerLifecycleEvents.SERVER_STARTED.register(callback::accept);
   }

   @Override
   public void onServerStopping(Consumer<MinecraftServer> callback) {
      ServerLifecycleEvents.SERVER_STOPPING.register(callback::accept);
   }

   @Override
   public void onServerTick(Consumer<MinecraftServer> callback) {
      ServerTickEvents.END_SERVER_TICK.register(callback::accept);
   }

   @Override
   public void onChunkUnload(BiConsumer<ServerLevel, ChunkPos> callback) {
      ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> callback.accept(level, chunk.getPos()));
   }

   @Override
   public void onPlayerJoin(BiConsumer<MinecraftServer, ServerPlayer> callback) {
      ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(server, handler.getPlayer()));
   }

   @Override
   public void onPlayerDisconnect(Consumer<ServerPlayer> callback) {
      ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> callback.accept(handler.getPlayer()));
   }
}
