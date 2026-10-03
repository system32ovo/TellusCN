package com.yucareux.tellus.integration.voxy;

import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Minecraft 26.2 server API bridge for shared Voxy pregeneration. */
final class VoxyPregenVersionCompat {
   private VoxyPregenVersionCompat() {
   }

   static long averageTickNanos(MinecraftServer server) {
      return server.getAverageTickTimeNanos();
   }

   static void requestFullChunk(
      ServerChunkCache source, int chunkX, int chunkZ, BiConsumer<ChunkAccess, Throwable> completion
   ) {
      source.getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true).whenComplete((result, error) -> {
         if (error != null || result == null || !result.isSuccess()) {
            completion.accept(null, error);
         } else {
            result.ifSuccess(chunk -> completion.accept(chunk, null));
         }
      });
   }
}
