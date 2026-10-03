package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.OsmBuildingKind;
import com.yucareux.tellus.worldgen.EarthProjection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;

public final class BuildingPlacementSupport {
   private BuildingPlacementSupport() {
   }

   /** Every chunk samples the same footprint, so the doorway and the room behind it share a floor level. */
   public static int sampleFoundationHeight(OsmBuildingFeature feature, List<OsmBuildingFeature> neighbors,
      double scale, IntBinaryOperator surfaceHeight) {
      OsmBuildingFeature footprint = feature;
      if (feature.kind() == OsmBuildingKind.PART && feature.buildingId() != null) {
         for (OsmBuildingFeature neighbor : neighbors) {
            if (neighbor.kind() == OsmBuildingKind.FOOTPRINT && feature.buildingId().equals(neighbor.buildingId())
               && (footprint == feature || neighbor.featureId() < footprint.featureId())) footprint = neighbor;
         }
      }
      double minX = footprint.minBlockXForScale(scale), maxX = footprint.maxBlockXForScale(scale);
      double minZ = footprint.minBlockZ(scale), maxZ = footprint.maxBlockZ(scale);
      List<Integer> samples = new ArrayList<>();
      Set<Long> positions = new HashSet<>();
      for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
         int x = (int)Math.floor(minX + (maxX - minX) * (column + 0.5) / 4.0);
         int z = (int)Math.floor(minZ + (maxZ - minZ) * (row + 0.5) / 4.0);
         long key = (long)x << 32 ^ z & 0xffffffffL;
         if (footprint.containsWorld(x + 0.5, z + 0.5, scale) && positions.add(key)) samples.add(surfaceHeight.applyAsInt(x, z));
      }
      if (samples.isEmpty()) {
         // Very thin footprints still have a deterministic reference, independent of the requesting chunk.
         int x = (int)Math.floor(footprint.lonAt(0, 0) * EarthProjection.blocksPerDegree(scale));
         int z = (int)Math.floor(EarthProjection.latToBlockZ(footprint.latAt(0, 0), scale));
         return surfaceHeight.applyAsInt(x, z);
      }
      samples.sort(Integer::compare);
      return samples.get(samples.size() / 2);
   }

   public static int capLowerColumnTopY(int floorY, int columnTopY, int overlyingFloorY) {
      if (overlyingFloorY == Integer.MAX_VALUE) {
         return columnTopY;
      } else if (overlyingFloorY <= floorY) {
         return Integer.MIN_VALUE;
      } else {
         return Math.min(columnTopY, overlyingFloorY - 1);
      }
   }
}
