package com.yucareux.tellus.worldgen.road;

import com.yucareux.tellus.world.data.osm.OsmStreetLightFeature;
import com.yucareux.tellus.world.data.osm.RoadClass;
import com.yucareux.tellus.world.data.osm.RoadFeature;
import com.yucareux.tellus.world.data.osm.RoadMode;
import com.yucareux.tellus.world.data.osm.RoadPointKind;
import com.yucareux.tellus.world.data.osm.RoadSurfaceStyle;
import com.yucareux.tellus.worldgen.EarthProjection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;

/** Plans in world coordinates first. Chunks only select the poles they own afterwards. */
public final class StreetLightPlanner {
   public static final int CONTEXT_MARGIN = 64;
   public static final double MAX_SCALE = 8.0;
   private static final int BUCKET_SIZE = 32;
   private static final Comparator<Lamp> PRIORITY = Comparator.comparing(Lamp::mapped).reversed()
      .thenComparing(Comparator.comparingInt((Lamp lamp) -> lamp.style().ordinal()).reversed())
      .thenComparingLong(Lamp::id).thenComparingInt(Lamp::worldX).thenComparingInt(Lamp::worldZ);

   private StreetLightPlanner() { }

   public static List<Lamp> plan(List<RoadFeature> roads, List<OsmStreetLightFeature> points, double scale,
      int mainWidth, int normalWidth, int dirtWidth, int minX, int minZ, int maxX, int maxZ) {
      if (!(scale > 0.0) || scale > MAX_SCALE || roads == null || roads.isEmpty()) return List.of();
      List<Path> paths = new ArrayList<>();
      for (RoadFeature road : roads) {
         int fallback = switch (road.roadClass()) {
            case MAIN -> mainWidth;
            case NORMAL -> normalWidth;
            case DIRT -> dirtWidth;
         };
         Path path = new Path(road, RoadSurfaceStyle.effectiveRoadWidth(road, fallback, scale), scale);
         if (path.length > 0.01) paths.add(path);
      }
      List<Lamp> candidates = new ArrayList<>();
      if (points != null) {
         for (OsmStreetLightFeature point : points) {
            if (point.kind() != RoadPointKind.STREET_LIGHT) continue;
            double x = point.longitude() * EarthProjection.blocksPerDegree(scale);
            double z = EarthProjection.latToBlockZ(point.latitude(), scale);
            Path nearest = null;
            Sample projection = null;
            double best = Double.POSITIVE_INFINITY;
            for (Path path : paths) {
               if (path.road.mode() != RoadMode.NORMAL) continue;
               Sample sample = path.project(x, z);
               double distance = Math.hypot(x - sample.x, z - sample.z);
               if (distance <= path.radius + 10.0 && (distance < best - 0.000001
                  || Math.abs(distance - best) < 0.000001 && (nearest == null || path.road.wayId() < nearest.road.wayId()))) {
                  best = distance;
                  nearest = path;
                  projection = sample;
               }
            }
            if (nearest == null) continue;
            double side = (x - projection.x) * -projection.tangentZ + (z - projection.z) * projection.tangentX;
            Lamp lamp = candidate(nearest, projection, side >= 0 ? 1 : -1, point.featureId(), true, paths, scale);
            // Retain a surveyed location if it is already on a safe verge; otherwise move it off the carriageway.
            if (lamp != null && best >= nearest.radius + 1.0 && best <= nearest.radius + 5.0
               && clearOfRoads(paths, quantize(x), quantize(z))) {
               lamp = new Lamp(lamp.id, lamp.roadId, quantize(x), quantize(z), projection.x, projection.z,
                  direction(projection.x - x, projection.z - z), lamp.style, lamp.height, lamp.arm, lamp.spacing, true);
            }
            if (lamp != null && inBounds(lamp, minX, minZ, maxX, maxZ, CONTEXT_MARGIN)) candidates.add(lamp);
         }
      }
      for (Path path : paths) {
         if (!automaticallyLit(path)) continue;
         int spacing = path.style.spacing(scale);
         double inset = Math.max(6.0, path.radius + 4.0);
         int stationNumber = 0;
         for (double station = inset; station <= path.length - inset; station += spacing, stationNumber++) {
            Sample sampled = path.sample(station);
            if (sampled.x < minX - CONTEXT_MARGIN - path.radius || sampled.x > maxX + CONTEXT_MARGIN + path.radius
               || sampled.z < minZ - CONTEXT_MARGIN - path.radius || sampled.z > maxZ + CONTEXT_MARGIN + path.radius) continue;
            int side = path.style == StreetLightStyle.COLLECTOR ? (stationNumber % 2 == 0 ? 1 : -1) : 1;
            for (int n = 0; n < (path.style.paired() ? 2 : 1); n++) {
               long id = mix(path.road.wayId() ^ (long)stationNumber * 341873128712L ^ (n == 0 ? 31L : 47L));
               Lamp lamp = candidate(path, sampled, n == 0 ? side : -side, id, false, paths, scale);
               if (lamp != null && inBounds(lamp, minX, minZ, maxX, maxZ, CONTEXT_MARGIN)) candidates.add(lamp);
            }
         }
      }
      // Compare all candidates, not only the ones accepted in this chunk. Local priority is independent of query order.
      Map<Long, List<Lamp>> buckets = new HashMap<>();
      for (Lamp candidate : candidates) {
         buckets.computeIfAbsent(bucket(Math.floorDiv(candidate.worldX, BUCKET_SIZE), Math.floorDiv(candidate.worldZ, BUCKET_SIZE)),
            ignored -> new ArrayList<>()).add(candidate);
      }
      Map<Long, Lamp> result = new HashMap<>();
      for (Lamp candidate : candidates) {
         if (!inBounds(candidate, minX, minZ, maxX, maxZ, 0)) continue;
         boolean blocked = false;
         int bx = Math.floorDiv(candidate.worldX, BUCKET_SIZE);
         int bz = Math.floorDiv(candidate.worldZ, BUCKET_SIZE);
         for (int dz = -2; dz <= 2 && !blocked; dz++) {
            for (int dx = -2; dx <= 2 && !blocked; dx++) {
               for (Lamp other : buckets.getOrDefault(bucket(bx + dx, bz + dz), List.of())) {
                  if (PRIORITY.compare(other, candidate) < 0 && conflicts(candidate, other)) {
                     blocked = true;
                     break;
                  }
               }
            }
         }
         if (!blocked) result.merge(bucket(candidate.worldX, candidate.worldZ), candidate,
            (first, second) -> PRIORITY.compare(first, second) <= 0 ? first : second);
      }
      return result.values().stream().sorted(Comparator.comparingInt(Lamp::worldX).thenComparingInt(Lamp::worldZ)).toList();
   }

