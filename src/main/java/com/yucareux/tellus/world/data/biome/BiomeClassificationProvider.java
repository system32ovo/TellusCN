package com.yucareux.tellus.world.data.biome;

import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Optional version-specific biome-classification input.
 *
 * <p>Providers return {@code null} when their data is unavailable or when no
 * regional override applies, allowing the existing ESA + Köppen classifier to
 * remain the fallback.</p>
 */
public interface BiomeClassificationProvider {
   ResourceKey<Biome> findBiomeKey(int esaCode, String koppenCode, int blockX, int blockZ, double worldScale);

   default Set<ResourceKey<Biome>> allBiomeKeys() {
      return Set.of();
   }
}
