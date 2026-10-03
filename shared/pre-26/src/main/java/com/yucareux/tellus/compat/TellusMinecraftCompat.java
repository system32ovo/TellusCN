package com.yucareux.tellus.compat;

import com.google.gson.JsonObject;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/** Minecraft server APIs shared by 1.20.1 and 1.21.1. */
public final class TellusMinecraftCompat {
   private TellusMinecraftCompat() {
   }

   public static boolean hasGamemasterPermission(CommandSourceStack source) {
      return source.hasPermission(2);
   }

   public static void configureInitialSpawn(ServerLevel level, EarthChunkGenerator generator) {
      BlockPos spawn = generator.getSpawnPosition(level);
      level.setDefaultSpawnPos(spawn, 0.0F);
   }

   public static boolean vanillaPrecipitationIsSnow(Biome biome, BlockPos position, ServerLevel level) {
      return biome.getPrecipitationAt(position) == Precipitation.SNOW;
   }

   public static long dayTime(ServerLevel level) {
      return Math.floorMod(level.getDayTime(), 24_000L);
   }

   public static LevelStem overworldStem(MinecraftServer server) {
      Registry<LevelStem> stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
      return stems.get(LevelStem.OVERWORLD);
   }

   public static void validateDynamicHeight(
      EarthGeneratorSettings settings, EarthGeneratorSettings.HeightLimits limits
   ) {
      // The active pre-26 runtime profile was validated during bootstrap.
   }

   public static String dimensionNamespace(ResourceKey<DimensionType> key) {
      return key.location().getNamespace();
   }

   public static String dimensionPath(ResourceKey<DimensionType> key) {
      return key.location().getPath();
   }

   public static void writePackFormat(JsonObject pack) {
      pack.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
   }
}
