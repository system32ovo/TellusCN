package com.yucareux.tellus.worldgen.caves;

import com.google.common.base.Preconditions;
import com.yucareux.tellus.worldgen.GeologicalStonePlacementPolicy;
import com.yucareux.tellus.worldgen.UndergroundFeatureClassifier;
import com.yucareux.tellus.worldgen.UndergroundStructureExclusion;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Samples the real vanilla Overworld density router into a temporary standard
 * height field, then projects its caves into Tellus terrain by depth below the
 * local surface. The temporary field never writes vanilla terrain into the
 * target chunk.
 */
public final class TellusVanillaNoiseCaveSampler {
   private static final int CHUNK_SIDE = 16;
   private static final int CHUNK_AREA = CHUNK_SIDE * CHUNK_SIDE;
   private static final int SURFACE_COVER_DEPTH = 4;
   private static final BlockState CAVE_AIR = Blocks.CAVE_AIR.defaultBlockState();
   private static final BlockState WATER = Blocks.WATER.defaultBlockState();
   private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

   private final NoiseGeneratorSettings vanillaSettings;
   private volatile SeededRandomState cachedRandomState;

   public TellusVanillaNoiseCaveSampler(NoiseGeneratorSettings vanillaSettings) {
      this.vanillaSettings = vanillaSettings;
   }

