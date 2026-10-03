package com.yucareux.tellus.mixin.client;

import java.util.function.LongConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.RenderDistanceTracker", remap = false)
public abstract class VoxyRenderDistanceTrackerMixin {
   private static final int VOXY_TOP_LEVEL_Y_BITS = 8;
   private static final int VOXY_TOP_LEVEL_LOD = 4;
   @Unique
   private static final int TELLUS$MIN_SAFE_TOP_LEVEL_Y = -(1 << (VOXY_TOP_LEVEL_Y_BITS - VOXY_TOP_LEVEL_LOD - 1));
   @Unique
   private static final int TELLUS$MAX_SAFE_TOP_LEVEL_Y = (1 << (VOXY_TOP_LEVEL_Y_BITS - VOXY_TOP_LEVEL_LOD - 1)) - 1;
   @Shadow
   @Final
   private LongConsumer addTopLevelNode;
   @Shadow
   @Final
   private LongConsumer removeTopLevelNode;
   @Shadow
   @Final
   private int minSec;
   @Shadow
   @Final
   private int maxSec;

   @Inject(
      method = {"add"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void tellus$clampTopLevelYOnAdd(int x, int z, CallbackInfo ci) {
      this.tellus$emitTopLevelNodes(this.addTopLevelNode, x, z);
      ci.cancel();
   }

   @Inject(
      method = {"rem"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void tellus$clampTopLevelYOnRemove(int x, int z, CallbackInfo ci) {
      this.tellus$emitTopLevelNodes(this.removeTopLevelNode, x, z);
      ci.cancel();
   }

   @Unique
   private void tellus$emitTopLevelNodes(LongConsumer consumer, int x, int z) {
      int minY = Math.max(this.minSec, TELLUS$MIN_SAFE_TOP_LEVEL_Y);
      int maxY = Math.min(this.maxSec, TELLUS$MAX_SAFE_TOP_LEVEL_Y);

      for (int y = minY; y <= maxY; y++) {
         consumer.accept(tellus$packWorldSectionId(VOXY_TOP_LEVEL_LOD, x, y, z));
      }
   }

   @Unique
   private static long tellus$packWorldSectionId(int level, int x, int y, int z) {
      return ((long)level << 60)
         | ((long)(y & 0xFF) << 52)
         | ((long)(z & ((1 << 24) - 1)) << 28)
         | ((long)(x & ((1 << 24) - 1)) << 4);
   }
}
