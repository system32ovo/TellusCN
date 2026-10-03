package com.yucareux.tellus.mixin;

import com.yucareux.tellus.integration.distant_horizons.DistantHorizonsIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.level.AbstractDhLevel", remap = false)
public abstract class DistantHorizonsChunkUpdateMixin {
   @Inject(
      method = "updateChunkAsync",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private void tellus$skipExperimentalHeightChunkUpdate(@Coerce Object chunkWrapper, int updateHash, CallbackInfo ci) {
      if (DistantHorizonsIntegration.shouldSkipChunkBackedUpdates(this)) {
         ci.cancel();
      }
   }
}
