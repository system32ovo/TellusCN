package com.yucareux.tellus.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Separates decorative host-rock patches from mineable ore features. Vanilla
 * registers both groups under {@code ore_*} IDs, so path matching alone cannot
 * implement independent settings.
 */
public final class UndergroundFeatureClassifier {
   private UndergroundFeatureClassifier() {
   }

   public static Kind classify(PlacedFeature feature) {
      Object configuration = feature.feature().value().config();
      if (!(configuration instanceof OreConfiguration oreConfiguration)) {
         return Kind.OTHER;
      }

      boolean geologicalStone = !oreConfiguration.targetStates.isEmpty()
         && oreConfiguration.targetStates.stream().allMatch(target -> isGeologicalStone(target.state.getBlock()));
      return geologicalStone ? Kind.GEOLOGICAL_STONE : Kind.MINEABLE_ORE;
   }

   public static boolean isGeologicalStone(Block block) {
      return block == Blocks.GRANITE
         || block == Blocks.DIORITE
         || block == Blocks.ANDESITE
         || block == Blocks.TUFF;
   }

   public enum Kind {
      OTHER,
      MINEABLE_ORE,
      GEOLOGICAL_STONE
   }
}
