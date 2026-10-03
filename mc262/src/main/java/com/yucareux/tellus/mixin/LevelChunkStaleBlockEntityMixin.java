package com.yucareux.tellus.mixin;

import com.yucareux.tellus.Tellus;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkStaleBlockEntityMixin {
   @Unique
   private static final Set<String> TELLUS$LOGGED_STALE_PENDING_BLOCK_ENTITIES = ConcurrentHashMap.newKeySet();

   @Shadow
   public abstract BlockState getBlockState(BlockPos pos);

   @Inject(
      method = "promotePendingBlockEntity",
      at = @At("HEAD"),
      cancellable = true
   )
   private void tellus$skipStalePendingBlockEntity(BlockPos pos, CompoundTag tag, CallbackInfoReturnable<BlockEntity> cir) {
      BlockState state = this.getBlockState(pos);
      if (state.hasBlockEntity()) {
         return;
      }

      Optional<String> typeName = tag.getString("id");
      String logKey = typeName.orElse("<missing>") + "@" + state.getBlock();
      if (TELLUS$LOGGED_STALE_PENDING_BLOCK_ENTITIES.add(logKey)) {
         Tellus.LOGGER.warn("Skipping stale pending block entity {} at {} because the saved block is {}.", typeName.orElse("<missing>"), pos, state.getBlock());
      }

      cir.setReturnValue(null);
   }
}
