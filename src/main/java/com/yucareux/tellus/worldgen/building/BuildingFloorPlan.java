package com.yucareux.tellus.worldgen.building;

import com.yucareux.tellus.world.data.osm.OsmBuildingFeature;
import com.yucareux.tellus.worldgen.EarthProjection;
import com.yucareux.tellus.worldgen.ScanlinePolygonRasterizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import net.minecraft.core.Direction;

/** A world-coordinate plan shared by every chunk and every storey of a building. */
public final class BuildingFloorPlan {
   private static final int MAX_PLAN_AREA = 65_536;
   private static final Map<BuildingBlueprint, BuildingFloorPlan> CACHE = new LinkedHashMap<>(32, 0.75F, true);
   private static int cachedCells;
   private final BuildingBlueprint blueprint;
   private final int width;
   private final int depth;
   private final int[] distance;
   private final Stairwell stairs;
   private final Map<Integer, Floor> floors = new ConcurrentHashMap<>();

   public static BuildingFloorPlan create(BuildingBlueprint blueprint, OsmBuildingFeature feature, double scale) {
      synchronized (CACHE) {
         BuildingFloorPlan cached = CACHE.get(blueprint);
         if (cached != null) {
            return cached;
         }
      }
      boolean[] footprint = new boolean[planArea(blueprint)];
      if (footprint.length > 0) {
         double[][] xs = new double[feature.partCount()][];
         double[][] zs = new double[feature.partCount()][];
         for (int part = 0; part < feature.partCount(); part++) {
            xs[part] = new double[feature.pointCount(part)];
            zs[part] = new double[feature.pointCount(part)];
            for (int point = 0; point < xs[part].length; point++) {
               xs[part][point] = feature.lonAt(part, point) * EarthProjection.blocksPerDegree(scale) - 0.5;
               zs[part][point] = EarthProjection.latToBlockZ(feature.latAt(part, point), scale) - 0.5;
            }
         }
         // Use the same cell coverage as the chunk rasterizer, including courtyard holes.
         ScanlinePolygonRasterizer.fill(xs, zs, blueprint.minWorldX(), blueprint.minWorldZ(), blueprint.maxWorldX(), blueprint.maxWorldZ(),
            (x, z) -> footprint[(z - blueprint.minWorldZ()) * blueprint.width() + x - blueprint.minWorldX()] = true);
      }
      BuildingFloorPlan plan = new BuildingFloorPlan(blueprint, footprint);
      synchronized (CACHE) {
         BuildingFloorPlan previous = CACHE.put(blueprint, plan);
         cachedCells += plan.distance.length - (previous == null ? 0 : previous.distance.length);
         while (cachedCells > MAX_PLAN_AREA * 4 || CACHE.size() > 32) {
            BuildingBlueprint oldest = CACHE.keySet().iterator().next();
            cachedCells -= CACHE.remove(oldest).distance.length;
         }
      }
      return plan;
   }

   public static BuildingFloorPlan create(BuildingBlueprint blueprint, BiPredicate<Integer, Integer> footprint) {
      boolean[] occupied = new boolean[planArea(blueprint)];
      for (int i = 0; i < occupied.length; i++) {
         occupied[i] = footprint.test(blueprint.minWorldX() + i % blueprint.width(), blueprint.minWorldZ() + i / blueprint.width());
      }
      return new BuildingFloorPlan(blueprint, occupied);
   }

   private static int planArea(BuildingBlueprint blueprint) {
      long area = (long)blueprint.width() * blueprint.depth();
      BuildingProfile.BuildingCategory category = blueprint.profile().category();
      if (category == BuildingProfile.BuildingCategory.GARAGE || category == BuildingProfile.BuildingCategory.SHED
         || category == BuildingProfile.BuildingCategory.GREENHOUSE) {
         return 0;
      }
      return blueprint.interiorsEnabled() && area > 0 && area <= MAX_PLAN_AREA ? (int)area : 0;
   }

