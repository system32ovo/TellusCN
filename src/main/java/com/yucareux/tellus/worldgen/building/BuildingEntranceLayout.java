package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.world.data.osm.RoadMode;
import com.yucareux.tellus.worldgen.EarthProjection;
import com.yucareux.tellus.worldgen.ScanlinePolygonRasterizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;

/** Chooses a real doorway into usable floor space, using the same blocks as the building rasterizer. */
public final class BuildingEntranceLayout {
   public static final int PASSAGE_DEPTH = 3;
   public static final int APPROACH_LENGTH = 6;
   private static final int MAX_AREA = 262144;

   private BuildingEntranceLayout() { }

   public static Placement resolve(OsmBuildingFeature feature, List<RoadFeature> roads, List<OsmBuildingFeature> neighbors,
      double scale, int minX, int maxX, int minZ, int maxZ) {
      if ((long)(maxX - minX + 3) * (maxZ - minZ + 3) > MAX_AREA) return null;
      Grid footprint = raster(feature, scale, minX - 1, maxX + 1, minZ - 1, maxZ + 1);
      Grid blocked = new Grid(minX - APPROACH_LENGTH - 2, maxX + APPROACH_LENGTH + 2,
         minZ - APPROACH_LENGTH - 2, maxZ + APPROACH_LENGTH + 2);
      for (OsmBuildingFeature neighbor : neighbors) {
         if (neighbor.featureId() == feature.featureId() || neighbor.minHeightMeters() > 0) continue;
         if (neighbor.hasParts() && feature.buildingId() != null && feature.buildingId().equals(neighbor.buildingId())) continue;
         fill(neighbor, scale, blocked);
      }
      return choose(footprint, roads, scale, (x, z) -> {
         for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (blocked.at(x + dx, z + dz)) return true;
         }
         return false;
      });
   }

   static Placement choose(int minX, int maxX, int minZ, int maxZ, BiPredicate<Integer, Integer> occupied,
      List<RoadFeature> roads, double scale, BiPredicate<Integer, Integer> blocked) {
      Grid grid = new Grid(minX - 1, maxX + 1, minZ - 1, maxZ + 1);
      for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) grid.set(x, z, occupied.test(x, z));
      return choose(grid, roads, scale, blocked);
   }

   private static Placement choose(Grid grid, List<RoadFeature> roads, double scale, BiPredicate<Integer, Integer> blocked) {
      boolean[] exterior = grid.floodExterior();
      int[] components = grid.interiorComponents();
      List<Segment> segments = new ArrayList<>();
      for (RoadFeature road : roads) {
         if (road.mode() != RoadMode.NORMAL) continue;
         for (int i = 1; i < road.pointCount(); i++) segments.add(new Segment(
            road.lonAt(i - 1) * EarthProjection.blocksPerDegree(scale), EarthProjection.latToBlockZ(road.latAt(i - 1), scale),
            road.lonAt(i) * EarthProjection.blocksPerDegree(scale), EarthProjection.latToBlockZ(road.latAt(i), scale)));
      }
      Placement best = null;
      double bestScore = Double.POSITIVE_INFINITY;
      for (int z = grid.minZ + 1; z < grid.maxZ; z++) for (int x = grid.minX + 1; x < grid.maxX; x++) {
         if (!grid.at(x, z)) continue;
         for (Direction facing : Direction.Plane.HORIZONTAL) {
            int outsideX = x + facing.getStepX();
            int outsideZ = z + facing.getStepZ();
            if (!exterior[grid.index(outsideX, outsideZ)]) continue;
            boolean clear = true;
            for (int step = 1; step <= APPROACH_LENGTH; step++) {
               int ax = x + facing.getStepX() * step;
               int az = z + facing.getStepZ() * step;
               if (grid.at(ax, az) || blocked.test(ax, az)) { clear = false; break; }
            }
            if (!clear) continue;
            int depth = 0;
            for (int step = 1; step <= PASSAGE_DEPTH; step++) {
               int ix = x - facing.getStepX() * step;
               int iz = z - facing.getStepZ() * step;
               if (!grid.at(ix, iz)) break;
               int component = components[grid.index(ix, iz)];
               if (component != 0) {
                  if (component == 1) depth = step;
                  break;
               }
            }
            if (depth == 0) continue;
            double centerX = (grid.minX + grid.maxX) * 0.5;
            double centerZ = (grid.minZ + grid.maxZ) * 0.5;
            double centerDistance = Math.pow(x - centerX, 2) + Math.pow(z - centerZ, 2);
            double roadScore = Double.POSITIVE_INFINITY;
            for (Segment road : segments) {
               double[] closest = road.closest(outsideX, outsideZ);
               double dx = closest[0] - x;
               double dz = closest[1] - z;
               double forward = dx * facing.getStepX() + dz * facing.getStepZ();
               // A street behind this wall is not an entrance approach.
               if (forward <= 0) continue;
               double squared = dx * dx + dz * dz;
               roadScore = Math.min(roadScore, squared + squared - forward * forward);
            }
            double score = (Double.isFinite(roadScore) ? roadScore : 1000000.0) + centerDistance * 0.05 + depth * 2.0;
            if (score < bestScore - 0.000001) {
               bestScore = score;
               best = new Placement(x, z, facing);
            }
         }
      }
      return best;
   }

   private static Grid raster(OsmBuildingFeature feature, double scale, int minX, int maxX, int minZ, int maxZ) {
      Grid result = new Grid(minX, maxX, minZ, maxZ);
      fill(feature, scale, result);
      return result;
   }

   private static void fill(OsmBuildingFeature feature, double scale, Grid result) {
      double[][] xs = new double[feature.partCount()][];
      double[][] zs = new double[feature.partCount()][];
      for (int ring = 0; ring < feature.partCount(); ring++) {
         xs[ring] = new double[feature.pointCount(ring)];
         zs[ring] = new double[feature.pointCount(ring)];
         for (int point = 0; point < xs[ring].length; point++) {
            xs[ring][point] = feature.lonAt(ring, point) * EarthProjection.blocksPerDegree(scale) - 0.5;
            zs[ring][point] = EarthProjection.latToBlockZ(feature.latAt(ring, point), scale) - 0.5;
         }
      }
      ScanlinePolygonRasterizer.fill(xs, zs, result.minX, result.minZ, result.maxX, result.maxZ, (x, z) -> result.set(x, z, true));
   }

   public record Placement(int worldX, int worldZ, Direction facing) { }

   private record Segment(double x1, double z1, double x2, double z2) {
      double[] closest(double x, double z) {
         double dx = x2 - x1;
         double dz = z2 - z1;
         double squared = dx * dx + dz * dz;
         double t = squared < 0.000001 ? 0 : Math.max(0, Math.min(1, ((x - x1) * dx + (z - z1) * dz) / squared));
         return new double[]{x1 + t * dx, z1 + t * dz};
      }
   }

   private static final class Grid {
      final int minX, maxX, minZ, maxZ, width;
      final boolean[] cells;
      Grid(int minX, int maxX, int minZ, int maxZ) {
         this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
         this.width = maxX - minX + 1;
         this.cells = new boolean[this.width * (maxZ - minZ + 1)];
      }
      int index(int x, int z) { return (z - minZ) * width + x - minX; }
      boolean at(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ && cells[index(x, z)]; }
      void set(int x, int z, boolean value) { cells[index(x, z)] = value; }
      boolean[] floodExterior() {
         boolean[] reached = new boolean[cells.length];
         ArrayDeque<Integer> queue = new ArrayDeque<>();
         queue.add(0); reached[0] = true;
         while (!queue.isEmpty()) {
            int i = queue.removeFirst();
            int x = minX + i % width;
            int z = minZ + i / width;
            for (Direction d : Direction.Plane.HORIZONTAL) {
               int nx = x + d.getStepX(), nz = z + d.getStepZ();
               if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
               int n = index(nx, nz);
               if (!cells[n] && !reached[n]) { reached[n] = true; queue.add(n); }
            }
         }
         return reached;
      }
      int[] interiorComponents() {
         int[] result = new int[cells.length];
         List<List<Integer>> groups = new ArrayList<>();
         for (int z = minZ + 1; z < maxZ; z++) for (int x = minX + 1; x < maxX; x++) {
            if (at(x, z) && at(x-1, z) && at(x+1, z) && at(x, z-1) && at(x, z+1)) result[index(x, z)] = -1;
         }
         for (int i = 0; i < result.length; i++) {
            if (result[i] != -1) continue;
            List<Integer> group = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            result[i] = 2; queue.add(i);
            while (!queue.isEmpty()) {
               int n = queue.removeFirst(); group.add(n);
               for (int next : new int[]{n - 1, n + 1, n - width, n + width}) {
                  if (next >= 0 && next < result.length && result[next] == -1) { result[next] = 2; queue.add(next); }
               }
            }
            groups.add(group);
         }
         // A doorway into an isolated decorative sliver cannot serve the building's main interior.
         groups.stream().max(Comparator.comparingInt(List::size)).ifPresent(main -> main.forEach(i -> result[i] = 1));
         return result;
      }
   }
}
