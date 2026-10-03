package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.biome.BiomeClassification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

final class RandomBiomeMixer {
   private static final int PATCH_GRID_BLOCKS = 512;
   private static final long LAND_PATCH_SALT = -4348849565147123417L;
   private static final long LAND_PICK_SALT = 7910913083456271721L;
   private static final long OCEAN_PATCH_SALT = 2568302803750997685L;
   private static final long OCEAN_PICK_SALT = -5823276844928919027L;
   private static final long RIVER_PATCH_SALT = 3937316899203929053L;
   private static final long RIVER_PICK_SALT = -8197131963272981473L;
   private static final long CAVE_PATCH_SALT = 6489672811367659937L;
   private static final long CAVE_PICK_SALT = -2982849728131767649L;
   private static final long POOL_PICK_SALT = -7512562818295870179L;
   private static final double SPECIAL_LAND_CHANCE = 0.18;
   private static final double ALL_LAND_CHANCE = 0.12;
   private static final double HASH_UNIT_SCALE = 0x1.0p-53;

   private final double density;
   private final long seed;
   private final List<Holder<Biome>> allLand;
   private final List<Holder<Biome>> temperateLand;
   private final List<Holder<Biome>> tropicalLand;
   private final List<Holder<Biome>> dryLand;
   private final List<Holder<Biome>> coldLand;
   private final List<Holder<Biome>> specialLand;
   private final List<Holder<Biome>> shallowOceans;
   private final List<Holder<Biome>> deepOceans;
   private final List<Holder<Biome>> rivers;
   private final List<Holder<Biome>> caves;

   RandomBiomeMixer(HolderGetter<Biome> biomeLookup, EarthGeneratorSettings settings) {
      Objects.requireNonNull(biomeLookup, "biomeLookup");
      EarthGeneratorSettings safeSettings = Objects.requireNonNull(settings, "settings");
      this.density = Mth.clamp(safeSettings.randomBiomeDensity(), 0.0, 0.4);
      this.seed = safeSettings.randomBiomeSeed();
      Set<ResourceKey<Biome>> selectedKeys = selectedBiomeKeys(safeSettings.randomBiomeIds());
      RandomBiomeMixer.Pools pools = filterPools(buildPools(biomeLookup, selectedKeys), selectedKeys);
      this.allLand = pools.allLand();
      this.temperateLand = pools.temperateLand();
      this.tropicalLand = pools.tropicalLand();
      this.dryLand = pools.dryLand();
      this.coldLand = pools.coldLand();
      this.specialLand = pools.specialLand();
      this.shallowOceans = pools.shallowOceans();
      this.deepOceans = pools.deepOceans();
      this.rivers = pools.rivers();
      this.caves = pools.caves();
   }

   boolean enabled() {
      return this.density > 0.0;
   }

   static boolean isLandPatchActive(EarthGeneratorSettings settings, int blockX, int blockZ) {
      EarthGeneratorSettings safeSettings = Objects.requireNonNull(settings, "settings");
      if (!RandomBiomeCatalog.hasLandBiomeSelection(safeSettings.randomBiomeIds())) {
         return false;
      }
      double density = Mth.clamp(safeSettings.randomBiomeDensity(), 0.0, 0.4);
      return isPatchActive(density, safeSettings.randomBiomeSeed(), blockX, blockZ, LAND_PATCH_SALT);
   }

   Holder<Biome> mixLand(Holder<Biome> base, int blockX, int blockZ, String koppen) {
      Holder<Biome> safeBase = Objects.requireNonNull(base, "baseBiome");
      if (!this.enabled() || this.allLand.isEmpty() || !this.isPatchActive(blockX, blockZ, LAND_PATCH_SALT)) {
         return safeBase;
      }

      double roll = hashToUnit(patchSeed(blockX, blockZ, this.seed ^ LAND_PICK_SALT));
      List<Holder<Biome>> pool;
      if (roll < SPECIAL_LAND_CHANCE && !this.specialLand.isEmpty()) {
         pool = this.specialLand;
      } else if (roll < SPECIAL_LAND_CHANCE + ALL_LAND_CHANCE) {
         pool = this.allLand;
      } else {
         pool = this.selectClimatePool(safeBase, koppen);
      }

      return this.pick(pool.isEmpty() ? this.allLand : pool, blockX, blockZ, POOL_PICK_SALT, safeBase);
   }