   private BuildingFloorPlan(BuildingBlueprint blueprint, boolean[] occupied) {
      this.blueprint = blueprint;
      this.width = blueprint.width();
      this.depth = blueprint.depth();
      this.distance = new int[occupied.length];
      Arrays.fill(this.distance, -1);
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      for (int i = 0; i < occupied.length; i++) {
         if (occupied[i]) {
            int x = i % this.width;
            int z = i / this.width;
            this.distance[i] = Integer.MAX_VALUE;
            if (x == 0 || z == 0 || x == this.width - 1 || z == this.depth - 1
               || !occupied[i - 1] || !occupied[i + 1] || !occupied[i - this.width] || !occupied[i + this.width]) {
               this.distance[i] = 0;
               queue.add(i);
            }
         }
      }
      while (!queue.isEmpty()) {
         int i = queue.removeFirst();
         for (Direction direction : Direction.Plane.HORIZONTAL) {
            int next = index(i % this.width + direction.getStepX(), i / this.width + direction.getStepZ());
            if (next >= 0 && this.distance[next] > this.distance[i] + 1) {
               this.distance[next] = this.distance[i] + 1;
               queue.add(next);
            }
         }
      }
      this.stairs = findStairwell();
   }

   public BuildingBlueprint blueprint() {
      return this.blueprint;
   }

   public Stairwell stairs() {
      return this.stairs;
   }

   public Floor floor(int floorIndex) {
      int setback = this.blueprint.setbackForFloor(floorIndex);
      // Geometry repeats vertically; furnishings are assigned to room uses by the renderer.
      return this.floors.computeIfAbsent(setback, this::planFloor);
   }

   public int indexAt(int worldX, int worldZ) {
      return index(worldX - this.blueprint.minWorldX(), worldZ - this.blueprint.minWorldZ());
   }

   public int boundaryDistanceAt(int worldX, int worldZ) {
      int index = indexAt(worldX, worldZ);
      return index < 0 ? -1 : this.distance[index];
   }

   public boolean isEntrancePassage(int x, int z) {
      if (!this.blueprint.isEntrancePassage(x, z) || boundaryDistanceAt(x, z) < 0) return false;
      // Recesses may cross several facade cells, but must stop before the opposite wall of a narrow building.
      int step = -this.blueprint.entranceAlong(x, z);
      for (int i = 1; i < step; i++) {
         int innerX = this.blueprint.entranceWorldX() - i * this.blueprint.entranceFacing().getStepX();
         int innerZ = this.blueprint.entranceWorldZ() - i * this.blueprint.entranceFacing().getStepZ();
         if (boundaryDistanceAt(innerX, innerZ) > 0) return boundaryDistanceAt(x, z) > 0;
      }
      return true;
   }

   public int worldX(int index) {
      return this.blueprint.minWorldX() + index % this.width;
   }

   public int worldZ(int index) {
      return this.blueprint.minWorldZ() + index / this.width;
   }

   private int index(int x, int z) {
      return x >= 0 && z >= 0 && x < this.width && z < this.depth && this.distance.length > 0 ? z * this.width + x : -1;
   }

