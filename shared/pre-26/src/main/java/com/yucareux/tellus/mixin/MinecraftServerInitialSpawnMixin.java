package com.yucareux.tellus.mixin;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerInitialSpawnMixin {
   @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
   private static void tellus$setEarthInitialSpawn(
      ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci
   ) {
      ChunkGenerator generator = level.getChunkSource().getGenerator();
      if (!(generator instanceof EarthChunkGenerator earthGenerator)) {
         return;
      }

      BlockPos spawn = earthGenerator.getInitialSpawnPosition(level);
      levelData.setSpawn(spawn, 0.0F);
      if (generateBonusChest) {
         level.registryAccess()
            .registry(Registries.CONFIGURED_FEATURE)
            .flatMap(registry -> registry.getHolder(MiscOverworldFeatures.BONUS_CHEST))
            .ifPresent(feature -> feature.value().place(level, generator, level.getRandom(), spawn));
      }

      Tellus.LOGGER.info("Using Tellus initial spawn at {} and skipping vanilla spawn search.", spawn);
      ci.cancel();
   }
}
