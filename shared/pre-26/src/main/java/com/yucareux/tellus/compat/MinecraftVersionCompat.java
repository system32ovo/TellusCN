package com.yucareux.tellus.compat;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import com.yucareux.tellus.worldgen.RandomBiomeCatalog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;

/** Narrow compatibility seam for Minecraft APIs that changed after 1.21.1. */
public final class MinecraftVersionCompat {
   private MinecraftVersionCompat() {
   }

   public static Executor backgroundExecutor() {
      return Util.backgroundExecutor();
   }

   public static List<String> defaultRandomBiomeIds() {
      return RandomBiomeCatalog.legacyOverworldBiomeIds();
   }

   public static List<String> normalizeRandomBiomeSelection(List<String> biomeIds) {
      return RandomBiomeCatalog.normalizeLegacySelection(biomeIds);
   }

   public static DimensionType applyHeightLimits(DimensionType base, EarthGeneratorSettings.HeightLimits limits) {
      return new DimensionType(
         base.fixedTime(),
         base.hasSkyLight(),
         base.hasCeiling(),
         base.ultraWarm(),
         base.natural(),
         base.coordinateScale(),
         base.bedWorks(),
         base.respawnAnchorWorks(),
         limits.minY(),
         limits.height(),
         limits.logicalHeight(),
         base.infiniburn(),
         base.effectsLocation(),
         base.ambientLight(),
         base.monsterSettings()
      );
   }

   public static HolderSet<Block> overworldCarverReplaceables(Registry<Block> blockRegistry) {
      return blockRegistry.getOrCreateTag(BlockTags.OVERWORLD_CARVER_REPLACEABLES);
   }

   public static DensityFunctions.BeardifierOrMarker emptyBeardifier() {
      return new Beardifier(
         new ObjectArrayList<Beardifier.Rigid>().iterator(),
         new ObjectArrayList<JigsawJunction>().iterator()
      );
   }

   public static void validatePackedHorizontalLength(List<String> failures) {
      // The horizontal packed-length constant is not exposed by these versions.
   }

   public static int chunkX(ChunkPos pos) {
      return pos.x;
   }

   public static int chunkZ(ChunkPos pos) {
      return pos.z;
   }

   public static long packChunkPos(int chunkX, int chunkZ) {
      return ChunkPos.asLong(chunkX, chunkZ);
   }

   public static ServerLevel serverLevel(ServerPlayer player) {
      return player.serverLevel();
   }

   public static int maxBuildHeight(WorldGenLevel level) {
      return level.getMaxBuildHeight();
   }

   public static boolean isInsideBuildHeight(WorldGenLevel level, BlockPos position) {
      return !level.isOutsideBuildHeight(position);
   }

   public static boolean isPaleGarden(Holder<Biome> biome) {
      return false;
   }

   public static boolean isPaleGarden(ResourceKey<Biome> biomeKey) {
      return false;
   }

   public static Block paleOakLogOr(Block fallback) {
      return fallback;
   }

   public static Block paleOakLeavesOr(Block fallback) {
      return fallback;
   }
}
