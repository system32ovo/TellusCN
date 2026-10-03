package com.yucareux.tellus.mixin;

import com.yucareux.tellus.worldgen.HighYPackedCoordinateProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelHighYPackedCoordinateMixin {
   /**
    * Prevent commands and gameplay from entering coordinates that the dense global profile cannot encode.
    */
   @Inject(method = "isInWorldBoundsHorizontal", at = @At("HEAD"), cancellable = true)
   private static void tellus$restrictHorizontalBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         cir.setReturnValue(HighYPackedCoordinateProfile.containsHorizontal(pos.getX(), pos.getZ()));
      }
   }

   /**
    * Prevent commands from creating positions outside the dense profile's packed Y safety range.
    */
   @Inject(method = "isOutsideSpawnableHeight", at = @At("HEAD"), cancellable = true)
   private static void tellus$restrictSpawnableHeight(int y, CallbackInfoReturnable<Boolean> cir) {
      if (HighYPackedCoordinateProfile.isEnabled()) {
         cir.setReturnValue(y < HighYPackedCoordinateProfile.Y_MIN || y > HighYPackedCoordinateProfile.Y_MAX);
      }
   }
}
