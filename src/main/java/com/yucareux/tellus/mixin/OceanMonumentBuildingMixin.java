package com.yucareux.tellus.mixin;

import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OceanMonumentPieces.MonumentBuilding.class)
public class OceanMonumentBuildingMixin {
   @Redirect(
      method = "postProcess",
      at = @At(
         value = "INVOKE",
         target = "Ljava/lang/Math;max(II)I",
         remap = false
      )
   )
   private int tellus$useTellusSeaLevel(
      int seaLevel,
      int minimumSeaLevel,
      WorldGenLevel level,
      StructureManager structureManager,
      ChunkGenerator generator,
      RandomSource random,
      BoundingBox box,
      ChunkPos chunkPos,
      BlockPos pos
   ) {
      return generator instanceof EarthChunkGenerator ? seaLevel : Math.max(seaLevel, minimumSeaLevel);
   }
}
