package com.yucareux.tellus.integration.voxy;

import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Minecraft 1.20.1 server API bridge for shared Voxy pregeneration. */
final class VoxyPregenVersionCompat {
   private VoxyPregenVersionCompat() {
   }

   static long averageTickNanos(MinecraftServer server) {
      return (long)(server.getAverageTickTime() * 1_000_000.0F);
   }

   static void requestFullChunk(
      ServerChunkCache source, int chunkX, int chunkZ, BiConsumer<ChunkAccess, Throwable> completion
   ) {
      source.getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true).whenComplete((result, error) -> {
         ChunkAccess chunk = error == null && result != null ? result.left().orElse(null) : null;
         completion.accept(chunk, error);
      });
   }
}
