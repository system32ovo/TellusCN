package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UndergroundFeatureClassifierTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      Bootstrap.bootStrap();
   }

   @Test
   void classifiesVanillaHostRockSeparatelyFromMineableOre() {
      assertEquals(
         UndergroundFeatureClassifier.Kind.GEOLOGICAL_STONE,
         UndergroundFeatureClassifier.classify(oreFeature(Blocks.GRANITE))
      );
      assertEquals(
         UndergroundFeatureClassifier.Kind.GEOLOGICAL_STONE,
         UndergroundFeatureClassifier.classify(oreFeature(Blocks.ANDESITE))
      );
      assertEquals(
         UndergroundFeatureClassifier.Kind.MINEABLE_ORE,
         UndergroundFeatureClassifier.classify(oreFeature(Blocks.IRON_ORE))
      );
   }

   private static PlacedFeature oreFeature(Block output) {
      OreConfiguration configuration = new OreConfiguration(
         new BlockMatchTest(Blocks.STONE), output.defaultBlockState(), 16
      );
      ConfiguredFeature<OreConfiguration, ?> configured = new ConfiguredFeature<>(Feature.ORE, configuration);
      return new PlacedFeature(Holder.direct(configured), List.of());
   }
}
