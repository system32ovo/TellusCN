package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.compat.MinecraftRelease;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Renders an entire interior column from one plan, including openings in floor slabs. */
public final class TellusBuildingInteriors {
   private final BuildingFloorPlan plan;
   private final Map<Integer, Map<Integer, Fixture>> furnishings = new ConcurrentHashMap<>();

   public TellusBuildingInteriors(BuildingFloorPlan plan) {
      this.plan = plan;
   }

   public BuildingFloorPlan plan() {
      return this.plan;
   }

   public BlockState blockAt(TellusBuildingMaterials.BuildingMaterialPalette palette, int boundaryDistance,
      int x, int z, int floorIndex, int y) {
      BuildingBlueprint blueprint = this.plan.blueprint();
      int bottom = blueprint.floorBottomY(floorIndex);
      int dy = y - bottom;
      // Rasterized diagonal walls can be two or three cells thick along the doorway's axis.
      if (floorIndex == 0 && this.plan.isEntrancePassage(x, z) && dy <= 2) {
         return dy == 0 ? palette.floor() : Blocks.AIR.defaultBlockState();
      }
      boolean facade = blueprint.isFacadeCell(boundaryDistance, floorIndex);
      if (facade) {
         if (dy == 0) {
            return blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER ? palette.accent() : palette.wall();
         }
         return TellusBuildingFacade.resolveFacadeBlock(blueprint, palette, boundaryDistance, x, z,
            floorIndex, bottom, blueprint.floorTopY(floorIndex), y);
      }
      BuildingFloorPlan.Stairwell stairs = this.plan.stairs();
      if (stairs != null && stairs.contains(x, z)) {
         if (stairs.ladder()) {
            if (x == stairs.minX()) {
               return palette.partition();
            }
            return dy == 0 && floorIndex == 0 ? palette.floor()
               : Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
         }
         int step = stairs.step(x, z);
         if (step >= 0) {
            // The final step belongs to the next floor's slab, not the previous floor's ceiling.
            if (floorIndex > 0 && dy == 0 && step == stairs.height() - 1
               || floorIndex < blueprint.floorCount() - 1 && dy == step + 1) {
               return palette.stair().setValue(BlockStateProperties.HORIZONTAL_FACING, stairs.facing());
            }
            if (floorIndex < blueprint.floorCount() - 1 && dy == step) {
               // A continuous underside supports the treads without filling the headroom below.
               return palette.floor();
            }
            return dy == 0 && floorIndex == 0 ? palette.floor() : Blocks.AIR.defaultBlockState();
         }
         if (dy == 0) {
            return palette.floor();
         }
         if (dy == 1 && stairs.railing(x, z)) {
            return Blocks.GLASS_PANE.defaultBlockState()
               .setValue(BlockStateProperties.EAST, stairs.alongX()).setValue(BlockStateProperties.WEST, stairs.alongX())
               .setValue(BlockStateProperties.NORTH, !stairs.alongX()).setValue(BlockStateProperties.SOUTH, !stairs.alongX());
         }
         return Blocks.AIR.defaultBlockState();
      }
      BuildingFloorPlan.Floor floor = this.plan.floor(floorIndex);
      int index = this.plan.indexAt(x, z);
      if (dy == 0) {
         return palette.floor();
      }
      if (floor.hasCell(index)) {
         Direction door = floor.doors()[index];
         if (door != null) {
            return dy <= 2 ? Blocks.OAK_DOOR.defaultBlockState()
               .setValue(BlockStateProperties.HORIZONTAL_FACING, door)
               .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)
               : palette.partition();
         }
         if (floor.walls()[index]) {
            boolean office = blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER
               || blueprint.profile().archetype() == BuildingProfile.Archetype.COMMERCIAL;
            return office && dy == 2 ? Blocks.GLASS.defaultBlockState() : palette.partition();
         }
         Fixture fixture = fixtures(floorIndex).get(index);
         if (fixture != null && dy == 1) {
            return fixtureState(fixture, palette);
         }
      }
      // Flush ceiling lighting stays outside stair flights and leaves two full blocks of headroom.
      if (dy >= 3 && y == blueprint.floorTopY(floorIndex)
         && TellusBuildingLighting.shouldPlaceInteriorLight(blueprint, boundaryDistance, x, z, floorIndex)) {
         return palette.light();
      }
      return Blocks.AIR.defaultBlockState();
   }

   public Map<Integer, Fixture> fixtures(int floorIndex) {
      BuildingBlueprint blueprint = this.plan.blueprint();
      int variant = floorIndex == 0 ? 0 : 1;
      int key = blueprint.setbackForFloor(floorIndex) * 2 + variant;
      return this.furnishings.computeIfAbsent(key, ignored -> furnish(floorIndex));
   }

   public RoomUse roomUse(BuildingFloorPlan.Room room, int floorIndex) {
      BuildingBlueprint blueprint = this.plan.blueprint();
      return switch (blueprint.profile().archetype()) {
         case HOUSE -> floorIndex == 0
            ? switch (room.id() % 4) {
               case 0 -> blueprint.floorCount() == 1 ? RoomUse.BEDROOM : RoomUse.LIVING;
               case 1 -> RoomUse.KITCHEN;
               case 2 -> RoomUse.BATHROOM;
               default -> RoomUse.LIVING;
            }
            : room.id() % 3 == 2 ? RoomUse.BATHROOM : RoomUse.BEDROOM;
         case APARTMENT -> floorIndex == 0 && room.id() == 0 ? RoomUse.LOBBY
            : switch (room.id() % 3) {
               case 0 -> RoomUse.KITCHEN;
               case 1 -> RoomUse.BEDROOM;
               default -> RoomUse.BATHROOM;
            };
         case TOWER, COMMERCIAL -> floorIndex == 0 ? (room.id() == 0 ? RoomUse.LOBBY : RoomUse.LIVING)
            : switch (room.id() % 4) {
               case 0 -> RoomUse.MEETING;
               case 1 -> RoomUse.OFFICE;
               case 2 -> RoomUse.KITCHEN;
               default -> RoomUse.BATHROOM;
            };
         case INDUSTRIAL -> RoomUse.STORAGE;
         case GENERIC -> RoomUse.OFFICE;
      };
   }

   private Map<Integer, Fixture> furnish(int floorIndex) {
      Map<Integer, Fixture> result = new HashMap<>();
      BuildingFloorPlan.Floor floor = this.plan.floor(floorIndex);
      for (BuildingFloorPlan.Room room : floor.rooms()) {
         RoomUse use = roomUse(room, floorIndex);
         if (use == RoomUse.BEDROOM) {
            placeBed(result, floor, room);
         }
         if (use == RoomUse.OFFICE || use == RoomUse.MEETING || use == RoomUse.KITCHEN) {
            placeTable(result, floor, room, use == RoomUse.MEETING);
         }
         List<FixtureKind> pieces = switch (use) {
            case BEDROOM -> List.of(FixtureKind.CABINET, FixtureKind.BOOKCASE);
            case KITCHEN -> List.of(FixtureKind.FURNACE, FixtureKind.SINK, FixtureKind.COUNTER, FixtureKind.CABINET);
            case BATHROOM -> List.of(FixtureKind.SINK, FixtureKind.TOILET);
            case LIVING -> List.of(FixtureKind.SOFA, FixtureKind.SOFA, FixtureKind.BOOKCASE);
            case LOBBY -> List.of(FixtureKind.COUNTER, FixtureKind.COUNTER, FixtureKind.SOFA, FixtureKind.PLANT);
            case OFFICE -> List.of(FixtureKind.BOOKCASE, FixtureKind.CABINET);
            case MEETING -> List.of(FixtureKind.BOOKCASE, FixtureKind.PLANT);
            case STORAGE -> List.of(FixtureKind.CABINET, FixtureKind.CABINET, FixtureKind.CABINET);
         };
         int nextPiece = 0;
         for (int z = room.minZ(); z < room.minZ() + room.depth() && nextPiece < pieces.size(); z++) {
            for (int x = room.minX(); x < room.minX() + room.width() && nextPiece < pieces.size(); x++) {
               int index = this.plan.indexAt(x, z);
               if (!canPlace(floor, room, result, index) || !nearWall(room, x, z)) {
                  continue;
               }
               Direction facing = facingRoom(room, x, z);
               int access = this.plan.indexAt(x + facing.getStepX(), z + facing.getStepZ());
               if (!floor.hasCell(access) || floor.walls()[access] || result.containsKey(access)) {
                  continue;
               }
               result.put(index, new Fixture(pieces.get(nextPiece++), facing));
            }
         }
      }
      return Map.copyOf(result);
   }

   private void placeTable(Map<Integer, Fixture> fixtures, BuildingFloorPlan.Floor floor, BuildingFloorPlan.Room room, boolean meeting) {
      for (int z = room.minZ() + 1; z < room.minZ() + room.depth() - 1; z++) {
         for (int x = room.minX() + 1; x < room.minX() + room.width() - 1; x++) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
               int table = this.plan.indexAt(x, z);
               int chair = this.plan.indexAt(x - facing.getStepX(), z - facing.getStepZ());
               int opposite = this.plan.indexAt(x + facing.getStepX(), z + facing.getStepZ());
               if (canPlace(floor, room, fixtures, table) && canPlace(floor, room, fixtures, chair)
                  && (!meeting || canPlace(floor, room, fixtures, opposite))) {
                  fixtures.put(table, new Fixture(FixtureKind.DESK, facing));
                  fixtures.put(chair, new Fixture(FixtureKind.CHAIR, facing));
                  if (meeting) fixtures.put(opposite, new Fixture(FixtureKind.CHAIR, facing.getOpposite()));
                  return;
               }
            }
         }
      }
   }

   private void placeBed(Map<Integer, Fixture> fixtures, BuildingFloorPlan.Floor floor, BuildingFloorPlan.Room room) {
      for (int z = room.minZ(); z < room.minZ() + room.depth(); z++) {
         for (int x = room.minX(); x < room.minX() + room.width(); x++) {
            int head = this.plan.indexAt(x, z);
            if (!canPlace(floor, room, fixtures, head) || !nearWall(room, x, z)) {
               continue;
            }
            for (Direction facing : Direction.Plane.HORIZONTAL) {
               int foot = this.plan.indexAt(x - facing.getStepX(), z - facing.getStepZ());
               if (canPlace(floor, room, fixtures, foot)) {
                  fixtures.put(head, new Fixture(FixtureKind.BED_HEAD, facing));
                  fixtures.put(foot, new Fixture(FixtureKind.BED_FOOT, facing));
                  return;
               }
            }
         }
      }
   }

   private boolean canPlace(BuildingFloorPlan.Floor floor, BuildingFloorPlan.Room room, Map<Integer, Fixture> fixtures, int index) {
      return floor.canFurnish(index) && floor.roomIds()[index] == room.id() && !fixtures.containsKey(index);
   }

   private static boolean nearWall(BuildingFloorPlan.Room room, int x, int z) {
      return x <= room.minX() + 1 || z <= room.minZ() + 1
         || x >= room.minX() + room.width() - 2 || z >= room.minZ() + room.depth() - 2;
   }

   private static Direction facingRoom(BuildingFloorPlan.Room room, int x, int z) {
      if (z <= room.minZ() + 1) {
         return Direction.SOUTH;
      }
      if (z >= room.minZ() + room.depth() - 2) {
         return Direction.NORTH;
      }
      return x <= room.minX() + 1 ? Direction.EAST : Direction.WEST;
   }

   private static BlockState fixtureState(Fixture fixture, TellusBuildingMaterials.BuildingMaterialPalette palette) {
      BlockState state = switch (fixture.kind()) {
         case BED_HEAD, BED_FOOT -> MinecraftRelease.block("white_bed").defaultBlockState()
            .setValue(BlockStateProperties.BED_PART, fixture.kind() == FixtureKind.BED_HEAD ? BedPart.HEAD : BedPart.FOOT);
         case CABINET -> Blocks.BARREL.defaultBlockState();
         case BOOKCASE -> Blocks.BOOKSHELF.defaultBlockState();
         case FURNACE -> Blocks.FURNACE.defaultBlockState();
         case SINK -> Blocks.CAULDRON.defaultBlockState();
         case COUNTER -> Blocks.SMOOTH_STONE.defaultBlockState();
         case TOILET -> Blocks.QUARTZ_STAIRS.defaultBlockState();
         case DESK -> palette.slab().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
         case CHAIR, SOFA -> Blocks.SPRUCE_STAIRS.defaultBlockState();
         case PLANT -> Blocks.POTTED_FERN.defaultBlockState();
      };
      if (fixture.kind() == FixtureKind.CHAIR || fixture.kind() == FixtureKind.SOFA || fixture.kind() == FixtureKind.TOILET) {
         // The high half of a stair is the back of the seat.
         return state.setValue(BlockStateProperties.HORIZONTAL_FACING, fixture.facing().getOpposite());
      }
      return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
         ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, fixture.facing()) : state;
   }

   public enum RoomUse { BEDROOM, KITCHEN, BATHROOM, LIVING, LOBBY, OFFICE, MEETING, STORAGE }
   public enum FixtureKind { BED_HEAD, BED_FOOT, CABINET, BOOKCASE, FURNACE, SINK, COUNTER, TOILET, DESK, CHAIR, SOFA, PLANT }
   public record Fixture(FixtureKind kind, Direction facing) { }
}
