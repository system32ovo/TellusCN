package com.yucareux.tellus.worldgen.road;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainNetworkPolicy;
import com.yucareux.tellus.world.data.osm.RoadMode;
import com.yucareux.tellus.worldgen.EarthChunkGenerator;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StreetLightRenderingTest {
   @TempDir static Path directory;
   private static EarthChunkGenerator generator;
   private static Method prepare;
   private static Object widths;

   @BeforeAll
   static void setup() throws Exception {
      assumeFalse(StreetLightRenderingTest.class.getClassLoader().getResource("net/minecraftforge/fml/ModList.class") != null,
         "Forge's raw JUnit bootstrap lacks ModLauncher; Fabric 1.20.1 exercises the same shared generator");
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      String gameDir = System.getProperty("tellus.gameDir");
      String configDir = System.getProperty("tellus.configDir");
      try {
         System.setProperty("tellus.gameDir", directory.toString());
         System.setProperty("tellus.configDir", directory.resolve("config").toString());
         var components = EarthGeneratorSettings.class.getRecordComponents();
         Class<?>[] types = new Class<?>[components.length];
         Object[] values = new Object[components.length];
         for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = components[i].getName().equals("worldScale") ? 1.0 : components[i].getAccessor().invoke(EarthGeneratorSettings.DEFAULT);
         }
         var settings = EarthGeneratorSettings.class.getDeclaredConstructor(types).newInstance(values);
         var biome = VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
         try (var ignored = ManagedTerrainNetworkPolicy.cacheOnly()) {
            generator = new EarthChunkGenerator(new FixedBiomeSource(biome), settings);
         }
         prepare = Arrays.stream(EarthChunkGenerator.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("prepareRoadLightsForChunk")).findFirst().orElseThrow();
         prepare.setAccessible(true);
         Constructor<?> constructor = prepare.getParameterTypes()[3].getDeclaredConstructors()[0];
         constructor.setAccessible(true);
         widths = constructor.newInstance(6, 4, 2);
      } finally {
         if (gameDir == null) System.clearProperty("tellus.gameDir"); else System.setProperty("tellus.gameDir", gameDir);
         if (configDir == null) System.clearProperty("tellus.configDir"); else System.setProperty("tellus.configDir", configDir);
      }
   }

   @Test
   void allFamiliesAreConnectedLitAndKeepHeadroomUnderTheArm() throws Exception {
      for (var style : StreetLightStyle.values()) for (var facing : Direction.Plane.HORIZONTAL) {
         var lamp = lamp(style, facing, 15, -16);
         Map<BlockPos, BlockState> blocks = new HashMap<>();
         assertTrue(StreetLightModel.place(lamp, 63, -64, 319, terrain(blocks, 63), blocks::put), style + " " + facing);
         BlockPos mast = new BlockPos(15, 63, -16);
         for (int y = 1; y < lamp.height(); y++) assertNotNull(blocks.get(mast.above(y)), "Gap in pole");
         BlockPos head = mast.above(lamp.height()).relative(facing, lamp.arm());
         assertTrue(blocks.get(head).is(Blocks.SEA_LANTERN));
         assertTrue(blocks.get(head.above()).is(Blocks.IRON_TRAPDOOR));
         for (var part : blocks.entrySet()) {
            assertFalse(part.getValue().is(Blocks.OAK_FENCE) || part.getValue().is(Blocks.GLOWSTONE));
            if (part.getKey().getX() != mast.getX() || part.getKey().getZ() != mast.getZ())
               assertTrue(part.getKey().getY() - 63 >= lamp.height());
         }
         if (facing == Direction.EAST) export(style, blocks);
      }
   }

   @Test
   void anyObstructionIncludingAnArmAcrossAChunkBorderRejectsTheEntireFixture() {
      var lamp = lamp(StreetLightStyle.BOULEVARD, Direction.EAST, 15, 8);
      for (var part : StreetLightModel.parts(lamp, 63)) {
         Map<BlockPos, BlockState> existing = new HashMap<>();
         existing.put(part.position(), Blocks.STONE.defaultBlockState());
         Map<BlockPos, BlockState> writes = new HashMap<>();
         assertFalse(StreetLightModel.place(lamp, 63, -64, 319, terrain(existing, 63), writes::put), part.toString());
         assertTrue(writes.isEmpty(), "No partial fixture may remain");
         assertTrue(existing.get(part.position()).is(Blocks.STONE));
      }
   }

   @Test
   void waterMissingGroundAndDoorwayApproachesAreRejected() {
      var lamp = lamp(StreetLightStyle.RESIDENTIAL, Direction.NORTH, 3, 5);
      BlockPos ground = new BlockPos(3, 63, 5);
      for (BlockPos obstacle : List.of(ground, ground.above(), ground.north().above(), ground.east().above(2))) {
         for (BlockState state : List.of(Blocks.WATER.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState())) {
            Map<BlockPos, BlockState> existing = new HashMap<>();
            existing.put(obstacle, state);
            Map<BlockPos, BlockState> writes = new HashMap<>();
            assertFalse(StreetLightModel.place(lamp, 63, -64, 319, terrain(existing, 63), writes::put));
            assertTrue(writes.isEmpty());
         }
      }
      Map<BlockPos, BlockState> writes = new HashMap<>();
      assertFalse(StreetLightModel.place(lamp, 63, -64, 319, pos -> Blocks.AIR.defaultBlockState(), writes::put));
      assertTrue(writes.isEmpty());
   }

   @Test
   void raisedSidewalksStayGroundedAndHeightLimitNeverProducesTruncatedPoles() {
      var lamp = lamp(StreetLightStyle.RESIDENTIAL, Direction.NORTH, 3, 5);
      Map<BlockPos, BlockState> writes = new HashMap<>();
      assertTrue(StreetLightModel.place(lamp, 63, -64, 319, terrain(writes, 65), writes::put));
      assertTrue(writes.get(new BlockPos(3, 66, 5)).is(Blocks.POLISHED_DEEPSLATE_WALL));
      assertFalse(StreetLightModel.place(lamp, 318, -64, 319, terrain(new HashMap<>(), 318), (p, s) -> fail("Above build limit")));
      assertFalse(StreetLightModel.place(lamp, 63, -64, 319,
         p -> p.getX() == 3 && p.getZ() == 5 && p.getY() <= 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
         (p, s) -> fail("On an isolated column")));
   }

   @Test
   void generatorPreparesOffRoadPolesAndVetoesRoadWaterBuildingAndBridgeMasks() throws Exception {
      var road = StreetLightPlannerTest.road(1, "residential", RoadMode.NORMAL, 7, -200, 0, 200, 0);
      byte[] roadMask = new byte[256];
      for (int z = 0; z <= 3; z++) for (int x = 0; x < 16; x++) roadMask[z * 16 + x] = 2;
      int[] terrain = new int[256];
      Arrays.fill(terrain, 63);
      int[] water = terrain.clone();
      boolean[] waterFlags = new boolean[256];
      boolean[] shafts = new boolean[256];
      int[] shaftBottom = new int[256];
      int[] shaftTop = new int[256];
      Object[] args = {new ChunkPos(0, 0), List.of(road), List.of(), widths, roadMask, terrain, water, waterFlags,
         shafts, shaftBottom, shaftTop, new boolean[256], new int[256], new int[256], null};
      Object prepared = prepare.invoke(generator, args);
      assertNotNull(prepared);
      Method lights = prepared.getClass().getDeclaredMethod("lights");
      lights.setAccessible(true);
      List<?> fixtures = (List<?>)lights.invoke(prepared);
      assertEquals(1, fixtures.size());
      Method getLamp = fixtures.get(0).getClass().getDeclaredMethod("lamp");
      getLamp.setAccessible(true);
      var lamp = (StreetLightPlanner.Lamp)getLamp.invoke(fixtures.get(0));
      int index = lamp.worldZ() * 16 + lamp.worldX();
      assertEquals(0, roadMask[index]);
      roadMask[index] = 2;
      assertNull(prepare.invoke(generator, args));
      roadMask[index] = 0;
      waterFlags[index] = true;
      water[index] = 65;
      assertNull(prepare.invoke(generator, args));
      waterFlags[index] = false;
      shafts[index] = true;
      shaftBottom[index] = 63;
      shaftTop[index] = 100;
      assertNull(prepare.invoke(generator, args));
      shafts[index] = false;
      Constructor<?> buildings = prepare.getParameterTypes()[14].getDeclaredConstructors()[0];
      buildings.setAccessible(true);
      Object buildingMask = buildings.newInstance();
      Method addSpan = buildingMask.getClass().getDeclaredMethod("addSpan", int.class, int.class, int.class);
      addSpan.setAccessible(true);
      addSpan.invoke(buildingMask, index + 1, 63, 90);
      args[14] = buildingMask;
      assertNull(prepare.invoke(generator, args), "Keep room between the mast and a neighboring building");
   }

   private static StreetLightPlanner.Lamp lamp(StreetLightStyle style, Direction facing, int x, int z) {
      return new StreetLightPlanner.Lamp(1, 1, x, z, x + facing.getStepX() * 5, z + facing.getStepZ() * 5,
         facing, style, style.height(1), style.arm(1), style.spacing(1), false);
   }

   private static Function<BlockPos, BlockState> terrain(Map<BlockPos, BlockState> blocks, int surface) {
      return pos -> blocks.getOrDefault(pos, pos.getY() <= surface ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
   }

   private static void export(StreetLightStyle style, Map<BlockPos, BlockState> blocks) throws Exception {
      String path = System.getenv("TELLUS_LIGHT_PREVIEW_DIR");
      if (path == null) return;
      Path destination = Path.of(path);
      Files.createDirectories(destination);
      List<String> rows = new ArrayList<>();
      rows.add("x\ty\tz\tstate");
      for (var part : blocks.entrySet()) rows.add(part.getKey().getX() + "\t" + (part.getKey().getY() - 63) + "\t"
         + part.getKey().getZ() + "\t" + part.getValue());
      Files.write(destination.resolve(style.name().toLowerCase() + ".tsv"), rows);
   }
}
