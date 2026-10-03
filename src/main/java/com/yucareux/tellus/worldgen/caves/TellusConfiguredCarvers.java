package com.yucareux.tellus.worldgen.caves;

import com.yucareux.tellus.compat.MinecraftVersionCompat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarverDebugSettings;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration.CanyonShapeConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;

final class TellusConfiguredCarvers {
   private static final float CAVE_PROBABILITY = 0.15F;
   private static final float CAVE_EXTRA_PROBABILITY = 0.07F;
   private static final float CANYON_PROBABILITY = 0.01F;
   private final HolderSet<Block> replaceables;
   private final int tellusMinY;
   private final int tellusMaxY;
   private final int undergroundDepth;
   private final ConcurrentMap<Integer, List<ConfiguredWorldCarver<?>>> carversBySurface = new ConcurrentHashMap<>();

   private TellusConfiguredCarvers(HolderSet<Block> replaceables, int tellusMinY, int tellusHeight, int undergroundDepth) {
      this.replaceables = replaceables;
      this.tellusMinY = tellusMinY;
      this.tellusMaxY = tellusMinY + Math.max(1, tellusHeight) - 1;
      this.undergroundDepth = Math.max(0, undergroundDepth);
   }

   static TellusConfiguredCarvers create(Registry<Block> blockRegistry, int tellusMinY, int tellusHeight, int undergroundDepth) {
      HolderSet<Block> replaceables = MinecraftVersionCompat.overworldCarverReplaceables(blockRegistry);
      return new TellusConfiguredCarvers(replaceables, tellusMinY, tellusHeight, undergroundDepth);
   }

   private List<ConfiguredWorldCarver<?>> createForSurface(int surfaceY) {
      int caveFloorY = Math.max(this.tellusMinY, surfaceY - this.undergroundDepth + 1);
      int caveCeilingY = Math.max(caveFloorY, Math.min(this.tellusMaxY, surfaceY - 1));
      int caveExtraCeilingY = Math.max(caveFloorY, caveCeilingY - 16);
      int canyonFloorY = Math.max(caveFloorY, caveCeilingY - 96);
      int canyonCeilingY = caveCeilingY;
      int lavaLevelY = Math.min(caveCeilingY, caveFloorY + 8);

      TellusCaveCarver cave = new TellusCaveCarver(
         new CaveCarverConfiguration(
            CAVE_PROBABILITY,
            UniformHeight.of(VerticalAnchor.absolute(caveFloorY), VerticalAnchor.absolute(caveCeilingY)),
            UniformFloat.of(0.1F, 0.9F),
            VerticalAnchor.absolute(lavaLevelY),
            CarverDebugSettings.of(false, Blocks.CRIMSON_BUTTON.defaultBlockState()),
            replaceables,
            UniformFloat.of(0.7F, 1.4F),
            UniformFloat.of(0.8F, 1.3F),
            UniformFloat.of(-1.0F, -0.4F)
         )
      );
      TellusCaveCarver caveExtraUnderground = new TellusCaveCarver(
         new CaveCarverConfiguration(
            CAVE_EXTRA_PROBABILITY,
            UniformHeight.of(VerticalAnchor.absolute(caveFloorY), VerticalAnchor.absolute(caveExtraCeilingY)),
            UniformFloat.of(0.1F, 0.9F),
            VerticalAnchor.absolute(lavaLevelY),
            CarverDebugSettings.of(false, Blocks.OAK_BUTTON.defaultBlockState()),
            replaceables,
            UniformFloat.of(0.7F, 1.4F),
            UniformFloat.of(0.8F, 1.3F),
            UniformFloat.of(-1.0F, -0.4F)
         )
      );
      CanyonShapeConfiguration canyonShape = new CanyonShapeConfiguration(
         UniformFloat.of(0.75F, 1.0F), TrapezoidFloat.of(0.0F, 6.0F, 2.0F), 3, UniformFloat.of(0.75F, 1.0F), 1.0F, 0.0F
      );
      TellusRavineCarver canyon = new TellusRavineCarver(
         new CanyonCarverConfiguration(
            CANYON_PROBABILITY,
            UniformHeight.of(VerticalAnchor.absolute(canyonFloorY), VerticalAnchor.absolute(canyonCeilingY)),
            ConstantFloat.of(3.0F),
            VerticalAnchor.absolute(lavaLevelY),
            CarverDebugSettings.of(false, Blocks.WARPED_BUTTON.defaultBlockState()),
            replaceables,
            UniformFloat.of(-0.125F, 0.125F),
            canyonShape
         )
      );
      return List.of(cave.configured(), caveExtraUnderground.configured(), canyon.configured());
   }

   List<ConfiguredWorldCarver<?>> orderedCarvers(int surfaceY) {
      int boundedSurfaceY = Math.max(this.tellusMinY, Math.min(this.tellusMaxY, surfaceY));
      return this.carversBySurface.computeIfAbsent(boundedSurfaceY, this::createForSurface);
   }

}
