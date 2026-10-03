package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class TellusBuildingBlueprints {
   private TellusBuildingBlueprints() {
   }

   public static BuildingBlueprint create(
      String groupId,
      OsmBuildingFeature feature,
      BuildingProfile profile,
      long worldSeed,
      int baseY,
      int floorY,
      int roofBaseY,
      int topY,
      List<RoadFeature> nearbyRoads,
      double worldScale
   ) {
      return create(groupId, feature, profile, worldSeed, baseY, floorY, roofBaseY, topY, nearbyRoads, worldScale, List.of());
   }

   public static BuildingBlueprint create(
      String groupId, OsmBuildingFeature feature, BuildingProfile profile, long worldSeed,
      int baseY, int floorY, int roofBaseY, int topY, List<RoadFeature> nearbyRoads,
      double worldScale, List<OsmBuildingFeature> nearbyBuildings
   ) {
      Objects.requireNonNull(groupId, "groupId");
      Objects.requireNonNull(feature, "feature");
      Objects.requireNonNull(profile, "profile");
      int minWorldX = Mth.floor(feature.minBlockXForScale(worldScale));
      int maxWorldX = Mth.ceil(feature.maxBlockXForScale(worldScale));
      int minWorldZ = Mth.floor(feature.minBlockZ(worldScale));
      int maxWorldZ = Mth.ceil(feature.maxBlockZ(worldScale));
      long seed = mixSeed(worldSeed, feature.featureId(), groupId.hashCode());
      BuildingStyle style = TellusBuildingStyles.resolveBuildingStyle(
         profile, feature.metadata(), feature.areaSquareMeters(), maxWorldX - minWorldX + 1, maxWorldZ - minWorldZ + 1, seed
      );
      BuildingEntranceLayout.Placement entrance = profile.interiorsEnabled() && feature.minHeightMeters() <= 0
         ? BuildingEntranceLayout.resolve(feature, nearbyRoads == null ? List.of() : nearbyRoads, nearbyBuildings,
            worldScale, minWorldX, maxWorldX, minWorldZ, maxWorldZ) : null;
      return new BuildingBlueprint(groupId, seed, profile, style, baseY, floorY, roofBaseY, topY,
         minWorldX, maxWorldX, minWorldZ, maxWorldZ, entrance == null ? minWorldX : entrance.worldX(),
         entrance == null ? minWorldZ : entrance.worldZ(), entrance == null ? Direction.NORTH : entrance.facing(),
         entrance == null ? 0 : 1);
   }

   private static long mixSeed(long worldSeed, long featureId, long salt) {
      long seed = worldSeed ^ featureId * 341873128712L ^ salt * 132897987541L;
      seed ^= seed >>> 33;
      seed *= -49064778989728563L;
      seed ^= seed >>> 33;
      seed *= -4265267296055464877L;
      seed ^= seed >>> 33;
      return seed;
   }

}
