package com.yucareux.tellus.worldgen.building;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A landing and short stair run connecting the threshold to the actual outside ground. */
public final class BuildingEntranceAccess {
   private BuildingEntranceAccess() { }

   public static boolean place(BuildingBlueprint blueprint, int minY, int maxY,
      Function<BlockPos, BlockState> read, BiConsumer<BlockPos, BlockState> write) {
      if (blueprint.entranceWidth() == 0) return false;
      int floor = blueprint.floorY();
      Direction facing = blueprint.entranceFacing();
      BlockPos threshold = new BlockPos(blueprint.entranceWorldX(), floor, blueprint.entranceWorldZ());
      int length = 0;
      int[] grounds = new int[BuildingEntranceLayout.APPROACH_LENGTH + 1];
      for (int step = 1; step < grounds.length; step++) {
         BlockPos column = threshold.relative(facing, step);
         grounds[step] = groundAt(column, floor, minY, maxY, read);
         if (grounds[step] == Integer.MIN_VALUE) return false;
         int difference = Math.abs(grounds[step] - floor);
         if (difference == 0 || step >= difference + 2) { length = step; break; }
      }
      if (length == 0) return false;
      int end = grounds[length];
      int rise = end - floor;
      Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
      for (int step = 1; step <= length; step++) {
         BlockPos column = threshold.relative(facing, step);
         boolean stair = step >= 2 && step <= Math.abs(rise) + 1;
         int surface = floor + Integer.signum(rise) * Math.min(Math.abs(rise), Math.max(0, step - 1));
         if (stair && rise < 0) surface++;
         int clearTop = Math.max(Math.max(surface + 2, floor + 2), grounds[step]);
         if (surface < minY || clearTop > maxY) return false;
         for (int y = surface + 1; y <= clearTop; y++) {
            BlockPos position = new BlockPos(column.getX(), y, column.getZ());
            BlockState state = read.apply(position);
            // Never excavate liquids, a neighboring door or an unavailable chunk.
            if (!state.getFluidState().isEmpty() || state.is(Blocks.BARRIER) || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) return false;
            blocks.put(position, Blocks.AIR.defaultBlockState());
         }
         for (int y = Math.min(grounds[step], surface); y < surface; y++) {
            blocks.put(new BlockPos(column.getX(), y, column.getZ()), Blocks.STONE_BRICKS.defaultBlockState());
         }
         BlockState top = stair ? Blocks.STONE_BRICK_STAIRS.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, rise > 0 ? facing : facing.getOpposite())
            : Blocks.STONE_BRICKS.defaultBlockState();
         blocks.put(new BlockPos(column.getX(), surface, column.getZ()), top);
      }
      blocks.forEach(write);
      return true;
   }

   private static int groundAt(BlockPos column, int floor, int minY, int maxY, Function<BlockPos, BlockState> read) {
      for (int y = Math.min(maxY - 2, floor + 4); y >= Math.max(minY, floor - 5); y--) {
         BlockPos position = new BlockPos(column.getX(), y, column.getZ());
         BlockState state = read.apply(position);
         if (!state.getFluidState().isEmpty() || state.is(Blocks.BARRIER)) return Integer.MIN_VALUE;
         BlockState below = read.apply(position.below());
         if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
            && !(state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
            && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, position, Direction.UP)
            && below.getFluidState().isEmpty() && !below.getCollisionShape(EmptyBlockGetter.INSTANCE, position.below()).isEmpty()) return y;
      }
      return Integer.MIN_VALUE;
   }
}
