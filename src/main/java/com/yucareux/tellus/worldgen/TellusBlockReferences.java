package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.compat.MinecraftRelease;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TellusBlockReferences {
   private static final Map<String, Block> BLOCKS_BY_PATH = new ConcurrentHashMap<>();

   private TellusBlockReferences() {
   }

   public static Block concreteBlock(String colorName) {
      return coloredBlock(colorName, "concrete");
   }

   public static BlockState concreteState(String colorName) {
      return concreteBlock(colorName).defaultBlockState();
   }

   public static Block terracottaBlock(String colorName) {
      return coloredBlock(colorName, "terracotta");
   }

   public static BlockState terracottaState(String colorName) {
      return terracottaBlock(colorName).defaultBlockState();
   }

   public static Block stainedGlassBlock(String colorName) {
      return coloredBlock(colorName, "stained_glass");
   }

   public static BlockState stainedGlassState(String colorName) {
      return stainedGlassBlock(colorName).defaultBlockState();
   }

   public static Block woolBlock(String colorName) {
      return coloredBlock(colorName, "wool");
   }

   public static BlockState woolState(String colorName) {
      return woolBlock(colorName).defaultBlockState();
   }

   public static BlockState waxedOxidizedCopperState() {
      return blockByPath("waxed_oxidized_copper").defaultBlockState();
   }

   public static BlockState lightningRodState() {
      return blockByPath("lightning_rod").defaultBlockState();
   }

   private static Block coloredBlock(String colorName, String suffix) {
      return blockByPath(colorName.toLowerCase(Locale.ROOT) + "_" + suffix);
   }

   private static Block blockByPath(String path) {
      return BLOCKS_BY_PATH.computeIfAbsent(path, TellusBlockReferences::findBlock);
   }

   private static Block findBlock(String path) {
      return MinecraftRelease.block(path);
   }
}