   private Stairwell findStairwell() {
      if (this.blueprint.floorCount() < 2 || this.distance.length == 0) {
         return null;
      }
      int inset = this.blueprint.setbackForFloor(this.blueprint.floorCount() - 1);
      int height = this.blueprint.profile().storeyHeightBlocks();
      if (height < 3) {
         return null;
      }
      boolean tower = this.blueprint.profile().archetype() == BuildingProfile.Archetype.TOWER;
      Stairwell best = null;
      long bestScore = Long.MAX_VALUE;
      for (int flightWidth = tower ? 2 : 1; flightWidth >= 1; flightWidth--) {
         for (boolean alongX : new boolean[]{this.width >= this.depth, this.width < this.depth}) {
            int spanX = alongX ? height + 2 : flightWidth + 2;
            int spanZ = alongX ? flightWidth + 2 : height + 2;
            for (int z = 1; z + spanZ < this.depth; z++) {
               for (int x = 1; x + spanX < this.width; x++) {
                  if (!fits(x, z, spanX, spanZ, inset)) {
                     continue;
                  }
                  long dx = 2L * x + spanX - this.width;
                  long dz = 2L * z + spanZ - this.depth;
                  long score = dx * dx + dz * dz;
                  if (score < bestScore) {
                     bestScore = score;
                     best = new Stairwell(this.blueprint.minWorldX() + x, this.blueprint.minWorldZ() + z,
                        height, flightWidth, alongX, false);
                  }
               }
            }
         }
         if (best != null) {
            return best;
         }
      }
      // Very small footprints still need vertical access: a backed, unobstructed ladder.
      for (int z = 1; z < this.depth - 1; z++) {
         for (int x = 0; x < this.width - 1; x++) {
            int backing = index(x, z);
            int ladder = index(x + 1, z);
            if (this.distance[backing] >= inset && this.distance[ladder] > inset
               && !this.blueprint.isEntrancePassage(worldX(backing), worldZ(backing))
               && !this.blueprint.isEntrancePassage(worldX(ladder), worldZ(ladder))) {
               return new Stairwell(this.blueprint.minWorldX() + x, this.blueprint.minWorldZ() + z, height, 1, true, true);
            }
         }
      }
      return null;
   }

   private boolean fits(int x, int z, int spanX, int spanZ, int inset) {
      for (int dz = 0; dz < spanZ; dz++) {
         for (int dx = 0; dx < spanX; dx++) {
            int i = index(x + dx, z + dz);
            if (i < 0 || this.distance[i] <= inset || this.blueprint.isEntrancePassage(worldX(i), worldZ(i))) {
               return false;
            }
         }
      }
      return true;
   }

