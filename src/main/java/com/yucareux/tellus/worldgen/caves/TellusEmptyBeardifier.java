package com.yucareux.tellus.worldgen.caves;

import com.yucareux.tellus.compat.MinecraftVersionCompat;
import net.minecraft.world.level.levelgen.DensityFunctions;

final class TellusEmptyBeardifier {
   private TellusEmptyBeardifier() {
   }

   static DensityFunctions.BeardifierOrMarker instance() {
      return MinecraftVersionCompat.emptyBeardifier();
   }
}
