package com.yucareux.tellus.worldgen;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class ThinShellSurfaceOre {
   private static final long JAVA_RANDOM_MULTIPLIER = 25214903917L;
   private static final long JAVA_RANDOM_ADDEND = 11L;
   private static final long JAVA_RANDOM_MASK = 281474976710655L;

   private ThinShellSurfaceOre() {
   }

   static BlockState resolve(BlockState topBlock, boolean oreDistribution, boolean underwater, boolean snowCovered, int worldX, int surfaceY, int worldZ) {
      if (!oreDistribution || underwater || snowCovered || !isHost(topBlock)) {
         return topBlock;
      }

      long seed = seedFromCoords(worldX, surfaceY, worldZ) ^ 596748711013247745L;
      if (seededRandomInt(seed, 96) != 0) {
         return topBlock;
      }

      boolean deepslate = topBlock.is(Blocks.DEEPSLATE);
      int roll = seededRandomInt(seed ^ -7046029254386353131L, 100);
      if (roll < 32) {
         return deepslate ? Blocks.DEEPSLATE_COAL_ORE.defaultBlockState() : Blocks.COAL_ORE.defaultBlockState();
      } else if (roll < 60) {
         return deepslate ? Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState() : Blocks.COPPER_ORE.defaultBlockState();
      } else if (roll < 88) {
         return deepslate ? Blocks.DEEPSLATE_IRON_ORE.defaultBlockState() : Blocks.IRON_ORE.defaultBlockState();
      } else if (roll < 96 || surfaceY < 120) {
         return deepslate ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState() : Blocks.GOLD_ORE.defaultBlockState();
      } else {
         return deepslate ? Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState() : Blocks.EMERALD_ORE.defaultBlockState();
      }
   }

   private static boolean isHost(BlockState state) {
      return state.is(Blocks.STONE)
         || state.is(Blocks.DEEPSLATE)
         || state.is(Blocks.ANDESITE)
         || state.is(Blocks.DIORITE)
         || state.is(Blocks.GRANITE)
         || state.is(Blocks.TUFF)
         || state.is(Blocks.CALCITE)
         || state.is(Blocks.BASALT)
         || state.is(Blocks.BLACKSTONE)
         || state.is(Blocks.DRIPSTONE_BLOCK)
         || state.is(Blocks.COBBLESTONE);
   }

   private static long seedFromCoords(int x, int y, int z) {
      long seed = x * 3129871L ^ z * 116129781L ^ y;
      seed = seed * seed * 42317861L + seed * 11L;
      return seed >> 16;
   }

   private static int seededRandomInt(long seed, int bound) {
      if (bound <= 0) {
         throw new IllegalArgumentException("bound must be positive");
      }

      long state = (seed ^ JAVA_RANDOM_MULTIPLIER) & JAVA_RANDOM_MASK;
      if ((bound & -bound) == bound) {
         state = nextJavaRandomState(state);
         return (int)((long)bound * (long)(int)(state >>> 17) >> 31);
      }

      int bits;
      int value;
      do {
         state = nextJavaRandomState(state);
         bits = (int)(state >>> 17);
         value = bits % bound;
      } while (bits - value + (bound - 1) < 0);

      return value;
   }

   private static long nextJavaRandomState(long state) {
      return (state * JAVA_RANDOM_MULTIPLIER + JAVA_RANDOM_ADDEND) & JAVA_RANDOM_MASK;
   }
}
