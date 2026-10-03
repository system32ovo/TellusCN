package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainNetworkPolicy;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class TellusBuildingInteriorRenderingTest {
   @TempDir static Path directory;
   private static EarthChunkGenerator generator;
   private static Constructor<?> preparedFeature;
   private static Method placeColumn;
   private static String previousGameDir;
   private static String previousConfigDir;

   @BeforeAll
   static void setup() throws Exception {
      previousGameDir = System.getProperty("tellus.gameDir");
      previousConfigDir = System.getProperty("tellus.configDir");
      assumeFalse(TellusBuildingInteriorRenderingTest.class.getClassLoader().getResource("net/minecraftforge/fml/ModList.class") != null,
         "Forge's raw JUnit bootstrap lacks ModLauncher; the same generator is exercised on Fabric 1.20.1");
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      System.setProperty("tellus.gameDir", directory.toString());
      System.setProperty("tellus.configDir", directory.resolve("config").toString());
      var biome = VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
      var components = EarthGeneratorSettings.class.getRecordComponents();
      Class<?>[] types = new Class<?>[components.length];
      Object[] values = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
         types[i] = components[i].getType();
         values[i] = components[i].getName().equals("worldScale") ? 1.0 : components[i].getAccessor().invoke(EarthGeneratorSettings.DEFAULT);
      }
      EarthGeneratorSettings settings = EarthGeneratorSettings.class.getDeclaredConstructor(types).newInstance(values);
      assertEquals(1.0, settings.worldScale());
      try (var ignored = ManagedTerrainNetworkPolicy.cacheOnly()) {
         generator = new EarthChunkGenerator(new FixedBiomeSource(biome), settings);
      }
      Class<?> featureType = Class.forName(EarthChunkGenerator.class.getName() + "$PreparedBuildingFeature");
      preparedFeature = featureType.getDeclaredConstructors()[0];
      preparedFeature.setAccessible(true);
      placeColumn = EarthChunkGenerator.class.getDeclaredMethod("placePreparedBuildingColumn", WorldGenLevel.class,
         BlockPos.MutableBlockPos.class, int.class, featureType, TellusBuildingMaterials.BuildingMaterialPalette.class,
         int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class);
      placeColumn.setAccessible(true);
   }

   @AfterAll
   static void restore() {
      if (previousGameDir == null) System.clearProperty("tellus.gameDir"); else System.setProperty("tellus.gameDir", previousGameDir);
      if (previousConfigDir == null) System.clearProperty("tellus.configDir"); else System.setProperty("tellus.configDir", previousConfigDir);
   }

   @Test
   void generatedHousesHaveWalkableStairsAndEveryRoomIsReachable() throws Exception {
      for (Direction entrance : Direction.Plane.HORIZONTAL) {
         BuildingBlueprint blueprint = BuildingFloorPlanTest.blueprint(16, 14, 3, entrance, false);
         Map<BlockPos, BlockState> blocks = render(blueprint, false);
         assertAccessible(blueprint, blocks);
         assertClearWindows(blueprint, blocks);
         if (entrance == Direction.NORTH) export("house", blocks);
      }
   }

   @Test
   void generatedTowerConnectsLobbyToEveryFloorIncludingTheCrown() throws Exception {
      BuildingBlueprint blueprint = BuildingFloorPlanTest.blueprint(26, 24, 24, Direction.SOUTH, true);
      Map<BlockPos, BlockState> blocks = render(blueprint, false);
      assertAccessible(blueprint, blocks);
      assertClearWindows(blueprint, blocks);
      export("tower", blocks);
   }

   @Test
   void reversedChunkAndColumnOrderCannotRefillStairOpeningsOrEraseFurniture() throws Exception {
      BuildingBlueprint blueprint = BuildingFloorPlanTest.blueprint(26, 24, 14, Direction.EAST, true, -19, -9);
      Map<BlockPos, BlockState> forward = render(blueprint, false);
      Map<BlockPos, BlockState> reverse = render(blueprint, true);
      for (int z = blueprint.minWorldZ(); z <= blueprint.maxWorldZ(); z++) {
         for (int x = blueprint.minWorldX(); x <= blueprint.maxWorldX(); x++) {
            int distance = boundary(blueprint, x, z);
            for (int floor = 0; floor <= blueprint.highestActiveFloor(distance); floor++) {
               if (!blueprint.isFacadeCell(distance, floor)) {
                  for (int y = blueprint.floorBottomY(floor); y <= blueprint.floorTopY(floor); y++) {
                     BlockPos position = new BlockPos(x, y, z);
                     assertEquals(forward.get(position), reverse.get(position), position.toString());
                  }
               }
            }
         }
      }
      assertAccessible(blueprint, reverse);
   }

   @Test
   void stairsHaveTwoBlocksOfHeadroomAndDoNotContinueIntoTheRoof() {
      BuildingBlueprint blueprint = BuildingFloorPlanTest.blueprint(16, 14, 4, Direction.NORTH, false);
      TellusBuildingInteriors interior = new TellusBuildingInteriors(BuildingFloorPlan.create(blueprint, (x, z) -> true));
      var stairs = interior.plan().stairs();
      var palette = TellusBuildingMaterials.resolvePalette(blueprint);
      for (int floor = 0; floor < blueprint.floorCount() - 1; floor++) {
         for (int z = blueprint.minWorldZ(); z <= blueprint.maxWorldZ(); z++) {
            for (int x = blueprint.minWorldX(); x <= blueprint.maxWorldX(); x++) {
               int step = stairs.step(x, z);
               if (step < 0) continue;
               int y = blueprint.floorBottomY(floor) + step + 1;
               assertTrue(interior.blockAt(palette, boundary(blueprint, x, z), x, z, blueprint.floorIndexAtY(y), y).getBlock() instanceof StairBlock);
               for (int head = y + 1; head <= y + 2; head++) {
                  assertTrue(interior.blockAt(palette, boundary(blueprint, x, z), x, z, blueprint.floorIndexAtY(head), head).isAir(), "Blocked flight at " + x + "," + y + "," + z);
               }
            }
         }
      }
   }

   @Test
   void irregularHousesKeepCourtyardAndWingCirculationConnected() throws Exception {
      BuildingBlueprint blueprint = BuildingFloorPlanTest.blueprint(25, 25, 3, Direction.NORTH, false, 0, 0);
      for (BiPredicate<Integer, Integer> shape : List.<BiPredicate<Integer, Integer>>of(
         (x, z) -> x < 12 || z < 12,
         (x, z) -> !(x >= 9 && x <= 15 && z >= 9 && z <= 15),
         (x, z) -> x + z >= 8 && x + z < 43)) {
         var blocks = render(blueprint, false, shape);
         assertAccessible(blueprint, blocks, BuildingFloorPlan.create(blueprint, shape));
      }
   }

   @Test
   void realRasterizedDoorsConnectTheStreetToTheInteriorThroughAngledAndConcaveWalls() throws Exception {
      List<com.yucareux.tellus.world.data.osm.OsmBuildingFeature> features = List.of(
         BuildingEntranceLayoutTest.feature(1, new double[]{-20,-8, -4,-20, 12,-8, -4,6, -20,-8}),
         BuildingEntranceLayoutTest.feature(2, new double[]{-20.25,-15.75, 3.75,-7.5, 14.75,-26.25, -6.5,-36.25, -20.25,-15.75}),
         BuildingEntranceLayoutTest.feature(3, new double[]{-17,-17, 8,-17, 8,-8, -7,-8, -7,8, -17,8, -17,-17}),
         BuildingEntranceLayoutTest.feature(4, new double[]{-20,-20, 8,-20, 8,8, -20,8, -20,-20},
            new double[]{-12,-12, -12,0, 0,0, 0,-12, -12,-12})
      );
      for (var feature : features) {
         var profile = BuildingFloorPlanTest.blueprint(24,24,3,Direction.NORTH,false).profile();
         var blueprint = TellusBuildingBlueprints.create("entrance", feature, profile, 77,63,64,76,79,
            List.of(BuildingEntranceLayoutTest.road(-1000,-43,1000,-43)),1);
         assertEquals(1, blueprint.entranceWidth());
         var plan = BuildingFloorPlan.create(blueprint,feature,1);
         BiPredicate<Integer,Integer> footprint = (x,z) -> plan.boundaryDistanceAt(x,z) >= 0;
         for (boolean reverse : new boolean[]{false,true}) {
            var blocks = render(blueprint,reverse,footprint,outsideTerrain(blueprint,64));
            assertTrue(BuildingEntranceAccess.place(blueprint,0,511,
               pos -> blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState()),blocks::put));
            assertEntryFromStreet(blueprint,plan,blocks,64);
         }
      }
   }

   @Test
   void thresholdLandingsAndStairsConnectToHigherAndLowerSidewalksInEveryDirection() throws Exception {
      for (Direction facing : Direction.Plane.HORIZONTAL) for (int ground : new int[]{61,62,63,64,65,66,67}) {
         var blueprint = BuildingFloorPlanTest.blueprint(16,14,3,facing,false,-17,-19);
         var plan = BuildingFloorPlan.create(blueprint,(x,z)->true);
         var blocks = render(blueprint,true,(x,z)->true,outsideTerrain(blueprint,ground));
         assertTrue(BuildingEntranceAccess.place(blueprint,0,511,
            pos -> blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState()),blocks::put),facing + " ground=" + ground);
         assertEntryFromStreet(blueprint,plan,blocks,ground);
         if (facing == Direction.NORTH && ground == 61) export("entrance-steps",blocks);
      }
   }

   @Test
   void noWallGlassFurnitureOrTrimCanRefillTheEntryPassageAfterTheDoorIsPlaced() throws Exception {
      var feature = BuildingEntranceLayoutTest.feature(8,new double[]{0,12,16,0,32,12,16,24,0,12});
      var template = BuildingFloorPlanTest.blueprint(32,24,3,Direction.NORTH,false);
      var blueprint = TellusBuildingBlueprints.create("angled",feature,template.profile(),77,63,64,76,79,
         List.of(BuildingEntranceLayoutTest.road(-1000,-8,1000,-8)),1);
      var plan = BuildingFloorPlan.create(blueprint,feature,1);
      for (boolean reverse : new boolean[]{false,true}) {
         var blocks = render(blueprint,reverse,(x,z)->plan.boundaryDistanceAt(x,z)>=0,outsideTerrain(blueprint,64));
         for (int step=0;step<=BuildingEntranceLayout.PASSAGE_DEPTH;step++) {
            int x=blueprint.entranceWorldX()-blueprint.entranceFacing().getStepX()*step;
            int z=blueprint.entranceWorldZ()-blueprint.entranceFacing().getStepZ()*step;
            for (int y=65;y<=66;y++) assertTrue(passable(blocks.get(new BlockPos(x,y,z))),
               "Blocked entrance passage at " + x + "," + y + "," + z);
         }
         assertEntryFromStreet(blueprint,plan,blocks,64);
      }
   }

   @Test
   void stairsNeverOccupyTheGroundFloorEntranceCorridor() throws Exception {
      for (Direction facing : Direction.Plane.HORIZONTAL) {
         var blueprint = BuildingFloorPlanTest.blueprint(8,9,3,facing,false);
         var plan = BuildingFloorPlan.create(blueprint,(x,z)->true);
         assertNotNull(plan.stairs());
         for(int step=1;step<=3;step++) {
            int x=blueprint.entranceWorldX()-facing.getStepX()*step;
            int z=blueprint.entranceWorldZ()-facing.getStepZ()*step;
            assertFalse(plan.stairs().contains(x,z));
         }
      }
   }

   @Test
   void aNarrowEntranceDoesNotCutThroughTheOppositeWall() throws Exception {
      var blueprint = BuildingFloorPlanTest.blueprint(3,8,1,Direction.WEST,false,0,0);
      var blocks = render(blueprint,false);
      assertTrue(passable(blocks.get(new BlockPos(1,65,blueprint.entranceWorldZ()))));
      assertFalse(passable(blocks.get(new BlockPos(2,65,blueprint.entranceWorldZ()))));
      assertFalse(passable(blocks.get(new BlockPos(2,66,blueprint.entranceWorldZ()))));
   }

   @Test
   void exteriorStepsDoNotBridgeWaterOrWriteIntoUnavailableChunks() {
      var blueprint = BuildingFloorPlanTest.blueprint(16,14,3,Direction.NORTH,false);
      for (BlockState obstacle : List.of(Blocks.WATER.defaultBlockState(),Blocks.BARRIER.defaultBlockState())) {
         Map<BlockPos,BlockState> writes = new HashMap<>();
         assertFalse(BuildingEntranceAccess.place(blueprint,0,511,pos->obstacle,writes::put));
         assertTrue(writes.isEmpty());
      }
   }

   private static Map<BlockPos,BlockState> outsideTerrain(BuildingBlueprint blueprint,int ground) {
      Map<BlockPos,BlockState> blocks = new HashMap<>();
      for(int z=blueprint.minWorldZ()-8;z<=blueprint.maxWorldZ()+8;z++) {
         for(int x=blueprint.minWorldX()-8;x<=blueprint.maxWorldX()+8;x++) {
            for(int y=55;y<=ground;y++) blocks.put(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
         }
      }
      return blocks;
   }

   private static void assertEntryFromStreet(BuildingBlueprint blueprint,BuildingFloorPlan plan,
      Map<BlockPos,BlockState> blocks,int ground) {
      BlockPos start=new BlockPos(blueprint.entranceWorldX(),ground+1,blueprint.entranceWorldZ())
         .relative(blueprint.entranceFacing(),BuildingEntranceLayout.APPROACH_LENGTH+1);
      Set<BlockPos> reached=new HashSet<>();
      ArrayDeque<BlockPos> queue=new ArrayDeque<>();
      queue.add(start);reached.add(start);
      while(!queue.isEmpty()) {
         BlockPos from=queue.removeFirst();
         for(Direction d:Direction.Plane.HORIZONTAL) for(int dy=-1;dy<=1;dy++) {
            BlockPos to=from.relative(d).above(dy);
            if(to.getX()<blueprint.minWorldX()-8||to.getX()>blueprint.maxWorldX()+8
               ||to.getZ()<blueprint.minWorldZ()-8||to.getZ()>blueprint.maxWorldZ()+8
               ||to.getY()<Math.min(ground-1,blueprint.floorY()+1)||to.getY()>Math.max(ground+2,blueprint.floorY()+3)) continue;
            BlockState support=blocks.getOrDefault(to.below(),Blocks.AIR.defaultBlockState());
            if(!support.isAir()&&!(support.getBlock() instanceof DoorBlock)
               && playerFits(blocks,to)&&reached.add(to)) queue.add(to);
         }
      }
      BlockPos doorway=new BlockPos(blueprint.entranceWorldX(),blueprint.floorY()+1,blueprint.entranceWorldZ());
      assertTrue(reached.contains(doorway),"Cannot enter from the street: " + blueprint.entranceFacing() + " " + doorway + " ground=" + ground);
      boolean entered=false;
      for(int step=1;step<=3;step++) {
         BlockPos inside=doorway.relative(blueprint.entranceFacing().getOpposite(),step);
         if(plan.boundaryDistanceAt(inside.getX(),inside.getZ())>0&&reached.contains(inside)) entered=true;
      }
      assertTrue(entered,"Door opens into another wall instead of the interior");
   }

   private static boolean playerFits(Map<BlockPos,BlockState> blocks,BlockPos feet) {
      var player=new net.minecraft.world.phys.AABB(feet.getX()+0.2,feet.getY(),feet.getZ()+0.2,
         feet.getX()+0.8,feet.getY()+1.8,feet.getZ()+0.8);
      for(int dy=0;dy<=1;dy++) {
         BlockPos pos=feet.above(dy);
         BlockState state=blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState());
         if(state.getBlock() instanceof DoorBlock) state=state.setValue(BlockStateProperties.OPEN,true);
         for(var shape:state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,pos).toAabbs()) {
            if(shape.move(pos.getX(),pos.getY(),pos.getZ()).intersects(player)) return false;
         }
      }
      return true;
   }

   private static Map<BlockPos, BlockState> render(BuildingBlueprint blueprint, boolean reversed) throws Exception {
      return render(blueprint, reversed, (x, z) -> true);
   }

   private static Map<BlockPos, BlockState> render(BuildingBlueprint blueprint, boolean reversed, BiPredicate<Integer, Integer> footprint) throws Exception {
      return render(blueprint, reversed, footprint, Map.of());
   }

   private static Map<BlockPos, BlockState> render(BuildingBlueprint blueprint, boolean reversed,
      BiPredicate<Integer, Integer> footprint, Map<BlockPos, BlockState> initial) throws Exception {
      BuildingFloorPlan coverage = BuildingFloorPlan.create(blueprint, footprint);
      Map<BlockPos, BlockState> blocks = new HashMap<>(initial);
      WorldGenLevel level = (WorldGenLevel)Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class},
         (proxy, method, args) -> switch (method.getName()) {
            case "setBlock" -> { blocks.put(((BlockPos)args[0]).immutable(), (BlockState)args[1]); yield true; }
            case "isEmptyBlock" -> blocks.getOrDefault(args[0], Blocks.AIR.defaultBlockState()).isAir();
            case "getBlockState" -> blocks.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
            default -> throw new UnsupportedOperationException(method.getName());
         });
      var palette = TellusBuildingMaterials.resolvePalette(blueprint);
      List<BlockPos> chunks = new ArrayList<>();
      for (int z = Math.floorDiv(blueprint.minWorldZ(), 16); z <= Math.floorDiv(blueprint.maxWorldZ(), 16); z++) {
         for (int x = Math.floorDiv(blueprint.minWorldX(), 16); x <= Math.floorDiv(blueprint.maxWorldX(), 16); x++) {
            chunks.add(new BlockPos(x * 16, 0, z * 16));
         }
      }
      if (reversed) Collections.reverse(chunks);
      for (BlockPos chunk : chunks) {
         boolean[] occupied = new boolean[256];
         boolean[] edge = new boolean[256];
         int[] distance = new int[256];
         int[] top = new int[256];
         List<Integer> columns = new ArrayList<>();
         for (int i = 0; i < 256; i++) {
            int x = chunk.getX() + i % 16;
            int z = chunk.getZ() + i / 16;
            if (x < blueprint.minWorldX() || x > blueprint.maxWorldX() || z < blueprint.minWorldZ() || z > blueprint.maxWorldZ()) continue;
            distance[i] = coverage.boundaryDistanceAt(x, z);
            if (distance[i] < 0) continue;
            occupied[i] = true;
            edge[i] = distance[i] == 0;
            top[i] = blueprint.roofTopY(x, z, distance[i]) + (edge[i] ? blueprint.parapetHeight(distance[i]) : 0);
            columns.add(i);
         }
         // Each chunk independently reconstructs the same whole-footprint plan.
         var interior = new TellusBuildingInteriors(BuildingFloorPlan.create(blueprint, footprint));
         Object feature = preparedFeature.newInstance(occupied, columns.stream().mapToInt(Integer::intValue).toArray(), edge, distance, top, blueprint, true, interior);
         if (reversed) Collections.reverse(columns);
         for (int i : columns) {
            placeColumn.invoke(generator, level, new BlockPos.MutableBlockPos(), 2, feature, palette,
               chunk.getX() + i % 16, chunk.getZ() + i / 16, i, top[i], chunk.getX(), chunk.getZ(), 0, 511);
         }
      }
      return blocks;
   }

   private static void assertAccessible(BuildingBlueprint blueprint, Map<BlockPos, BlockState> blocks) {
      assertAccessible(blueprint, blocks, BuildingFloorPlan.create(blueprint, (x, z) -> true));
   }

   private static void assertAccessible(BuildingBlueprint blueprint, Map<BlockPos, BlockState> blocks, BuildingFloorPlan plan) {
      BlockPos entrance = new BlockPos(blueprint.entranceWorldX(), blueprint.floorY() + 1, blueprint.entranceWorldZ());
      Set<BlockPos> reachable = new HashSet<>();
      ArrayDeque<BlockPos> queue = new ArrayDeque<>();
      queue.add(entrance);
      reachable.add(entrance);
      while (!queue.isEmpty()) {
         BlockPos from = queue.removeFirst();
         for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
               BlockPos to = from.relative(direction).above(dy);
               if (to.getX() < blueprint.minWorldX() || to.getX() > blueprint.maxWorldX()
                  || to.getZ() < blueprint.minWorldZ() || to.getZ() > blueprint.maxWorldZ()
                  || to.getY() <= blueprint.floorY() || to.getY() > blueprint.roofBaseY()) continue;
               BlockState below = blocks.getOrDefault(to.below(), Blocks.AIR.defaultBlockState());
               if (!below.isAir() && !(below.getBlock() instanceof DoorBlock)
                  && passable(blocks.get(to)) && passable(blocks.get(to.above())) && reachable.add(to)) {
                  queue.add(to);
               }
            }
         }
      }
      for (int floorIndex = 0; floorIndex < blueprint.floorCount(); floorIndex++) {
         int y = blueprint.floorBottomY(floorIndex) + 1;
         final int floorY = y;
         assertTrue(reachable.stream().anyMatch(p -> p.getY() == floorY), "Unreachable storey " + floorIndex);
         var floor = plan.floor(floorIndex);
         for (int i = 0; i < floor.doors().length; i++) {
            if (floor.doors()[i] != null) {
               assertTrue(reachable.contains(new BlockPos(plan.worldX(i), y, plan.worldZ(i))),
                  "Unreachable room on storey " + floorIndex + BuildingFloorPlanTest.describe(plan, floorIndex));
            }
         }
      }
   }

   private static void assertClearWindows(BuildingBlueprint blueprint, Map<BlockPos, BlockState> blocks) {
      for (int floor = 0; floor < blueprint.floorCount(); floor++) {
         int inset = blueprint.setbackForFloor(floor);
         int y = blueprint.floorBottomY(floor) + 2;
         for (int x = blueprint.minWorldX() + inset; x <= blueprint.maxWorldX() - inset; x++) {
            for (int z = blueprint.minWorldZ() + inset; z <= blueprint.maxWorldZ() - inset; z++) {
               if (!TellusBuildingArchitecture.window(blueprint, boundary(blueprint, x, z), x, z, floor, y)) continue;
               Direction outward = x == blueprint.minWorldX() + inset ? Direction.WEST
                  : x == blueprint.maxWorldX() - inset ? Direction.EAST
                  : z == blueprint.minWorldZ() + inset ? Direction.NORTH : Direction.SOUTH;
               BlockPos outside = new BlockPos(x, y, z).relative(outward);
               assertTrue(passable(blocks.get(outside)), "Decoration blocks window at " + outside);
            }
         }
      }
   }

   private static boolean passable(BlockState state) {
      return state == null || state.isAir() || state.getBlock() instanceof DoorBlock;
   }

   private static int boundary(BuildingBlueprint blueprint, int x, int z) {
      return Math.min(Math.min(x - blueprint.minWorldX(), blueprint.maxWorldX() - x), Math.min(z - blueprint.minWorldZ(), blueprint.maxWorldZ() - z));
   }

   private static void export(String name, Map<BlockPos, BlockState> blocks) throws Exception {
      String output = System.getenv("TELLUS_BUILDING_PREVIEW_DIR");
      if (output == null) return;
      Path path = Path.of(output);
      Files.createDirectories(path);
      List<String> rows = new ArrayList<>();
      for (var entry : blocks.entrySet()) {
         if (!entry.getValue().isAir()) {
            BlockPos p = entry.getKey();
            rows.add(p.getX() + "\t" + p.getY() + "\t" + p.getZ() + "\t" + entry.getValue());
         }
      }
      Files.write(path.resolve(name + ".tsv"), rows);
   }
}