   Holder<Biome> mixOcean(Holder<Biome> base, int blockX, int blockZ, boolean deep) {
      Holder<Biome> safeBase = Objects.requireNonNull(base, "baseBiome");
      if (!this.enabled() || !this.isPatchActive(blockX, blockZ, OCEAN_PATCH_SALT)) {
         return safeBase;
      }

      List<Holder<Biome>> pool = deep ? this.deepOceans : this.shallowOceans;
      return this.pick(pool, blockX, blockZ, OCEAN_PICK_SALT, safeBase);
   }

   Holder<Biome> mixRiver(Holder<Biome> base, int blockX, int blockZ) {
      Holder<Biome> safeBase = Objects.requireNonNull(base, "baseBiome");
      if (!this.enabled() || !this.isPatchActive(blockX, blockZ, RIVER_PATCH_SALT)) {
         return safeBase;
      }
      return this.pick(this.rivers, blockX, blockZ, RIVER_PICK_SALT, safeBase);
   }

   Holder<Biome> mixCave(Holder<Biome> base, int blockX, int blockY, int blockZ) {
      Holder<Biome> safeBase = Objects.requireNonNull(base, "baseBiome");
      if (!this.enabled() || !this.isPatchActive(blockX, blockZ, CAVE_PATCH_SALT)) {
         return safeBase;
      }
      return this.pick(this.caves, blockX, blockZ, CAVE_PICK_SALT ^ Math.floorDiv(blockY, 64), safeBase);
   }

   List<Holder<Biome>> possibleBiomes() {
      List<Holder<Biome>> holders = new ArrayList<>();
      addUnique(holders, this.allLand);
      addUnique(holders, this.shallowOceans);
      addUnique(holders, this.deepOceans);
      addUnique(holders, this.rivers);
      addUnique(holders, this.caves);
      return List.copyOf(holders);
   }

   private List<Holder<Biome>> selectClimatePool(Holder<Biome> base, String koppen) {
      String normalized = koppen == null ? "" : koppen.toUpperCase(Locale.ROOT);
      if (normalized.startsWith("A") || isTropicalBase(base)) {
         return this.tropicalLand;
      } else if (normalized.startsWith("B") || isDryBase(base)) {
         return this.dryLand;
      } else if (normalized.startsWith("D") || normalized.startsWith("E") || isColdBase(base)) {
         return this.coldLand;
      } else {
         return this.temperateLand;
      }
   }

   private boolean isPatchActive(int blockX, int blockZ, long salt) {
      return isPatchActive(this.density, this.seed, blockX, blockZ, salt);
   }

   private static boolean isPatchActive(double density, long seed, int blockX, int blockZ, long salt) {
      if (density <= 0.0) {
         return false;
      }

      double noise = sampleValueNoise(blockX, blockZ, PATCH_GRID_BLOCKS, seed ^ salt);
      return noise >= 1.0 - density;
   }

   private Holder<Biome> pick(List<Holder<Biome>> pool, int blockX, int blockZ, long salt, Holder<Biome> fallback) {
      if (pool.isEmpty()) {
         return fallback;
      }

      long sample = patchSeed(blockX, blockZ, this.seed ^ salt);
      int index = Math.floorMod((int)(sample ^ sample >>> 32), pool.size());
      return pool.get(index);
   }

