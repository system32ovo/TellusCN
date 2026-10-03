package com.yucareux.tellus.mixin;

import com.yucareux.tellus.world.realtime.TellusRealtimeState;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Biome.class})
public class BiomeFreezeMixin {
   @Inject(
      method = {"shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void tellus$gateWaterFreeze(LevelReader level, BlockPos pos, boolean mustBeAtEdge, CallbackInfoReturnable<Boolean> cir) {
      if (level instanceof ServerLevel serverLevel && serverLevel.getChunkSource().getGenerator() instanceof EarthChunkGenerator) {
         boolean ocean = level.getBiome(pos).is(BiomeTags.IS_OCEAN);
         if (!TellusRealtimeState.canWaterFreeze(pos, ocean)) {
            cir.setReturnValue(false);
         }
      }
   }
}