   private static boolean automaticallyLit(Path path) {
      if (path.road.mode() != RoadMode.NORMAL || path.road.roadClass() == RoadClass.DIRT) return false;
      // Motorways and rural paths need mapped evidence, rather than a blanket line of urban lights.
      if (path.style == StreetLightStyle.HIGHWAY) return false;
      return switch (path.road.roadSurface()) {
         case "dirt", "earth", "ground", "grass", "sand", "gravel", "unpaved", "fine_gravel" -> false;
         default -> true;
      };
   }

   private static Lamp candidate(Path path, Sample sampled, int side, long id, boolean mapped, List<Path> roads, double scale) {
      if (nearJunction(path, sampled, roads)) return null;
      double nx = -sampled.tangentZ * side;
      double nz = sampled.tangentX * side;
      double offset = path.radius + (path.style.ordinal() >= StreetLightStyle.COLLECTOR.ordinal() ? 2.0 : 1.5);
      for (int extra = 0; extra <= 3; extra++) {
         int x = quantize(sampled.x + nx * (offset + extra));
         int z = quantize(sampled.z + nz * (offset + extra));
         if (clearOfRoads(roads, x, z)) {
            return new Lamp(id, path.road.wayId(), x, z, sampled.x, sampled.z, direction(-nx, -nz), path.style,
               path.style.height(scale), path.style.arm(scale), path.style.spacing(scale), mapped);
         }
      }
      return null;
   }

   private static boolean nearJunction(Path path, Sample sampled, List<Path> roads) {
      for (Path other : roads) {
         if (other == path || other.road.mode() != RoadMode.NORMAL || other.road.wayId() == path.road.wayId()) continue;
         Sample closest = other.project(sampled.x, sampled.z);
         double dot = Math.abs(sampled.tangentX * closest.tangentX + sampled.tangentZ * closest.tangentZ);
         if (dot < 0.94 && Math.hypot(closest.x - sampled.x, closest.z - sampled.z) < other.radius + 6.0) return true;
      }
      return false;
   }

   private static boolean clearOfRoads(List<Path> roads, int x, int z) {
      for (Path path : roads) {
         if (path.road.mode() != RoadMode.NORMAL) continue;
         Sample closest = path.project(x, z);
         if (Math.hypot(x - closest.x, z - closest.z) < path.radius + 0.85) return false;
      }
      return true;
   }

   private static boolean conflicts(Lamp first, Lamp second) {
      double separation;
      if (first.roadId == second.roadId && first.style.paired() && second.style.paired()
         && Math.hypot(first.roadX - second.roadX, first.roadZ - second.roadZ) < 1.0
         && first.facing == second.facing.getOpposite()) {
         separation = 3.0;
      } else if (first.mapped && second.mapped) {
         separation = Math.max(6.0, Math.min(first.spacing, second.spacing) * 0.4);
      } else {
         separation = Math.max(8.0, Math.min(first.spacing, second.spacing) * 0.7);
      }
      return Math.hypot(first.worldX - second.worldX, first.worldZ - second.worldZ) < separation;
   }