   private static RandomBiomeMixer.Pools buildPools(HolderGetter<Biome> biomeLookup, Set<ResourceKey<Biome>> selectedKeys) {
      List<Holder<Biome>> allLand = new ArrayList<>();
      List<Holder<Biome>> temperate = new ArrayList<>();
      List<Holder<Biome>> tropical = new ArrayList<>();
      List<Holder<Biome>> dry = new ArrayList<>();
      List<Holder<Biome>> cold = new ArrayList<>();
      List<Holder<Biome>> special = new ArrayList<>();
      List<Holder<Biome>> shallowOceans = new ArrayList<>();
      List<Holder<Biome>> deepOceans = new ArrayList<>();
      List<Holder<Biome>> rivers = new ArrayList<>();
      List<Holder<Biome>> caves = new ArrayList<>();

      addLand(biomeLookup, Biomes.PLAINS, allLand, temperate, tropical, dry);
      addLand(biomeLookup, Biomes.SUNFLOWER_PLAINS, allLand, temperate, dry);
      addLand(biomeLookup, Biomes.MEADOW, allLand, temperate, cold);
      addLand(biomeLookup, Biomes.FOREST, allLand, temperate, tropical);
      addLand(biomeLookup, Biomes.FLOWER_FOREST, allLand, temperate, tropical, special);
      addLand(biomeLookup, Biomes.BIRCH_FOREST, allLand, temperate);
      addLand(biomeLookup, Biomes.OLD_GROWTH_BIRCH_FOREST, allLand, temperate);
      addLand(biomeLookup, Biomes.DARK_FOREST, allLand, temperate, special);
      addLand(biomeLookup, Biomes.CHERRY_GROVE, allLand, temperate, tropical, special);
      addLandByIdIfSelected(biomeLookup, "pale_garden", selectedKeys, allLand, temperate, special);
      addLand(biomeLookup, Biomes.SWAMP, allLand, temperate, tropical, special);
      addLand(biomeLookup, Biomes.MANGROVE_SWAMP, allLand, tropical, special);
      addLand(biomeLookup, Biomes.JUNGLE, allLand, tropical, special);
      addLand(biomeLookup, Biomes.SPARSE_JUNGLE, allLand, tropical);
      addLand(biomeLookup, Biomes.BAMBOO_JUNGLE, allLand, tropical, special);
      addLand(biomeLookup, Biomes.SAVANNA, allLand, tropical, dry);
      addLand(biomeLookup, Biomes.SAVANNA_PLATEAU, allLand, tropical, dry);
      addLand(biomeLookup, Biomes.WINDSWEPT_SAVANNA, allLand, dry, special);
      addLand(biomeLookup, Biomes.DESERT, allLand, dry, special);
      addLand(biomeLookup, Biomes.BADLANDS, allLand, dry, special);
      addLand(biomeLookup, Biomes.WOODED_BADLANDS, allLand, dry, special);
      addLand(biomeLookup, Biomes.ERODED_BADLANDS, allLand, dry, special);
      addLand(biomeLookup, Biomes.TAIGA, allLand, temperate, cold);
      addLand(biomeLookup, Biomes.OLD_GROWTH_PINE_TAIGA, allLand, temperate, cold, special);
      addLand(biomeLookup, Biomes.OLD_GROWTH_SPRUCE_TAIGA, allLand, temperate, cold, special);
      addLand(biomeLookup, Biomes.SNOWY_TAIGA, allLand, cold, special);
      addLand(biomeLookup, Biomes.SNOWY_PLAINS, allLand, cold);
      addLand(biomeLookup, Biomes.ICE_SPIKES, allLand, cold, special);
      addLand(biomeLookup, Biomes.GROVE, allLand, cold, special);
      addLand(biomeLookup, Biomes.SNOWY_SLOPES, allLand, cold);
      addLand(biomeLookup, Biomes.FROZEN_PEAKS, allLand, cold);
      addLand(biomeLookup, Biomes.JAGGED_PEAKS, allLand, cold, special);
      addLand(biomeLookup, Biomes.STONY_PEAKS, allLand, temperate, dry, cold);
      addLand(biomeLookup, Biomes.WINDSWEPT_HILLS, allLand, temperate, cold);
      addLand(biomeLookup, Biomes.WINDSWEPT_FOREST, allLand, temperate, cold);
      addLand(biomeLookup, Biomes.WINDSWEPT_GRAVELLY_HILLS, allLand, temperate, dry, cold);
      addLand(biomeLookup, Biomes.STONY_SHORE, allLand, temperate, dry, cold);
      addLand(biomeLookup, Biomes.BEACH, allLand, temperate, tropical, dry);
      addLand(biomeLookup, Biomes.SNOWY_BEACH, allLand, cold);
      addLand(biomeLookup, Biomes.MUSHROOM_FIELDS, allLand, temperate, tropical, cold, special);

      addOcean(biomeLookup, Biomes.OCEAN, shallowOceans);
      addOcean(biomeLookup, Biomes.WARM_OCEAN, shallowOceans);
      addOcean(biomeLookup, Biomes.LUKEWARM_OCEAN, shallowOceans);
      addOcean(biomeLookup, Biomes.COLD_OCEAN, shallowOceans);
      addOcean(biomeLookup, Biomes.FROZEN_OCEAN, shallowOceans);
      addOcean(biomeLookup, Biomes.DEEP_OCEAN, deepOceans);
      addOcean(biomeLookup, Biomes.DEEP_LUKEWARM_OCEAN, deepOceans);
      addOcean(biomeLookup, Biomes.DEEP_COLD_OCEAN, deepOceans);
      addOcean(biomeLookup, Biomes.DEEP_FROZEN_OCEAN, deepOceans);

      addOcean(biomeLookup, Biomes.RIVER, rivers);
      addOcean(biomeLookup, Biomes.FROZEN_RIVER, rivers);

      addOcean(biomeLookup, Biomes.LUSH_CAVES, caves);
      addOcean(biomeLookup, Biomes.DRIPSTONE_CAVES, caves);
      addOcean(biomeLookup, Biomes.DEEP_DARK, caves);
      addOceanByIdIfSelected(biomeLookup, "sulfur_caves", selectedKeys, caves);

      return new RandomBiomeMixer.Pools(
         List.copyOf(allLand),
         List.copyOf(temperate),
         List.copyOf(tropical),
         List.copyOf(dry),
         List.copyOf(cold),
         List.copyOf(special),
         List.copyOf(shallowOceans),
         List.copyOf(deepOceans),
         List.copyOf(rivers),
         List.copyOf(caves)
      );
   }

