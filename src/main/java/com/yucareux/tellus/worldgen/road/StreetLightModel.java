package com.yucareux.tellus.worldgen.road;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/** A complete fixture is checked before any blocks are written, including arms crossing chunk boundaries. */
public final class StreetLightModel {
   private StreetLightModel() { }

   public static List<Part> parts(StreetLightPlanner.Lamp lamp, int baseY) {
      List<Part> parts = new ArrayList<>();
      BlockPos base = new BlockPos(lamp.worldX(), baseY, lamp.worldZ());
      BlockState pole = Blocks.IRON_BARS.defaultBlockState();
      parts.add(new Part(base.above(), Blocks.POLISHED_DEEPSLATE_WALL.defaultBlockState()));
      int poleTop = lamp.arm() == 0 ? lamp.height() - 1 : lamp.height();
      for (int y = 2; y <= poleTop; y++) parts.add(new Part(base.above(y), pole));
      BlockState cap = Blocks.IRON_TRAPDOOR.defaultBlockState()
         .setValue(BlockStateProperties.OPEN, false).setValue(BlockStateProperties.HALF, Half.BOTTOM)
         .setValue(BlockStateProperties.HORIZONTAL_FACING, lamp.facing());
      BlockPos head = base.above(lamp.height()).relative(lamp.facing(), lamp.arm());
      parts.add(new Part(head, Blocks.SEA_LANTERN.defaultBlockState()));
      parts.add(new Part(head.above(), cap));
      // A thin horizontal steel arm, with the diffuser suspended beneath its end.
      for (int arm = 0; arm < lamp.arm(); arm++) {
         parts.add(new Part(base.above(lamp.height() + 1).relative(lamp.facing(), arm), cap));
      }
      return List.copyOf(parts);
   }

   public static boolean place(StreetLightPlanner.Lamp lamp, int expectedGroundY, int minY, int maxY,
      Function<BlockPos, BlockState> read, BiConsumer<BlockPos, BlockState> write) {
      // Road plazas and curbs can raise or lower the sampled terrain slightly.
      for (int ground = Math.min(expectedGroundY + 2, maxY - lamp.height() - 1); ground >= Math.max(minY, expectedGroundY - 2); ground--) {
         BlockPos base = new BlockPos(lamp.worldX(), ground, lamp.worldZ());
         if (!supportsPole(read.apply(base), base) || !replaceable(read.apply(base.above()))) continue;
         List<Part> fixture = parts(lamp, ground);
         for (Part part : fixture) {
            if (!replaceable(read.apply(part.position()))) return false;
         }
         // Leave pedestrian room around the footing, and reject doorways, walls, trees and steep drop-offs.
         int supportedNeighbors = 0;
         int levelNeighbors = 0;
         for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = base.relative(side);
            if (!replaceable(read.apply(neighbor.above())) || !replaceable(read.apply(neighbor.above(2)))) return false;
            if (supportsPole(read.apply(neighbor), neighbor)) { levelNeighbors++; supportedNeighbors++; }
            else if (supportsPole(read.apply(neighbor.below()), neighbor.below())) supportedNeighbors++;
         }
         if (supportedNeighbors < 3 || ground > expectedGroundY && levelNeighbors < 2) return false;
         for (Part part : fixture) write.accept(part.position(), part.state());
         return true;
      }
      return false;
   }

   private static boolean supportsPole(BlockState state, BlockPos pos) {
      return state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
         && state.isFaceSturdy(EmptyBlockGetter.INSTANCE, pos, Direction.UP);
   }

   private static boolean replaceable(BlockState state) {
      return state.getFluidState().isEmpty() && (state.isAir() || state.is(Blocks.SNOW)
         || state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
   }

   public record Part(BlockPos position, BlockState state) { }
}
