package com.yucareux.tellus.mixin.client;

import com.yucareux.tellus.Tellus;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin({SectionOcclusionGraph.class})
public abstract class SectionOcclusionGraphMixin {
   @Unique
   private static final int TELLUS$MIN_TALL_WORLD_SECTION_COUNT = 256;
   @Unique
   private static boolean tellus$loggedTallWorldVerticalCull;

   @Redirect(
      method = {"getRelativeFrom"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ViewArea;getViewDistance()I"
      )
   )
   private int tellus$extendVerticalCullDistance(ViewArea viewArea) {
      int viewDistance = viewArea.getViewDistance();
      int sectionCount = viewArea.sectionCount();
      if (sectionCount <= TELLUS$MIN_TALL_WORLD_SECTION_COUNT) {
         return viewDistance;
      }

      int verticalDistance = Math.max(viewDistance, sectionCount);
      if (!tellus$loggedTallWorldVerticalCull) {
         tellus$loggedTallWorldVerticalCull = true;
         Tellus.LOGGER.info(
            "Tall-world renderer vertical culling distance extended from {} to {} sections for a {}-section level.",
            viewDistance,
            verticalDistance,
            sectionCount
         );
      }

      return verticalDistance;
   }
}