   public void apply(
      RegistryAccess registryAccess,
      long worldSeed,
      ChunkAccess chunk,
      int chunkMinY,
      int tellusSeaLevel,
      boolean applyCaves,
      boolean cavesReachSurface,
      boolean applyOreVeins,
      boolean applyGeologicalStonePatches,
      int[] surfaceYByColumn,
      IntBinaryOperator surfaceHeightSampler,
      int[] floodGuardYByColumn,
      int[] generationFloorYByColumn,
      List<UndergroundStructureExclusion.Box> structureExclusions,
      CaveBlockWriter blockWriter
   ) {
      Preconditions.checkArgument(
         applyCaves || applyOreVeins || applyGeologicalStonePatches,
         "At least one underground noise feature must be enabled"
      );
      Preconditions.checkArgument(surfaceYByColumn.length == CHUNK_AREA, "Tellus cave surface array must contain 256 columns");
      if (applyGeologicalStonePatches) {
         Objects.requireNonNull(surfaceHeightSampler, "surfaceHeightSampler");
      }
      RandomState randomState = this.randomStateFor(registryAccess, worldSeed);
      VanillaField field = this.sampleVanillaField(randomState, chunk.getPos());
      BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
      ChunkPos chunkPos = chunk.getPos();
      int chunkMinX = chunkPos.getMinBlockX();
      int chunkMinZ = chunkPos.getMinBlockZ();
      int[] minimumGeologySurfaceYByColumn = applyGeologicalStonePatches
         ? minimumNearbySurfaceYByColumn(
            surfaceHeightSampler,
            chunkMinX,
            chunkMinZ,
            GeologicalStonePlacementPolicy.NOISE_SURFACE_SAMPLE_RADIUS
         )
         : null;
      boolean exposeSurfaceEntrances = applyCaves && cavesReachSurface;

      for (int localZ = 0; localZ < CHUNK_SIDE; localZ++) {
         for (int localX = 0; localX < CHUNK_SIDE; localX++) {
            int columnIndex = localZ * CHUNK_SIDE + localX;
            int actualSurfaceY = surfaceYByColumn[columnIndex];
            int virtualSurfaceY = field.surfaceReferenceY(localX, localZ, exposeSurfaceEntrances);
            if (virtualSurfaceY <= field.minY()) {
               continue;
            }

            int actualBottomY = chunkMinY + 1;
            if (generationFloorYByColumn != null) {
               actualBottomY = Math.max(actualBottomY, generationFloorYByColumn[columnIndex] + 1);
            }
            int firstCarveY = exposeSurfaceEntrances ? actualSurfaceY : actualSurfaceY - SURFACE_COVER_DEPTH - 1;
            if (firstCarveY < actualBottomY) {
               continue;
            }

            int worldX = chunkMinX + localX;
            int worldZ = chunkMinZ + localZ;
            int minimumNearbySurfaceY = minimumGeologySurfaceYByColumn != null
               ? minimumGeologySurfaceYByColumn[columnIndex]
               : Integer.MAX_VALUE;
            for (int actualY = firstCarveY; actualY >= actualBottomY; actualY--) {
               boolean carvingBlocked =
                  UndergroundStructureExclusion.blocksCarving(structureExclusions, worldX, actualY, worldZ);
               int virtualY = TellusCaveDepthMapper.virtualYForActualY(
                  actualY, actualSurfaceY, actualBottomY, virtualSurfaceY, field.minY()
               );
               BlockState vanillaState = field.state(localX, virtualY, localZ);
               boolean caveAllowed = applyCaves
                  && !carvingBlocked
                  && (floodGuardYByColumn == null || actualY < floodGuardYByColumn[columnIndex]);
               BlockState replacement = caveAllowed ? caveReplacement(vanillaState, actualY, tellusSeaLevel) : null;
               boolean noiseFeature = false;
               if (replacement == null && (applyOreVeins || applyGeologicalStonePatches)) {
                  replacement = oreVeinReplacement(vanillaState, applyOreVeins, applyGeologicalStonePatches);
                  boolean geologicalStone = replacement != null
                     && UndergroundFeatureClassifier.isGeologicalStone(replacement.getBlock());
                  if (geologicalStone
                     && !GeologicalStonePlacementPolicy.isNoiseStoneBuried(actualY, minimumNearbySurfaceY)) {
                     replacement = null;
                  }
                  noiseFeature = replacement != null;
               }
               if (replacement == null) {
                  continue;
               }

               cursor.set(worldX, actualY, worldZ);
               BlockState current = chunk.getBlockState(cursor);
               boolean replaceable = noiseFeature
                  ? current.is(BlockTags.BASE_STONE_OVERWORLD)
                  : current.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES);
               if (!replaceable) {
                  continue;
               }

               blockWriter.set(chunk, cursor, replacement, !replacement.getFluidState().isEmpty());
            }
         }
      }
   }

   private VanillaField sampleVanillaField(RandomState randomState, ChunkPos chunkPos) {
      NoiseSettings noiseSettings = this.vanillaSettings.noiseSettings();
      int minY = noiseSettings.minY();
      int height = noiseSettings.height();
      int cellWidth = noiseSettings.getCellWidth();
      int cellHeight = noiseSettings.getCellHeight();
      int horizontalCellCount = CHUNK_SIDE / cellWidth;
      int verticalCellCount = height / cellHeight;
      int cellMinY = Math.floorDiv(minY, cellHeight);
      Aquifer.FluidPicker fluidPicker = createFluidPicker(this.vanillaSettings);
      // Structure starts have already been retargeted into Tellus's actual Y
      // range. Feeding those coordinates into this temporary vanilla-height
      // field mixes two vertical coordinate systems and can stretch a local
      // structure beard into a surface-reaching cave chamber.
      SamplingNoiseChunk noiseChunk = new SamplingNoiseChunk(
         horizontalCellCount,
         randomState,
         chunkPos.getMinBlockX(),
         chunkPos.getMinBlockZ(),
         noiseSettings,
         TellusEmptyBeardifier.instance(),
         this.vanillaSettings,
         fluidPicker,
         Blender.empty()
      );
      BlockState[] states = new BlockState[height * CHUNK_AREA];
      int[] preliminarySurfaceY = new int[CHUNK_AREA];
      for (int localZ = 0; localZ < CHUNK_SIDE; localZ++) {
         for (int localX = 0; localX < CHUNK_SIDE; localX++) {
            int worldX = chunkPos.getMinBlockX() + localX;
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            preliminarySurfaceY[localZ * CHUNK_SIDE + localX] = Mth.clamp(
               noiseChunk.preliminarySurfaceLevel(worldX, worldZ), minY, minY + height - 1
            );
         }
      }

      noiseChunk.initializeForFirstCellX();
      try {
         for (int cellX = 0; cellX < horizontalCellCount; cellX++) {
            noiseChunk.advanceCellX(cellX);
            for (int cellZ = 0; cellZ < horizontalCellCount; cellZ++) {
               for (int cellY = verticalCellCount - 1; cellY >= 0; cellY--) {
                  noiseChunk.selectCellYZ(cellY, cellZ);
                  for (int inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
                     int virtualY = (cellMinY + cellY) * cellHeight + inCellY;
                     noiseChunk.updateForY(virtualY, inCellY / (double)cellHeight);
                     for (int inCellX = 0; inCellX < cellWidth; inCellX++) {
                        int localX = cellX * cellWidth + inCellX;
                        int worldX = chunkPos.getMinBlockX() + localX;
                        noiseChunk.updateForX(worldX, inCellX / (double)cellWidth);
                        for (int inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                           int localZ = cellZ * cellWidth + inCellZ;
                           int worldZ = chunkPos.getMinBlockZ() + localZ;
                           noiseChunk.updateForZ(worldZ, inCellZ / (double)cellWidth);
                           BlockState state = noiseChunk.sampleInterpolatedState();
                           if (state == null) {
                              state = this.vanillaSettings.defaultBlock();
                           }
                           states[VanillaField.index(localX, virtualY, localZ, minY)] = state;
                        }
                     }
                  }
               }
            }
            noiseChunk.swapSlices();
         }
      } finally {
         noiseChunk.stopInterpolation();
      }

      return new VanillaField(minY, height, states, preliminarySurfaceY);
   }

   RandomState randomStateFor(RegistryAccess registryAccess, long worldSeed) {
      SeededRandomState cached = this.cachedRandomState;
      if (cached != null && cached.seed() == worldSeed) {
         return cached.state();
      }

      synchronized (this) {
         cached = this.cachedRandomState;
         if (cached == null || cached.seed() != worldSeed) {
            RandomState state = RandomState.create(
               this.vanillaSettings, registryAccess.lookupOrThrow(Registries.NOISE), worldSeed
            );
            cached = new SeededRandomState(worldSeed, state);
            this.cachedRandomState = cached;
         }
         return cached.state();
      }
   }

   private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
      Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, LAVA);
      Aquifer.FluidStatus sea = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
      int lavaCeiling = Math.min(-54, settings.seaLevel());
      return (x, y, z) -> y < lavaCeiling ? lava : sea;
   }

   private static BlockState caveReplacement(BlockState vanillaState, int actualY, int tellusSeaLevel) {
      if (vanillaState.isAir()) {
         return CAVE_AIR;
      }
      if (vanillaState.is(Blocks.LAVA)) {
         return LAVA;
      }
      if (!vanillaState.getFluidState().isEmpty()) {
         return actualY <= tellusSeaLevel ? WATER : CAVE_AIR;
      }
      return null;
   }

   static BlockState oreVeinReplacement(
      BlockState vanillaState, boolean applyOreVeins, boolean applyGeologicalStonePatches
   ) {
      if (applyOreVeins && (vanillaState.is(Blocks.COPPER_ORE)
         || vanillaState.is(Blocks.RAW_COPPER_BLOCK)
         || vanillaState.is(Blocks.DEEPSLATE_IRON_ORE)
         || vanillaState.is(Blocks.RAW_IRON_BLOCK))) {
         return vanillaState;
      }
      if (applyGeologicalStonePatches && (vanillaState.is(Blocks.GRANITE) || vanillaState.is(Blocks.TUFF))) {
         return vanillaState;
      }
      return null;
   }

   private static int[] minimumNearbySurfaceYByColumn(
      IntBinaryOperator surfaceHeightSampler, int chunkMinX, int chunkMinZ, int radius
   ) {
      int sampleSide = CHUNK_SIDE + radius * 2;
      int[] sampledSurfaceY = new int[sampleSide * sampleSide];
      for (int sampleZ = 0; sampleZ < sampleSide; sampleZ++) {
         for (int sampleX = 0; sampleX < sampleSide; sampleX++) {
            sampledSurfaceY[sampleZ * sampleSide + sampleX] = surfaceHeightSampler.applyAsInt(
               chunkMinX + sampleX - radius, chunkMinZ + sampleZ - radius
            );
         }
      }

      int[] minimumSurfaceYByColumn = new int[CHUNK_AREA];
      for (int localZ = 0; localZ < CHUNK_SIDE; localZ++) {
         for (int localX = 0; localX < CHUNK_SIDE; localX++) {
            int minimumSurfaceY = Integer.MAX_VALUE;
            for (int dz = 0; dz <= radius * 2; dz++) {
               for (int dx = 0; dx <= radius * 2; dx++) {
                  minimumSurfaceY = Math.min(
                     minimumSurfaceY, sampledSurfaceY[(localZ + dz) * sampleSide + localX + dx]
                  );
               }
            }
            minimumSurfaceYByColumn[localZ * CHUNK_SIDE + localX] = minimumSurfaceY;
         }
      }
      return minimumSurfaceYByColumn;
   }

   static int surfaceReferenceY(int highestSolidY, int preliminarySurfaceY, boolean cavesReachSurface) {
      return cavesReachSurface && preliminarySurfaceY - highestSolidY > SURFACE_COVER_DEPTH
         ? preliminarySurfaceY
         : highestSolidY;
   }

   private record SeededRandomState(long seed, RandomState state) {
   }

   @FunctionalInterface
   public interface CaveBlockWriter {
      void set(ChunkAccess chunk, BlockPos pos, BlockState state, boolean fluid);
   }

   private record VanillaField(int minY, int height, BlockState[] states, int[] preliminarySurfaceY) {
      BlockState state(int localX, int y, int localZ) {
         return this.states[index(localX, y, localZ, this.minY)];
      }

      int highestSolidY(int localX, int localZ) {
         for (int y = this.minY + this.height - 1; y >= this.minY; y--) {
            BlockState state = this.state(localX, y, localZ);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
               return y;
            }
         }
         return this.minY;
      }

      int surfaceReferenceY(int localX, int localZ, boolean cavesReachSurface) {
         int highestSolidY = this.highestSolidY(localX, localZ);
         int preliminaryY = this.preliminarySurfaceY[localZ * CHUNK_SIDE + localX];
         return TellusVanillaNoiseCaveSampler.surfaceReferenceY(highestSolidY, preliminaryY, cavesReachSurface);
      }

      static int index(int localX, int y, int localZ, int minY) {
         return (y - minY) * CHUNK_AREA + localZ * CHUNK_SIDE + localX;
      }
   }

   private static final class SamplingNoiseChunk extends NoiseChunk {
      SamplingNoiseChunk(
         int horizontalCellCount,
         RandomState randomState,
         int firstBlockX,
         int firstBlockZ,
         NoiseSettings noiseSettings,
         DensityFunctions.BeardifierOrMarker beardifier,
         NoiseGeneratorSettings generatorSettings,
         Aquifer.FluidPicker fluidPicker,
         Blender blender
      ) {
         super(
            horizontalCellCount,
            randomState,
            firstBlockX,
            firstBlockZ,
            noiseSettings,
            beardifier,
            generatorSettings,
            fluidPicker,
            blender
         );
      }

      BlockState sampleInterpolatedState() {
         return this.getInterpolatedState();
      }
   }
}
