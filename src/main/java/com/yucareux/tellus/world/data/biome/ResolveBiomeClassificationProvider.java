package com.yucareux.tellus.world.data.biome;

import com.yucareux.tellus.world.data.resolve.ResolveEcoregion;
import com.yucareux.tellus.world.data.resolve.TellusResolveSource;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Activation point for the bundled RESOLVE classifier. */
public final class ResolveBiomeClassificationProvider implements BiomeClassificationProvider {
   private final TellusResolveSource resolveSource = TellusResolveSource.shared();

   @Override
   public ResourceKey<Biome> findBiomeKey(
      int esaCode,
      String koppenCode,
      int blockX,
      int blockZ,
      double worldScale
   ) {
      ResolveEcoregion ecoregion = this.resolveSource.sampleEcoregion(blockX, blockZ, worldScale);
      return ResolveBiomeClassification.findBiomeKey(esaCode, koppenCode, ecoregion);
   }

   @Override
   public Set<ResourceKey<Biome>> allBiomeKeys() {
      return ResolveBiomeClassification.allBiomeKeys();
   }
}
