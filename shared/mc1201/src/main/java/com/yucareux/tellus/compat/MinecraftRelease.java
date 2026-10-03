package com.yucareux.tellus.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.biome.Biome;

public final class MinecraftRelease {
   public static final String VERSION = "1.20.1";

   private MinecraftRelease() {
   }

   public static ResourceLocation resourceLocation(String namespace, String path) {
      ResourceLocation id = ResourceLocation.tryBuild(namespace, path);
      if (id == null) {
         throw new IllegalArgumentException("Invalid resource location " + namespace + ":" + path);
      }
      return id;
   }

   public static ResourceKey<Biome> biomeKey(String biomeId) {
      ResourceLocation id = biomeId.contains(":") ? ResourceLocation.tryParse(biomeId) : ResourceLocation.tryBuild("minecraft", biomeId);
      if (id == null) {
         id = resourceLocation("minecraft", "plains");
      }
      return ResourceKey.create(Registries.BIOME, id);
   }

   public static Block block(String path) {
      ResourceLocation id = resourceLocation("minecraft", path);
      return BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(() -> new IllegalStateException("Missing Minecraft block " + id));
   }
}