   private Floor planFloor(int setback) {
      int area = this.distance.length;
      boolean[] passage = new boolean[area];
      boolean[] usable = new boolean[area];
      for (int i = 0; i < area; i++) {
         usable[i] = this.distance[i] > setback;
      }
      if (area == 0) {
         return new Floor(passage, new boolean[0], new Direction[0], new int[0], List.of());
      }
      int source = -1;
      for (int step = 1; step <= BuildingEntranceLayout.PASSAGE_DEPTH; step++) {
         int i = indexAt(this.blueprint.entranceWorldX() - step * this.blueprint.entranceFacing().getStepX(),
            this.blueprint.entranceWorldZ() - step * this.blueprint.entranceFacing().getStepZ());
         if (i >= 0 && usable[i]) { source = i; break; }
      }
      if (source < 0) source = nearestUsable(usable, this.blueprint.entranceWorldX(), this.blueprint.entranceWorldZ());
      if (source < 0) {
         return new Floor(passage, new boolean[area], new Direction[area], emptyRoomIds(area), List.of());
      }
      // A single connected circulation network follows the real polygon, not its bounding box.
      int[] parent = pathsFrom(source, usable);
      passage[source] = true;
      if (this.stairs != null) {
         for (int i = 0; i < area; i++) {
            if (usable[i] && this.stairs.contains(worldX(i), worldZ(i))) {
               reservePath(i, parent, passage);
            }
         }
      }
      boolean alongX = this.stairs != null ? this.stairs.alongX() : this.width >= this.depth;
      int hallX = this.stairs == null ? worldX(source) : this.stairs.bypassX();
      int hallZ = this.stairs == null ? worldZ(source) : this.stairs.bypassZ();
      int hallWidth = this.blueprint.profile().archetype() == BuildingProfile.Archetype.HOUSE ? 1 : 2;
      for (int i = 0; i < area; i++) {
         if (usable[i] && parent[i] != -2
            && (alongX ? Math.abs(worldZ(i) - hallZ) < hallWidth : Math.abs(worldX(i) - hallX) < hallWidth)) {
            reservePath(i, parent, passage);
         }
      }
      // Reserve the full entry approach, even when the door is off-centre.
      for (int step = 1; step <= 3; step++) {
         int i = indexAt(this.blueprint.entranceWorldX() - step * this.blueprint.entranceFacing().getStepX(),
            this.blueprint.entranceWorldZ() - step * this.blueprint.entranceFacing().getStepZ());
         if (i >= 0 && usable[i]) {
            reservePath(i, parent, passage);
         }
      }
      boolean[] claimed = passage.clone();
      boolean[] walls = new boolean[area];
      Direction[] doors = new Direction[area];
      int[] roomIds = emptyRoomIds(area);
      List<Room> rooms = new ArrayList<>();
      for (int z = 1; z < this.depth - 3; z++) {
         for (int x = 1; x < this.width - 3; x++) {
            if (!available(x, z, 4, 4, usable, claimed)) {
               continue;
            }
            int spanX = 4;
            int spanZ = 4;
            while (spanX < 8 && available(x, z, spanX + 1, spanZ, usable, claimed)) {
               spanX++;
            }
            while (spanZ < 8 && available(x, z, spanX, spanZ + 1, usable, claimed)) {
               spanZ++;
            }
            Room room = new Room(this.blueprint.minWorldX() + x, this.blueprint.minWorldZ() + z, spanX, spanZ, rooms.size());
            int door = -1;
            Direction doorFacing = null;
            int bestDistance = Integer.MAX_VALUE;
            for (int dz = 0; dz < spanZ; dz++) {
               for (int dx = 0; dx < spanX; dx++) {
                  if ((dx == 0 || dx == spanX - 1) && (dz == 0 || dz == spanZ - 1)) {
                     continue;
                  }
                  for (Direction side : Direction.Plane.HORIZONTAL) {
                     if (!onSide(dx, dz, spanX, spanZ, side)) {
                        continue;
                     }
                     int outside = index(x + dx + side.getStepX(), z + dz + side.getStepZ());
                     int score = Math.abs(2 * dx - spanX + 1) + Math.abs(2 * dz - spanZ + 1);
                     if (outside >= 0 && passage[outside] && score < bestDistance) {
                        door = index(x + dx, z + dz);
                        doorFacing = side;
                        bestDistance = score;
                     }
                  }
               }
            }
            if (door < 0) {
               continue;
            }
            for (int dz = 0; dz < spanZ; dz++) {
               for (int dx = 0; dx < spanX; dx++) {
                  int i = index(x + dx, z + dz);
                  claimed[i] = true;
                  roomIds[i] = room.id();
                  for (Direction side : Direction.Plane.HORIZONTAL) {
                     int outside = index(x + dx + side.getStepX(), z + dz + side.getStepZ());
                     if (onSide(dx, dz, spanX, spanZ, side) && outside >= 0 && usable[outside] && !walls[outside]) {
                        walls[i] = true;
                     }
                  }
               }
            }
            walls[door] = false;
            doors[door] = doorFacing;
            // Door swings and the centre of the room stay clear of every fixture.
            int insideX = worldX(door) - doorFacing.getStepX();
            int insideZ = worldZ(door) - doorFacing.getStepZ();
            int centerX = room.minX() + room.width() / 2;
            int centerZ = room.minZ() + room.depth() / 2;
            passage[door] = true;
            while (insideX != centerX || insideZ != centerZ) {
               passage[indexAt(insideX, insideZ)] = true;
               if (insideX != centerX) {
                  insideX += Integer.signum(centerX - insideX);
               } else {
                  insideZ += Integer.signum(centerZ - insideZ);
               }
            }
            passage[indexAt(centerX, centerZ)] = true;
            rooms.add(room);
         }
      }
      return new Floor(passage, walls, doors, roomIds, List.copyOf(rooms));
   }

   private int[] emptyRoomIds(int area) {
      int[] ids = new int[area];
      Arrays.fill(ids, -1);
      return ids;
   }

