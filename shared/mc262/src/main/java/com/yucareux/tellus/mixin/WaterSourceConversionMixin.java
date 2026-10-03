package com.yucareux.tellus.mixin;

import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables only the infinite-source conversion that can make DEM-following
 * waterfall sheets fill their surroundings. Normal spreading and downward
 * flow still run through vanilla's FlowingFluid implementation.
 */
@Mixin(FlowingFluid.class)
public class WaterSourceConversionMixin {
   @Inject(
      method = "getNewLiquid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/material/FluidState;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void tellus$gateWaterSourceConversion(
      ServerLevel level,
      BlockPos pos,
      BlockState state,
      CallbackInfoReturnable<FluidState> cir
   ) {
      FluidState current = level.getFluidState(pos);
      FluidState resolved = cir.getReturnValue();
      if (resolved.isSourceOfType(Fluids.WATER)
         && !current.isSource()
         && current.getType().isSame(Fluids.WATER)
         && level.getChunkSource().getGenerator() instanceof EarthChunkGenerator generator
         && generator.shouldSuppressWaterSourceConversion(pos.getX(), pos.getZ())) {
         // Returning the existing flowing state blocks only this attempted
         // conversion. The next vanilla tick still updates and spreads it.
         cir.setReturnValue(current);
      }
   }
}
