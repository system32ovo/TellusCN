package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.world.data.biome.BiomeClassification;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

class RandomBiomeMixerRegistrySafetyTest {
   @Test
   void doesNotDereferenceBiomeIdsMissingFromTheCurrentMinecraftVersion() {
      AtomicBoolean getCalled = new AtomicBoolean();
      HolderGetter<Biome> lookup = recordingBiomeLookup(getCalled);
      ResourceKey<Biome> sulfurCaves = BiomeClassification.toBiomeKey("sulfur_caves");

      RandomBiomeMixer.addOceanByIdIfSelected(lookup, "sulfur_caves", Set.of(Biomes.PLAINS), new ArrayList<Holder<Biome>>());
      assertFalse(getCalled.get());

      RandomBiomeMixer.addOceanByIdIfSelected(lookup, "sulfur_caves", Set.of(sulfurCaves), new ArrayList<Holder<Biome>>());
      assertTrue(getCalled.get());
   }

   @SuppressWarnings("unchecked")
   private static HolderGetter<Biome> recordingBiomeLookup(AtomicBoolean getCalled) {
      return (HolderGetter<Biome>)Proxy.newProxyInstance(
         RandomBiomeMixerRegistrySafetyTest.class.getClassLoader(),
         new Class<?>[]{HolderGetter.class},
         (proxy, method, args) -> {
            if (method.getName().equals("get")) {
               getCalled.set(true);
               return Optional.empty();
            }
            throw new UnsupportedOperationException(method.toString());
         }
      );
   }
}
