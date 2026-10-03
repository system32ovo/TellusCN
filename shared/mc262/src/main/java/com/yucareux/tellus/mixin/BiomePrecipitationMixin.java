package com.yucareux.tellus.mixin;

import com.yucareux.tellus.world.realtime.TellusRealtimeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Biome.class})
public class BiomePrecipitationMixin {
   @Inject(
      method = {"getPrecipitationAt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void tellus$overridePrecipitation(BlockPos pos, int seaLevel, CallbackInfoReturnable<Precipitation> cir) {
      Precipitation override = TellusRealtimeState.precipitationOverride((Biome)(Object)this, pos, seaLevel);
      if (override != null) {
         cir.setReturnValue(override);
      }
   }
}
