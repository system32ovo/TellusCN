package com.yucareux.tellus.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Adapts the Minecraft 1.21.1 chunk-generator contract to the shared implementation. */
public abstract class EarthChunkGeneratorVersionCompat extends ChunkGenerator {
   public static final MapCodec<EarthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(EarthChunkGenerator::getBiomeSource),
            EarthGeneratorSettings.CODEC.fieldOf("settings").forGetter(EarthChunkGenerator::settings)
         )
         .apply(instance, EarthChunkGenerator::new)
   );

   protected EarthChunkGeneratorVersionCompat(
      BiomeSource biomeSource, Function<Holder<Biome>, BiomeGenerationSettings> generationSettings
   ) {
      super(biomeSource, generationSettings);
   }

   @Override
   protected final MapCodec<? extends ChunkGenerator> codec() {
      return CODEC;
   }

   @Override
   public final CompletableFuture<ChunkAccess> createBiomes(
      RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk
   ) {
      return this.createBiomesShared(random, blender, structures, chunk);
   }

   @Override
   public final CompletableFuture<ChunkAccess> fillFromNoise(
      Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk
   ) {
      return this.fillFromNoiseShared(blender, random, structures, chunk);
   }

   protected abstract CompletableFuture<ChunkAccess> createBiomesShared(
      RandomState random, Blender blender, StructureManager structures, ChunkAccess chunk
   );

   protected abstract CompletableFuture<ChunkAccess> fillFromNoiseShared(
      Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk
   );

   protected static boolean canReplaceCaveBlock(BlockState state) {
      return true;
   }

   protected static Structure trialChambers(Registry<Structure> registry) {
      return Objects.requireNonNull(registry.getOrThrow(BuiltinStructures.TRIAL_CHAMBERS), "trialChambers");
   }

   protected static Axolotl createAxolotl(ServerLevel level, BlockPos position) {
      return (Axolotl)EntityType.AXOLOTL.create(level, entity -> {}, position, MobSpawnType.CHUNK_GENERATION, false, false);
   }
}