   private static Set<ResourceKey<Biome>> selectedBiomeKeys(List<String> biomeIds) {
      Set<ResourceKey<Biome>> keys = new HashSet<>();
      for (String biomeId : RandomBiomeCatalog.normalizeSelection(biomeIds)) {
         keys.add(BiomeClassification.toBiomeKey(biomeId));
      }
      return Set.copyOf(keys);
   }

   private static RandomBiomeMixer.Pools filterPools(RandomBiomeMixer.Pools pools, Set<ResourceKey<Biome>> selectedKeys) {
      return new RandomBiomeMixer.Pools(
         filterSelected(pools.allLand(), selectedKeys),
         filterSelected(pools.temperateLand(), selectedKeys),
         filterSelected(pools.tropicalLand(), selectedKeys),
         filterSelected(pools.dryLand(), selectedKeys),
         filterSelected(pools.coldLand(), selectedKeys),
         filterSelected(pools.specialLand(), selectedKeys),
         filterSelected(pools.shallowOceans(), selectedKeys),
         filterSelected(pools.deepOceans(), selectedKeys),
         filterSelected(pools.rivers(), selectedKeys),
         filterSelected(pools.caves(), selectedKeys)
      );
   }

   private static List<Holder<Biome>> filterSelected(List<Holder<Biome>> biomes, Set<ResourceKey<Biome>> selectedKeys) {
      if (selectedKeys.isEmpty()) {
         return List.of();
      }

      List<Holder<Biome>> result = new ArrayList<>();
      for (Holder<Biome> biome : biomes) {
         if (selectedKeys.stream().anyMatch(biome::is)) {
            result.add(biome);
         }
      }
      return List.copyOf(result);
   }

   @SafeVarargs
   private static void addLand(HolderGetter<Biome> lookup, ResourceKey<Biome> key, List<Holder<Biome>> all, List<Holder<Biome>>... pools) {
      resolveOptional(lookup, key).ifPresent(biome -> {
         addUnique(all, biome);
         for (List<Holder<Biome>> pool : pools) {
            addUnique(pool, biome);
         }
      });
   }

   @SafeVarargs
   private static void addLandByIdIfSelected(
      HolderGetter<Biome> lookup,
      String id,
      Set<ResourceKey<Biome>> selectedKeys,
      List<Holder<Biome>> all,
      List<Holder<Biome>>... pools
   ) {
      ResourceKey<Biome> key = BiomeClassification.toBiomeKey(id);
      if (selectedKeys.contains(key)) {
         addLand(lookup, key, all, pools);
      }
   }

   private static void addOcean(HolderGetter<Biome> lookup, ResourceKey<Biome> key, List<Holder<Biome>> pool) {
      resolveOptional(lookup, key).ifPresent(biome -> addUnique(pool, biome));
   }

   static void addOceanByIdIfSelected(
      HolderGetter<Biome> lookup, String id, Set<ResourceKey<Biome>> selectedKeys, List<Holder<Biome>> pool
   ) {
      ResourceKey<Biome> key = BiomeClassification.toBiomeKey(id);
      if (selectedKeys.contains(key)) {
         addOcean(lookup, key, pool);
      }
   }

