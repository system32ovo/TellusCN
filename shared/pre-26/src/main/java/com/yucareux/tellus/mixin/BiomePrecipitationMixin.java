package com.yucareux.tellus.mixin;

import com.yucareux.tellus.world.realtime.TellusRealtimeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Biome.class})
public class BiomePrecipitationMixin {
   @Inject(
      method = {"getPrecipitationAt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void tellus$overridePrecipitation(BlockPos pos, CallbackInfoReturnable<Precipitation> cir) {
      Biome biome = (Biome)(Object)this;
      Precipitation override = TellusRealtimeState.precipitationOverride(biome.hasPrecipitation(), pos);
      if (override != null) {
         cir.setReturnValue(override);
      }
   }

   @Inject(method = {"shouldSnow"}, at = {@At("HEAD")}, cancellable = true)
   private void tellus$preventSnowWhenTemperatureIsWarm(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      Biome biome = (Biome)(Object)this;
      if (Boolean.FALSE.equals(TellusRealtimeState.snowTemperatureOverride(biome.hasPrecipitation(), pos))) {
         cir.setReturnValue(false);
      }
   }

   @Redirect(
      method = {"shouldSnow"},
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;warmEnoughToRain(Lnet/minecraft/core/BlockPos;)Z")
   )
   private boolean tellus$useRealTemperatureForSnow(Biome biome, BlockPos pos) {
      Boolean snow = TellusRealtimeState.snowTemperatureOverride(biome.hasPrecipitation(), pos);
      return snow == null ? biome.warmEnoughToRain(pos) : !snow;
   }
}
