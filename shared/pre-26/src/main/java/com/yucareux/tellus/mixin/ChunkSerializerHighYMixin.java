package com.yucareux.tellus.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public abstract class ChunkSerializerHighYMixin {
   private static final ThreadLocal<Integer> TELLUS$READ_SECTION_Y = ThreadLocal.withInitial(() -> 0);
   private static final ThreadLocal<Integer> TELLUS$WRITE_SECTION_Y = ThreadLocal.withInitial(() -> 0);

   @Redirect(
      method = "read",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/nbt/CompoundTag;getByte(Ljava/lang/String;)B"
      )
   )
   private static byte tellus$readFullSectionY(CompoundTag tag, String key) {
      int sectionY = tag.getInt(key);
      TELLUS$READ_SECTION_Y.set(sectionY);
      return (byte)sectionY;
   }

   @Redirect(
      method = "read",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerLevel;getSectionIndexFromSectionY(I)I"
      )
   )
   private static int tellus$indexFullSectionY(ServerLevel level, int ignoredSectionY) {
      return level.getSectionIndexFromSectionY(TELLUS$READ_SECTION_Y.get());
   }

   @Redirect(
      method = "read",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/core/SectionPos;"
      )
   )
   private static SectionPos tellus$lightPositionWithFullSectionY(ChunkPos chunkPos, int ignoredSectionY) {
      return SectionPos.of(chunkPos, TELLUS$READ_SECTION_Y.get());
   }

   @Redirect(
      method = "write",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSectionIndexFromSectionY(I)I"
      )
   )
   private static int tellus$captureFullSectionY(ChunkAccess chunk, int sectionY) {
      TELLUS$WRITE_SECTION_Y.set(sectionY);
      return chunk.getSectionIndexFromSectionY(sectionY);
   }

   @Redirect(
      method = "write",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/nbt/CompoundTag;putByte(Ljava/lang/String;B)V"
      )
   )
   private static void tellus$writeFullSectionY(CompoundTag tag, String key, byte ignoredSectionY) {
      tag.putInt(key, TELLUS$WRITE_SECTION_Y.get());
   }
}