   private boolean available(int x, int z, int spanX, int spanZ, boolean[] usable, boolean[] claimed) {
      for (int dz = 0; dz < spanZ; dz++) {
         for (int dx = 0; dx < spanX; dx++) {
            int i = index(x + dx, z + dz);
            if (i < 0 || !usable[i] || claimed[i]) {
               return false;
            }
         }
      }
      return true;
   }

   private static boolean onSide(int x, int z, int width, int depth, Direction side) {
      return switch (side) {
         case NORTH -> z == 0;
         case SOUTH -> z == depth - 1;
         case WEST -> x == 0;
         case EAST -> x == width - 1;
         default -> false;
      };
   }

   private int nearestUsable(boolean[] usable, int x, int z) {
      int best = -1;
      long bestDistance = Long.MAX_VALUE;
      for (int i = 0; i < usable.length; i++) {
         long dx = (long)worldX(i) - x;
         long dz = (long)worldZ(i) - z;
         long score = dx * dx + dz * dz;
         if (usable[i] && score < bestDistance) {
            best = i;
            bestDistance = score;
         }
      }
      return best;
   }

   private int[] pathsFrom(int source, boolean[] usable) {
      int[] parent = new int[usable.length];
      Arrays.fill(parent, -2);
      parent[source] = -1;
      ArrayDeque<Integer> queue = new ArrayDeque<>();
      queue.add(source);
      while (!queue.isEmpty()) {
         int i = queue.removeFirst();
         for (Direction direction : Direction.Plane.HORIZONTAL) {
            int next = index(i % this.width + direction.getStepX(), i / this.width + direction.getStepZ());
            if (next >= 0 && usable[next] && parent[next] == -2) {
               parent[next] = i;
               queue.add(next);
            }
         }
      }
      return parent;
   }

   private static void reservePath(int cell, int[] parent, boolean[] passage) {
      if (parent[cell] == -2) {
         return;
      }
      for (int i = cell; i >= 0 && !passage[i]; i = parent[i]) {
         passage[i] = true;
      }
   }

   public record Floor(boolean[] passage, boolean[] walls, Direction[] doors, int[] roomIds, List<Room> rooms) {
      public boolean hasCell(int index) {
         return index >= 0 && index < this.roomIds.length;
      }

      public boolean canFurnish(int index) {
         return hasCell(index) && this.roomIds[index] >= 0 && !this.passage[index] && !this.walls[index] && this.doors[index] == null;
      }
   }

   public record Room(int minX, int minZ, int width, int depth, int id) {
   }

   /** One full-height flight, a guardrail, a return aisle, and a landing at each end. */
   public record Stairwell(int minX, int minZ, int height, int flightWidth, boolean alongX, boolean ladder) {
      public boolean contains(int x, int z) {
         int along = this.alongX ? x - this.minX : z - this.minZ;
         int across = this.alongX ? z - this.minZ : x - this.minX;
         return this.ladder ? along >= 0 && along < 2 && across == 0
            : along >= 0 && along < this.height + 2 && across >= 0 && across < this.flightWidth + 2;
      }

      public int step(int x, int z) {
         if (!contains(x, z) || this.ladder) {
            return -1;
         }
         int along = this.alongX ? x - this.minX : z - this.minZ;
         int across = this.alongX ? z - this.minZ : x - this.minX;
         return along >= 1 && along <= this.height && across < this.flightWidth ? along - 1 : -1;
      }

      public boolean railing(int x, int z) {
         int along = this.alongX ? x - this.minX : z - this.minZ;
         int across = this.alongX ? z - this.minZ : x - this.minX;
         return !this.ladder && contains(x, z) && along >= 1 && along <= this.height && across == this.flightWidth;
      }

      public int bypassX() {
         return this.alongX ? this.minX : this.minX + this.flightWidth + 1;
      }

      public int bypassZ() {
         return this.alongX ? this.minZ + this.flightWidth + 1 : this.minZ;
      }

      public Direction facing() {
         return this.alongX ? Direction.EAST : Direction.SOUTH;
      }
   }
}
