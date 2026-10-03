package com.yucareux.tellus.worldgen.caves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TellusVanillaNoiseCaveSamplerTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      Bootstrap.bootStrap();
   }

   @Test
   void separatesMineableOreVeinsFromTheirGeologicalHostStone() {
      assertPreserved(Blocks.COPPER_ORE, true, false);
      assertPreserved(Blocks.RAW_COPPER_BLOCK, true, false);
      assertPreserved(Blocks.DEEPSLATE_IRON_ORE, true, false);
      assertPreserved(Blocks.RAW_IRON_BLOCK, true, false);
      assertPreserved(Blocks.GRANITE, false, true);
      assertPreserved(Blocks.TUFF, false, true);
      assertNull(TellusVanillaNoiseCaveSampler.oreVeinReplacement(Blocks.GRANITE.defaultBlockState(), true, false));
      assertNull(TellusVanillaNoiseCaveSampler.oreVeinReplacement(Blocks.COPPER_ORE.defaultBlockState(), false, true));
   }

   @Test
   void rejectsOrdinaryNoiseTerrain() {
      assertNull(TellusVanillaNoiseCaveSampler.oreVeinReplacement(Blocks.STONE.defaultBlockState(), true, true));
      assertNull(TellusVanillaNoiseCaveSampler.oreVeinReplacement(Blocks.DEEPSLATE.defaultBlockState(), true, true));
      assertNull(TellusVanillaNoiseCaveSampler.oreVeinReplacement(Blocks.IRON_ORE.defaultBlockState(), true, true));
   }

   @Test
   void onlyExposesConfirmedSurfaceEntranceGaps() {
      assertEquals(80, TellusVanillaNoiseCaveSampler.surfaceReferenceY(80, 100, false));
      assertEquals(80, TellusVanillaNoiseCaveSampler.surfaceReferenceY(80, 84, true));
      assertEquals(85, TellusVanillaNoiseCaveSampler.surfaceReferenceY(80, 85, true));
   }

   private static void assertPreserved(Block block, boolean applyOres, boolean applyGeology) {
      BlockState state = block.defaultBlockState();
      assertSame(state, TellusVanillaNoiseCaveSampler.oreVeinReplacement(state, applyOres, applyGeology));
   }
}
