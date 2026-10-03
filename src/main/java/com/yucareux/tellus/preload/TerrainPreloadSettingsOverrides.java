package com.yucareux.tellus.preload;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public record TerrainPreloadSettingsOverrides(
   double worldScale,
   boolean caveGeneration,
   boolean oreDistribution,
   boolean geologicalStonePatches,
   boolean addStructures,
   boolean enableRoads,
   boolean enableBuildings,
   boolean enableWater,
   boolean thinShellTerrain,
   boolean experimentalIncreaseHeight,
   int undergroundDepth
) {
   public static TerrainPreloadSettingsOverrides from(EarthGeneratorSettings settings) {
      Objects.requireNonNull(settings, "settings");
      return new TerrainPreloadSettingsOverrides(
         settings.worldScale(),
         settings.caveGeneration(),
         settings.oreDistribution(),
         settings.geologicalStonePatches(),
         structuresEnabled(settings),
         settings.enableRoads(),
         settings.enableBuildings(),
         settings.enableWater(),
         settings.thinShellTerrain(),
         settings.experimentalIncreaseHeight(),
         settings.undergroundDepth()
      );
   }

   public EarthGeneratorSettings apply(EarthGeneratorSettings base) {
      Objects.requireNonNull(base, "base");
      return this.apply(base, base.spawnLatitude(), base.spawnLongitude());
   }

   public EarthGeneratorSettings apply(EarthGeneratorSettings base, double spawnLatitude, double spawnLongitude) {
      Objects.requireNonNull(base, "base");
      AtomicReference<String> encodeError = new AtomicReference<>();
      JsonElement encoded = EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, base)
         .resultOrPartial(encodeError::set)
         .orElse(null);
      if (encodeError.get() != null || !(encoded instanceof JsonObject object)) {
         throw new IllegalStateException("Unable to preserve the current world settings for terrain preload: " + errorDetail(encodeError.get()));
      }

      double safeSpawnLatitude = Double.isFinite(spawnLatitude) ? spawnLatitude : base.spawnLatitude();
      double safeSpawnLongitude = Double.isFinite(spawnLongitude) ? spawnLongitude : base.spawnLongitude();
      object.addProperty("world_scale", this.worldScale);
      object.addProperty("spawn_latitude", safeSpawnLatitude);
      object.addProperty("spawn_longitude", safeSpawnLongitude);
      object.addProperty("cave_generation", this.caveGeneration);
      object.addProperty("ore_distribution", this.oreDistribution);
      object.addProperty("geological_stone_patches", this.geologicalStonePatches);
      object.addProperty("enable_roads", this.enableRoads);
      object.addProperty("enable_buildings", this.enableBuildings);
      object.addProperty("enable_water", this.enableWater);
      object.addProperty("thin_shell_terrain", this.thinShellTerrain);
      object.addProperty("experimental_increase_height", this.experimentalIncreaseHeight);
      object.addProperty("underground_depth", this.undergroundDepth);
      object.addProperty("add_strongholds", this.addStructures);
      object.addProperty("add_villages", this.addStructures);
      object.addProperty("add_mineshafts", this.addStructures);
      object.addProperty("add_ocean_monuments", this.addStructures);
      object.addProperty("add_woodland_mansions", this.addStructures);
      object.addProperty("add_desert_temples", this.addStructures);
      object.addProperty("add_jungle_temples", this.addStructures);
      object.addProperty("add_pillager_outposts", this.addStructures);
      object.addProperty("add_ruined_portals", this.addStructures);
      object.addProperty("add_shipwrecks", this.addStructures);
      object.addProperty("add_ocean_ruins", this.addStructures);
      object.addProperty("add_buried_treasure", this.addStructures);
      object.addProperty("add_igloos", this.addStructures);
      object.addProperty("add_witch_huts", this.addStructures);
      object.addProperty("add_ancient_cities", this.addStructures);
      object.addProperty("add_trial_chambers", this.addStructures);
      object.addProperty("add_trail_ruins", this.addStructures);
      AtomicReference<String> parseError = new AtomicReference<>();
      EarthGeneratorSettings applied = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, object)
         .resultOrPartial(parseError::set)
         .orElse(null);
      if (parseError.get() != null || applied == null) {
         throw new IllegalStateException("Unable to apply the selected terrain preload settings: " + errorDetail(parseError.get()));
      }
      double scaleTolerance = Math.max(1.0E-6, Math.abs(this.worldScale) * 1.0E-6);
      if (Math.abs(applied.worldScale() - this.worldScale) > scaleTolerance) {
         throw new IllegalStateException(
            "Terrain preload world scale changed unexpectedly from " + this.worldScale + " to " + applied.worldScale() + "."
         );
      }
      return applied;
   }

   private static String errorDetail(String message) {
      return message == null || message.isBlank() ? "unknown codec error" : message;
   }

   public TerrainPreloadSettingsOverrides withWorldScale(double value) {
      return new TerrainPreloadSettingsOverrides(
         value,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withCaveGeneration(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         value,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withOreDistribution(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         value,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withGeologicalStonePatches(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         value,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withAddStructures(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         value,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withEnableRoads(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         value,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withEnableBuildings(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         value,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withEnableWater(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         value,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withThinShellTerrain(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         value,
         this.experimentalIncreaseHeight,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withExperimentalIncreaseHeight(boolean value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         value,
         this.undergroundDepth
      );
   }

   public TerrainPreloadSettingsOverrides withUndergroundDepth(int value) {
      return new TerrainPreloadSettingsOverrides(
         this.worldScale,
         this.caveGeneration,
         this.oreDistribution,
         this.geologicalStonePatches,
         this.addStructures,
         this.enableRoads,
         this.enableBuildings,
         this.enableWater,
         this.thinShellTerrain,
         this.experimentalIncreaseHeight,
         value
      );
   }

   private static boolean structuresEnabled(EarthGeneratorSettings settings) {
      return settings.addStrongholds()
         || settings.addVillages()
         || settings.addMineshafts()
         || settings.addOceanMonuments()
         || settings.addWoodlandMansions()
         || settings.addDesertTemples()
         || settings.addJungleTemples()
         || settings.addPillagerOutposts()
         || settings.addRuinedPortals()
         || settings.addShipwrecks()
         || settings.addOceanRuins()
         || settings.addBuriedTreasure()
         || settings.addIgloos()
         || settings.addWitchHuts()
         || settings.addAncientCities()
         || settings.addTrialChambers()
         || settings.addTrailRuins();
   }
}