   private static boolean inBounds(Lamp lamp, int minX, int minZ, int maxX, int maxZ, int margin) {
      return lamp.worldX >= (long)minX - margin && lamp.worldX <= (long)maxX + margin
         && lamp.worldZ >= (long)minZ - margin && lamp.worldZ <= (long)maxZ + margin;
   }

   private static int quantize(double value) { return (int)Math.floor(value + 0.5); }
   private static long bucket(int x, int z) { return (long)x << 32 ^ z & 0xffffffffL; }
   private static Direction direction(double x, double z) {
      return Math.abs(x) >= Math.abs(z) ? (x >= 0 ? Direction.EAST : Direction.WEST) : (z >= 0 ? Direction.SOUTH : Direction.NORTH);
   }
   private static long mix(long seed) {
      seed = (seed ^ seed >>> 33) * -49064778989728563L;
      seed = (seed ^ seed >>> 33) * -4265267296055464877L;
      return seed ^ seed >>> 33;
   }

   public record Lamp(long id, long roadId, int worldX, int worldZ, double roadX, double roadZ, Direction facing,
      StreetLightStyle style, int height, int arm, int spacing, boolean mapped) { }

   private record Sample(double x, double z, double tangentX, double tangentZ) { }

   private static final class Path {
      final RoadFeature road;
      final StreetLightStyle style;
      final double radius;
      final double[] xs;
      final double[] zs;
      final double[] starts;
      final double length;

      Path(RoadFeature road, int width, double scale) {
         this.road = road;
         this.style = StreetLightStyle.forRoad(road, width);
         this.radius = Math.max(0.5, (width - 1) * 0.5);
         int count = road.pointCount();
         this.xs = new double[count];
         this.zs = new double[count];
         this.starts = new double[count];
         boolean closed = road.lonAt(0) == road.lonAt(count - 1) && road.latAt(0) == road.latAt(count - 1);
         int first = 0;
         int step = comparePoint(road, 0, count - 1) > 0 ? -1 : 1;
         if (closed) {
            for (int i = 1; i < count - 1; i++) if (comparePoint(road, i, first) < 0) first = i;
            int next = (first + 1) % (count - 1);
            int previous = Math.floorMod(first - 1, count - 1);
            step = comparePoint(road, next, previous) <= 0 ? 1 : -1;
         } else if (step < 0) first = count - 1;
         double total = 0;
         for (int i = 0; i < count; i++) {
            int point = closed ? Math.floorMod(first + step * i, count - 1) : first + step * i;
            this.xs[i] = road.lonAt(point) * EarthProjection.blocksPerDegree(scale);
            this.zs[i] = EarthProjection.latToBlockZ(road.latAt(point), scale);
            if (i > 0) total += Math.hypot(this.xs[i] - this.xs[i - 1], this.zs[i] - this.zs[i - 1]);
            this.starts[i] = total;
         }
         this.length = total;
      }

      private static int comparePoint(RoadFeature road, int first, int second) {
         int longitude = Double.compare(road.lonAt(first), road.lonAt(second));
         return longitude != 0 ? longitude : Double.compare(road.latAt(first), road.latAt(second));
      }

      Sample sample(double station) {
         for (int i = 1; i < this.xs.length; i++) {
            double segment = this.starts[i] - this.starts[i - 1];
            if (segment > 0.001 && (station <= this.starts[i] || i == this.xs.length - 1)) {
               return onSegment(i, Math.max(0, Math.min(1, (station - this.starts[i - 1]) / segment)));
            }
         }
         return new Sample(this.xs[0], this.zs[0], 1, 0);
      }

      Sample project(double x, double z) {
         Sample best = null;
         double distance = Double.POSITIVE_INFINITY;
         for (int i = 1; i < this.xs.length; i++) {
            double dx = this.xs[i] - this.xs[i - 1];
            double dz = this.zs[i] - this.zs[i - 1];
            double squared = dx * dx + dz * dz;
            if (squared < 0.000001) continue;
            Sample sample = onSegment(i, Math.max(0, Math.min(1, ((x - this.xs[i - 1]) * dx + (z - this.zs[i - 1]) * dz) / squared)));
            double delta = Math.hypot(x - sample.x, z - sample.z);
            if (delta < distance) { best = sample; distance = delta; }
         }
         return best == null ? sample(0) : best;
      }

      Sample onSegment(int i, double t) {
         double dx = this.xs[i] - this.xs[i - 1];
         double dz = this.zs[i] - this.zs[i - 1];
         double segment = Math.hypot(dx, dz);
         return new Sample(this.xs[i - 1] + dx * t, this.zs[i - 1] + dz * t, dx / segment, dz / segment);
      }
   }
}
