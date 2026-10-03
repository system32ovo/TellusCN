package com.yucareux.tellus.worldgen.building;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuildingFloorPlanTest {
   @Test
   void homesHaveConnectedRoomsAndCompleteBedPairsForEveryEntranceSide() {
      for (Direction entrance : Direction.Plane.HORIZONTAL) {
         for (int[] size : new int[][]{{14, 12}, {12, 16}, {20, 14}}) {
            BuildingBlueprint blueprint = blueprint(size[0], size[1], 2, entrance, false);
            BuildingFloorPlan plan = BuildingFloorPlan.create(blueprint, (x, z) -> true);
            assertNotNull(plan.stairs());
            assertFalse(plan.stairs().ladder());
            assertTrue(plan.floor(0).rooms().size() >= 2, describe(plan, 0));
            verifyRooms(plan, 0);
            verifyRooms(plan, 1);
            TellusBuildingInteriors interior = new TellusBuildingInteriors(plan);
            assertTrue(interior.fixtures(1).values().stream().anyMatch(f -> f.kind() == TellusBuildingInteriors.FixtureKind.BED_HEAD), describe(plan, 1));
            for (var entry : interior.fixtures(1).entrySet()) {
               var fixture = entry.getValue();
               if (fixture.kind() == TellusBuildingInteriors.FixtureKind.BED_HEAD) {
                  int foot = plan.indexAt(plan.worldX(entry.getKey()) - fixture.facing().getStepX(), plan.worldZ(entry.getKey()) - fixture.facing().getStepZ());
                  assertEquals(TellusBuildingInteriors.FixtureKind.BED_FOOT, interior.fixtures(1).get(foot).kind());
               }
            }
         }
      }
   }

   @Test
   void towerCoreSurvivesAllSetbacksAndFixturesNeverEnterCirculation() {
      BuildingBlueprint blueprint = blueprint(26, 24, 28, Direction.SOUTH, true);
      BuildingFloorPlan plan = BuildingFloorPlan.create(blueprint, (x, z) -> true);
      assertNotNull(plan.stairs());
      assertEquals(2, plan.stairs().flightWidth());
      assertEquals(0, blueprint.setbackForFloor(0));
      assertTrue(blueprint.setbackForFloor(4) > 0);
      for (int floorIndex = 0; floorIndex < blueprint.floorCount(); floorIndex++) {
         assertTrue(plan.floor(floorIndex).rooms().size() >= 2, describe(plan, floorIndex));
         verifyRooms(plan, floorIndex);
         for (int z = blueprint.minWorldZ(); z <= blueprint.maxWorldZ(); z++) {
            for (int x = blueprint.minWorldX(); x <= blueprint.maxWorldX(); x++) {
               if (plan.stairs().contains(x, z)) {
                  int boundary = Math.min(Math.min(x - blueprint.minWorldX(), blueprint.maxWorldX() - x),
                     Math.min(z - blueprint.minWorldZ(), blueprint.maxWorldZ() - z));
                  assertTrue(boundary > blueprint.setbackForFloor(floorIndex));
               }
            }
         }
      }
   }

   @Test
   void courtyardAngledAndLShapedFootprintsNeverClipRoomsOrStairs() {
      for (BiPredicate<Integer, Integer> shape : java.util.List.<BiPredicate<Integer, Integer>>of(
         (x, z) -> x < 12 || z < 12,
         (x, z) -> !(x >= 9 && x <= 15 && z >= 9 && z <= 15),
         (x, z) -> x + z >= 8 && x + z < 43)) {
         BuildingBlueprint blueprint = blueprint(25, 25, 3, Direction.NORTH, false, 0, 0);
         BuildingFloorPlan plan = BuildingFloorPlan.create(blueprint, shape);
         assertNotNull(plan.stairs());
         for (int floor = 0; floor < 3; floor++) {
            verifyRooms(plan, floor);
            for (var room : plan.floor(floor).rooms()) {
               for (int x = room.minX(); x < room.minX() + room.width(); x++) {
                  for (int z = room.minZ(); z < room.minZ() + room.depth(); z++) {
                     assertTrue(shape.test(x, z));
                  }
               }
            }
         }
         for (int z = 0; z < 25; z++) {
            for (int x = 0; x < 25; x++) {
               if (plan.stairs().contains(x, z)) {
                  assertTrue(shape.test(x, z));
               }
            }
         }
      }
   }

   @Test
   void compactAndSingleStoreyPlansDoNotCreateUnusableStairFragments() {
      BuildingFloorPlan single = BuildingFloorPlan.create(blueprint(14, 12, 1, Direction.WEST, false), (x, z) -> true);
      assertNull(single.stairs());
      BuildingFloorPlan tiny = BuildingFloorPlan.create(blueprint(5, 5, 2, Direction.NORTH, false), (x, z) -> true);
      assertNotNull(tiny.stairs());
      assertTrue(tiny.stairs().ladder());
      BuildingBlueprint slender = blueprint(9, 14, 25, Direction.EAST, true);
      assertEquals(0, slender.setbackForFloor(24));
   }

   @Test
   void plansRemainIdenticalAcrossNegativeCoordinatesAndChunkBoundaries() {
      BuildingBlueprint blueprint = blueprint(26, 24, 20, Direction.WEST, true, -19, -9);
      BuildingFloorPlan first = BuildingFloorPlan.create(blueprint, (x, z) -> true);
      BuildingFloorPlan second = BuildingFloorPlan.create(blueprint, (x, z) -> true);
      assertEquals(first.stairs(), second.stairs());
      for (int floor = 0; floor < 20; floor++) {
         assertEquals(first.floor(floor).rooms(), second.floor(floor).rooms());
         assertArrayEquals(first.floor(floor).walls(), second.floor(floor).walls());
         assertArrayEquals(first.floor(floor).doors(), second.floor(floor).doors());
      }
   }

   private static void verifyRooms(BuildingFloorPlan plan, int floorIndex) {
      var floor = plan.floor(floorIndex);
      var fixtures = new TellusBuildingInteriors(plan).fixtures(floorIndex);
      for (int index : fixtures.keySet()) {
         assertTrue(floor.canFurnish(index));
         assertFalse(plan.stairs() != null && plan.stairs().contains(plan.worldX(index), plan.worldZ(index)));
      }
      Set<Integer> visited = new HashSet<>();
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      int source = -1;
      for (int i = 0; i < floor.passage().length; i++) {
         if (floor.passage()[i]) {
            source = i;
            break;
         }
      }
      if (source < 0) {
         return;
      }
      queue.add(source);
      visited.add(source);
      while (!queue.isEmpty()) {
         int i = queue.removeFirst();
         for (Direction direction : Direction.Plane.HORIZONTAL) {
            int next = plan.indexAt(plan.worldX(i) + direction.getStepX(), plan.worldZ(i) + direction.getStepZ());
            if (floor.hasCell(next) && floor.passage()[next] && !floor.walls()[next] && visited.add(next)) {
               queue.add(next);
            }
         }
      }
      for (int i = 0; i < floor.doors().length; i++) {
         if (floor.doors()[i] != null) {
            assertTrue(visited.contains(i), "Disconnected doorway\n" + describe(plan, floorIndex));
         }
      }
   }

   static String describe(BuildingFloorPlan plan, int floorIndex) {
      var blueprint = plan.blueprint();
      var floor = plan.floor(floorIndex);
      StringBuilder text = new StringBuilder("\n");
      for (int z = blueprint.minWorldZ(); z <= blueprint.maxWorldZ(); z++) {
         for (int x = blueprint.minWorldX(); x <= blueprint.maxWorldX(); x++) {
            int i = plan.indexAt(x, z);
            text.append(plan.stairs() != null && plan.stairs().contains(x, z) ? 'S'
               : floor.doors()[i] != null ? 'D' : floor.walls()[i] ? '#'
               : floor.passage()[i] ? '+' : floor.roomIds()[i] >= 0 ? Character.forDigit(floor.roomIds()[i] % 10, 10) : '.');
         }
         text.append('\n');
      }
      return text.toString();
   }

   static BuildingBlueprint blueprint(int width, int depth, int floors, Direction entrance, boolean tower) {
      return blueprint(width, depth, floors, entrance, tower, -5, -7);
   }

   static BuildingBlueprint blueprint(int width, int depth, int floors, Direction entrance, boolean tower, int minX, int minZ) {
      BuildingProfile profile = new BuildingProfile(tower ? BuildingProfile.Archetype.TOWER : BuildingProfile.Archetype.HOUSE,
         tower ? BuildingProfile.BuildingCategory.MODERN_SKYSCRAPER : BuildingProfile.BuildingCategory.HOUSE,
         tower ? BuildingProfile.RoofProfile.FLAT_CROWN : BuildingProfile.RoofProfile.GABLED_X,
         BuildingProfile.ClimateFamily.TEMPERATE, floors, 4, true, tower ? 2 : 0, 3, tower ? 6 : 0, tower ? 4 : 0, 4);
      int entranceX = minX + (entrance == Direction.WEST ? 0 : entrance == Direction.EAST ? width - 1 : width / 2);
      int entranceZ = minZ + (entrance == Direction.NORTH ? 0 : entrance == Direction.SOUTH ? depth - 1 : depth / 2);
      return new BuildingBlueprint("test", 77L, profile, null, 63, 64, 64 + floors * 4, 67 + floors * 4,
         minX, minX + width - 1, minZ, minZ + depth - 1, entranceX, entranceZ, entrance, 1);
   }
}
