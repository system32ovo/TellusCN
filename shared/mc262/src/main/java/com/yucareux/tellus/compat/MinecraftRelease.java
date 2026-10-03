package com.yucareux.tellus.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;

public final class MinecraftRelease {
   public static final String VERSION = "26.2";

   private MinecraftRelease() {
   }

   public static Identifier resourceLocation(String namespace, String path) {
      return Identifier.fromNamespaceAndPath(namespace, path);
   }

   public static ResourceKey<Biome> biomeKey(String biomeId) {
      Identifier id = biomeId.contains(":") ? Identifier.tryParse(biomeId) : Identifier.fromNamespaceAndPath("minecraft", biomeId);
      if (id == null) {
         id = Identifier.fromNamespaceAndPath("minecraft", "plains");
      }
      return ResourceKey.create(Registries.BIOME, id);
   }

   public static Block block(String path) {
      Identifier id = resourceLocation("minecraft", path);
      return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> new IllegalStateException("Missing Minecraft block " + id));
   }
}
