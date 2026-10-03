package com.yucareux.tellus.mixin;

import com.yucareux.tellus.Tellus;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockEntity.class})
public abstract class BlockEntityLoadStaticMixin {
   @Unique
   private static final Set<String> TELLUS$LOGGED_STALE_BLOCK_ENTITIES = ConcurrentHashMap.newKeySet();

   @Inject(
      method = {"loadStatic"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void tellus$skipStaleBlockEntityNbt(
      BlockPos pos,
      BlockState state,
      CompoundTag tag,
      HolderLookup.Provider lookup,
      CallbackInfoReturnable<BlockEntity> cir
   ) {
      if (state.hasBlockEntity()) {
         return;
      }

      Optional<String> typeName = tag.getString("id");
      if (typeName.isEmpty()) {
         return;
      }

      Identifier typeId = Identifier.tryParse(typeName.get());
      if (typeId == null) {
         return;
      }

      BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(typeId);
      if (type == null || type.isValid(state)) {
         return;
      }

      String logKey = typeId + "@" + state.getBlock();
      if (TELLUS$LOGGED_STALE_BLOCK_ENTITIES.add(logKey)) {
         Tellus.LOGGER.warn("Skipping stale block entity {} at {} because the saved block is {}.", typeId, pos, state.getBlock());
      }
      cir.setReturnValue(null);
   }
}
