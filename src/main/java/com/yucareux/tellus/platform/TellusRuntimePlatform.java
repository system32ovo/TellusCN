package com.yucareux.tellus.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Loader lifecycle hooks consumed by the shared Tellus server bootstrap. */
public interface TellusRuntimePlatform {
   void registerCommands(Consumer<CommandDispatcher<CommandSourceStack>> registration);

   void onServerStarted(Consumer<MinecraftServer> callback);

   void onServerStopping(Consumer<MinecraftServer> callback);

   void onServerTick(Consumer<MinecraftServer> callback);

   void onChunkUnload(BiConsumer<ServerLevel, ChunkPos> callback);

   void onPlayerJoin(BiConsumer<MinecraftServer, ServerPlayer> callback);

   void onPlayerDisconnect(Consumer<ServerPlayer> callback);
}
