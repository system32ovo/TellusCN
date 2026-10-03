package com.yucareux.tellus.mixin;

import com.mojang.serialization.Codec;
import com.yucareux.tellus.Tellus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataHighYMixin {
   @Unique
   private static final String TELLUS$SECTIONS_TAG = "sections";
   @Unique
   private static final String TELLUS$SECTION_Y_TAG = "Y";
   @Unique
   private static final String TELLUS$BLOCK_STATES_TAG = "block_states";
   @Unique
   private static final String TELLUS$BIOMES_TAG = "biomes";
   @Unique
   private static final String TELLUS$BLOCK_LIGHT_TAG = "BlockLight";
   @Unique
   private static final String TELLUS$SKY_LIGHT_TAG = "SkyLight";

   @Inject(
      method = "write",
      at = @At("RETURN")
   )
   private void tellus$writeFullSectionY(CallbackInfoReturnable<CompoundTag> cir) {
      SerializableChunkData self = (SerializableChunkData)(Object)this;
      List<SectionData> sections = self.sectionData();
      ListTag sectionTags = cir.getReturnValue().getListOrEmpty(TELLUS$SECTIONS_TAG);
      int count = Math.min(sections.size(), sectionTags.size());

      for (int i = 0; i < count; i++) {
         Optional<CompoundTag> sectionTag = sectionTags.getCompound(i);
         if (sectionTag.isPresent()) {
            int sectionY = sections.get(i).y();
            if (sectionY < Byte.MIN_VALUE || sectionY > Byte.MAX_VALUE) {
               sectionTag.get().putInt(TELLUS$SECTION_Y_TAG, sectionY);
            }
         }
      }
   }

   @Inject(
      method = "parse",
      at = @At("RETURN"),
      cancellable = true
   )
   private static void tellus$parseFullSectionY(
      LevelHeightAccessor levelHeightAccessor,
      PalettedContainerFactory containerFactory,
      CompoundTag tag,
      CallbackInfoReturnable<SerializableChunkData> cir
   ) {
      ListTag sectionTags = tag.getListOrEmpty(TELLUS$SECTIONS_TAG);
      if (!tellus$hasExtendedSectionY(sectionTags)) {
         return;
      }

      SerializableChunkData original = cir.getReturnValue();
      if (original == null) {
         return;
      }

      ChunkPos chunkPos = original.chunkPos();
      List<SectionData> repairedSections = new ArrayList<>(sectionTags.size());
      Codec<PalettedContainer<BlockState>> blockStatesCodec = containerFactory.blockStatesContainerCodec();
      Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = containerFactory.biomeContainerCodec();

      for (int i = 0; i < sectionTags.size(); i++) {
         Optional<CompoundTag> sectionTag = sectionTags.getCompound(i);
         if (sectionTag.isEmpty()) {
            continue;
         }

         CompoundTag section = sectionTag.get();
         int sectionY = section.getIntOr(TELLUS$SECTION_Y_TAG, 0);
         LevelChunkSection chunkSection = null;
         if (sectionY >= levelHeightAccessor.getMinSectionY() && sectionY <= levelHeightAccessor.getMaxSectionY()) {
            PalettedContainer<BlockState> blockStates = tellus$parseBlockStates(
               blockStatesCodec, containerFactory, chunkPos, sectionY, section
            );
            PalettedContainerRO<Holder<Biome>> biomes = tellus$parseBiomes(biomeCodec, containerFactory, chunkPos, sectionY, section);
            chunkSection = new LevelChunkSection(blockStates, biomes);
         }

         DataLayer blockLight = section.getByteArray(TELLUS$BLOCK_LIGHT_TAG).map(DataLayer::new).orElse(null);
         DataLayer skyLight = section.getByteArray(TELLUS$SKY_LIGHT_TAG).map(DataLayer::new).orElse(null);
         repairedSections.add(new SectionData(sectionY, chunkSection, blockLight, skyLight));
      }

      ((SerializableChunkDataAccessor)(Object)original).tellus$setSectionData(repairedSections);
      cir.setReturnValue(original);
   }

   @Unique
   private static boolean tellus$hasExtendedSectionY(ListTag sectionTags) {
      for (int i = 0; i < sectionTags.size(); i++) {
         Optional<CompoundTag> sectionTag = sectionTags.getCompound(i);
         if (sectionTag.isPresent()) {
            int sectionY = sectionTag.get().getIntOr(TELLUS$SECTION_Y_TAG, 0);
            if (sectionY < Byte.MIN_VALUE || sectionY > Byte.MAX_VALUE) {
               return true;
            }
         }
      }

      return false;
   }

   @Unique
   private static PalettedContainer<BlockState> tellus$parseBlockStates(
      Codec<PalettedContainer<BlockState>> codec,
      PalettedContainerFactory containerFactory,
      ChunkPos chunkPos,
      int sectionY,
      CompoundTag section
   ) {
      Optional<CompoundTag> blockStates = section.getCompound(TELLUS$BLOCK_STATES_TAG);
      if (blockStates.isEmpty()) {
         return containerFactory.createForBlockStates();
      }

      return codec.parse(NbtOps.INSTANCE, blockStates.get())
         .resultOrPartial(message -> tellus$logSectionParseError(chunkPos, sectionY, TELLUS$BLOCK_STATES_TAG, message))
         .orElseGet(containerFactory::createForBlockStates);
   }

   @Unique
   private static PalettedContainerRO<Holder<Biome>> tellus$parseBiomes(
      Codec<PalettedContainerRO<Holder<Biome>>> codec,
      PalettedContainerFactory containerFactory,
      ChunkPos chunkPos,
      int sectionY,
      CompoundTag section
   ) {
      Optional<CompoundTag> biomes = section.getCompound(TELLUS$BIOMES_TAG);
      if (biomes.isEmpty()) {
         return containerFactory.createForBiomes();
      }

      return codec.parse(NbtOps.INSTANCE, biomes.get())
         .resultOrPartial(message -> tellus$logSectionParseError(chunkPos, sectionY, TELLUS$BIOMES_TAG, message))
         .orElseGet(containerFactory::createForBiomes);
   }

   @Unique
   private static void tellus$logSectionParseError(ChunkPos chunkPos, int sectionY, String tagName, String message) {
      Tellus.LOGGER.error("Recovering chunk {} section {} with invalid {} data: {}", chunkPos, sectionY, tagName, message);
   }
}
