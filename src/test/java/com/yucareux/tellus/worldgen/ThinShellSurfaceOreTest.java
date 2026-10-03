package com.yucareux.tellus.worldgen;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThinShellSurfaceOreTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void exposedOreSelectionIsDeterministic() {
      OreSample sample = findOreSample(Blocks.STONE.defaultBlockState(), 96);

      BlockState first = ThinShellSurfaceOre.resolve(
         Blocks.STONE.defaultBlockState(), true, false, false, sample.x(), 96, sample.z()
      );
      BlockState second = ThinShellSurfaceOre.resolve(
         Blocks.STONE.defaultBlockState(), true, false, false, sample.x(), 96, sample.z()
      );

      assertEquals(first, second);
      assertNotEquals(Blocks.STONE.defaultBlockState(), first);
   }

   @Test
   void keepsOriginalBlockWhenOrePassIsDisabledOrSurfaceIsIneligible() {
      BlockState stone = Blocks.STONE.defaultBlockState();
      OreSample sample = findOreSample(stone, 96);

      assertEquals(stone, ThinShellSurfaceOre.resolve(stone, false, false, false, sample.x(), 96, sample.z()));
      assertEquals(stone, ThinShellSurfaceOre.resolve(stone, true, true, false, sample.x(), 96, sample.z()));
      assertEquals(stone, ThinShellSurfaceOre.resolve(stone, true, false, true, sample.x(), 96, sample.z()));
      assertEquals(
         Blocks.GRASS_BLOCK.defaultBlockState(),
         ThinShellSurfaceOre.resolve(Blocks.GRASS_BLOCK.defaultBlockState(), true, false, false, sample.x(), 96, sample.z())
      );
      assertEquals(
         Blocks.SAND.defaultBlockState(),
         ThinShellSurfaceOre.resolve(Blocks.SAND.defaultBlockState(), true, false, false, sample.x(), 96, sample.z())
      );
      assertEquals(
         Blocks.GRAVEL.defaultBlockState(),
         ThinShellSurfaceOre.resolve(Blocks.GRAVEL.defaultBlockState(), true, false, false, sample.x(), 96, sample.z())
      );
   }

   @Test
   void deepslateHostUsesOnlyDeepslateOreVariants() {
      BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
      OreSample sample = findOreSample(deepslate, 48);
      BlockState ore = ThinShellSurfaceOre.resolve(deepslate, true, false, false, sample.x(), 48, sample.z());

      assertTrue(
         ore.is(Blocks.DEEPSLATE_COAL_ORE)
            || ore.is(Blocks.DEEPSLATE_COPPER_ORE)
            || ore.is(Blocks.DEEPSLATE_IRON_ORE)
            || ore.is(Blocks.DEEPSLATE_GOLD_ORE)
            || ore.is(Blocks.DEEPSLATE_EMERALD_ORE)
      );
   }

   private static OreSample findOreSample(BlockState host, int surfaceY) {
      for (int x = -256; x <= 256; x++) {
         for (int z = -256; z <= 256; z++) {
            BlockState result = ThinShellSurfaceOre.resolve(host, true, false, false, x, surfaceY, z);
            if (!result.equals(host)) {
               return new OreSample(x, z);
            }
         }
      }

      throw new AssertionError("Expected to find a deterministic thin-shell ore sample");
   }

   private record OreSample(int x, int z) {
   }
}