   private static java.util.Optional<Holder<Biome>> resolveOptional(HolderGetter<Biome> lookup, ResourceKey<Biome> key) {
      return lookup.get(key).map(holder -> (Holder<Biome>)holder);
   }

   private static void addUnique(List<Holder<Biome>> holders, Holder<Biome> biome) {
      if (biome != null && !holders.contains(biome)) {
         holders.add(biome);
      }
   }

   private static void addUnique(List<Holder<Biome>> holders, List<Holder<Biome>> biomes) {
      for (Holder<Biome> biome : biomes) {
         addUnique(holders, biome);
      }
   }

   private static boolean isTropicalBase(Holder<Biome> biome) {
      return biome.is(Biomes.JUNGLE)
         || biome.is(Biomes.SPARSE_JUNGLE)
         || biome.is(Biomes.BAMBOO_JUNGLE)
         || biome.is(Biomes.MANGROVE_SWAMP)
         || biome.is(Biomes.SAVANNA)
         || biome.is(Biomes.SAVANNA_PLATEAU);
   }

   private static boolean isDryBase(Holder<Biome> biome) {
      return biome.is(Biomes.DESERT)
         || biome.is(Biomes.BADLANDS)
         || biome.is(Biomes.WOODED_BADLANDS)
         || biome.is(Biomes.ERODED_BADLANDS)
         || biome.is(Biomes.SAVANNA)
         || biome.is(Biomes.SAVANNA_PLATEAU)
         || biome.is(Biomes.WINDSWEPT_SAVANNA);
   }

   private static boolean isColdBase(Holder<Biome> biome) {
      return biome.is(Biomes.SNOWY_PLAINS)
         || biome.is(Biomes.ICE_SPIKES)
         || biome.is(Biomes.SNOWY_TAIGA)
         || biome.is(Biomes.GROVE)
         || biome.is(Biomes.SNOWY_SLOPES)
         || biome.is(Biomes.FROZEN_PEAKS)
         || biome.is(Biomes.JAGGED_PEAKS);
   }

   private static double sampleValueNoise(int blockX, int blockZ, int grid, long salt) {
      int gridX = Math.floorDiv(blockX, grid);
      int gridZ = Math.floorDiv(blockZ, grid);
      double fracX = Math.floorMod(blockX, grid) / (double)grid;
      double fracZ = Math.floorMod(blockZ, grid) / (double)grid;
      double sx = smoothstep(fracX);
      double sz = smoothstep(fracZ);
      double n00 = gridNoise(gridX, gridZ, salt);
      double n10 = gridNoise(gridX + 1, gridZ, salt);
      double n01 = gridNoise(gridX, gridZ + 1, salt);
      double n11 = gridNoise(gridX + 1, gridZ + 1, salt);
      double nx0 = Mth.lerp(sx, n00, n10);
      double nx1 = Mth.lerp(sx, n01, n11);
      return Mth.lerp(sz, nx0, nx1);
   }

   private static double gridNoise(int gridX, int gridZ, long salt) {
      return hashToUnit(gridSeed(gridX, gridZ, salt));
   }

   private static long patchSeed(int blockX, int blockZ, long salt) {
      return gridSeed(Math.floorDiv(blockX, PATCH_GRID_BLOCKS), Math.floorDiv(blockZ, PATCH_GRID_BLOCKS), salt);
   }

   private static long gridSeed(int gridX, int gridZ, long salt) {
      return gridX * 341873128712L + gridZ * 132897987541L + salt;
   }

   private static double smoothstep(double value) {
      return value * value * (3.0 - 2.0 * value);
   }

   private static double hashToUnit(long seed) {
      seed ^= seed >>> 33;
      seed *= -49064778989728563L;
      seed ^= seed >>> 33;
      seed *= -4265267296055464877L;
      seed ^= seed >>> 33;
      return (seed >>> 11) * HASH_UNIT_SCALE;
   }

   private record Pools(
      List<Holder<Biome>> allLand,
      List<Holder<Biome>> temperateLand,
      List<Holder<Biome>> tropicalLand,
      List<Holder<Biome>> dryLand,
      List<Holder<Biome>> coldLand,
      List<Holder<Biome>> specialLand,
      List<Holder<Biome>> shallowOceans,
      List<Holder<Biome>> deepOceans,
      List<Holder<Biome>> rivers,
      List<Holder<Biome>> caves
   ) {
   }
}
