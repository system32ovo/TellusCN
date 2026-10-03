package com.yucareux.tellus.mixin;

import java.util.List;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SerializableChunkData.class)
public interface SerializableChunkDataAccessor {
   @Mutable
   @Accessor("sectionData")
   void tellus$setSectionData(List<SectionData> sections);
}
