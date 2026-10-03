package com.yucareux.tellus.integration.distant_horizons.managed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ManagedTerrainTarget(
   int centerChunkX,
   int centerChunkZ,
   int renderRadiusChunks,
   int safetyRingChunks,
   int targetRadiusChunks
) {
   public static final int MIN_RENDER_RADIUS_CHUNKS = 32;
   public static final int MAX_RENDER_RADIUS_CHUNKS = 4096;
   public static final int MIN_SAFETY_RING_CHUNKS = 32;
   public static final int MAX_SAFETY_RING_CHUNKS = 128;

   public static ManagedTerrainTarget initial(int playerChunkX, int playerChunkZ, int renderRadiusChunks) {
      int radius = clamp(renderRadiusChunks, MIN_RENDER_RADIUS_CHUNKS, MAX_RENDER_RADIUS_CHUNKS);
      int safetyRing = alignUp(clamp((radius + 7) / 8, MIN_SAFETY_RING_CHUNKS, MAX_SAFETY_RING_CHUNKS), ManagedTerrainCell.SIZE_CHUNKS);
      return new ManagedTerrainTarget(playerChunkX, playerChunkZ, radius, safetyRing, radius + safetyRing);
   }

   public ManagedTerrainTarget update(int playerChunkX, int playerChunkZ, int renderRadiusChunks) {
      ManagedTerrainTarget requested = initial(playerChunkX, playerChunkZ, renderRadiusChunks);
      boolean radiusChanged = requested.renderRadiusChunks != this.renderRadiusChunks || requested.safetyRingChunks != this.safetyRingChunks;
      long dx = Math.abs((long)playerChunkX - this.centerChunkX);
      long dz = Math.abs((long)playerChunkZ - this.centerChunkZ);
      return radiusChanged || Math.max(dx, dz) >= this.safetyRingChunks ? requested : this;
   }

   public List<ManagedTerrainCell> prioritizedCells(int playerChunkX, int playerChunkZ) {
      ManagedTerrainCell min = ManagedTerrainCell.containingChunk(
         saturatedSubtract(this.centerChunkX, this.targetRadiusChunks), saturatedSubtract(this.centerChunkZ, this.targetRadiusChunks)
      );
      ManagedTerrainCell max = ManagedTerrainCell.containingChunk(
         saturatedAdd(this.centerChunkX, this.targetRadiusChunks), saturatedAdd(this.centerChunkZ, this.targetRadiusChunks)
      );
      ManagedTerrainCell playerCell = ManagedTerrainCell.containingChunk(playerChunkX, playerChunkZ);
      List<ManagedTerrainCell> cells = new ArrayList<>((max.x() - min.x() + 1) * (max.z() - min.z() + 1));
      for (int z = min.z(); z <= max.z(); z++) {
         for (int x = min.x(); x <= max.x(); x++) {
            cells.add(new ManagedTerrainCell(x, z));
         }
      }
      cells.sort(
         Comparator.comparingLong((ManagedTerrainCell cell) -> chebyshev(cell, playerCell))
            .thenComparingLong(cell -> chebyshev(cell, ManagedTerrainCell.containingChunk(this.centerChunkX, this.centerChunkZ)))
            .thenComparingInt(ManagedTerrainCell::z)
            .thenComparingInt(ManagedTerrainCell::x)
      );
      return cells;
   }

   public List<CellBatch> prioritizedBatches(int playerChunkX, int playerChunkZ, int cellsPerSide) {
      int safeCellsPerSide = Math.max(1, cellsPerSide);
      Map<Long, List<ManagedTerrainCell>> grouped = new LinkedHashMap<>();
      for (ManagedTerrainCell cell : this.prioritizedCells(playerChunkX, playerChunkZ)) {
         int batchX = Math.floorDiv(cell.x(), safeCellsPerSide);
         int batchZ = Math.floorDiv(cell.z(), safeCellsPerSide);
         long key = (long)batchX << 32 | batchZ & 0xffffffffL;
         grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(cell);
      }

      List<CellBatch> batches = new ArrayList<>(grouped.size());
      grouped.forEach((key, cells) -> batches.add(new CellBatch((int)(key >> 32), (int)(long)key, List.copyOf(cells))));
      return List.copyOf(batches);
   }

   public boolean contains(ManagedTerrainCell cell) {
      int minX = saturatedSubtract(this.centerChunkX, this.targetRadiusChunks);
      int minZ = saturatedSubtract(this.centerChunkZ, this.targetRadiusChunks);
      int maxX = saturatedAdd(this.centerChunkX, this.targetRadiusChunks);
      int maxZ = saturatedAdd(this.centerChunkZ, this.targetRadiusChunks);
      return cell.maxChunkX() >= minX && cell.minChunkX() <= maxX && cell.maxChunkZ() >= minZ && cell.minChunkZ() <= maxZ;
   }

   private static long chebyshev(ManagedTerrainCell a, ManagedTerrainCell b) {
      return Math.max(Math.abs((long)a.x() - b.x()), Math.abs((long)a.z() - b.z()));
   }

   private static int alignUp(int value, int alignment) {
      return Math.min(MAX_SAFETY_RING_CHUNKS, ((value + alignment - 1) / alignment) * alignment);
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static int saturatedAdd(int value, int amount) {
      return (int)Math.min(Integer.MAX_VALUE, (long)value + amount);
   }

   private static int saturatedSubtract(int value, int amount) {
      return (int)Math.max(Integer.MIN_VALUE, (long)value - amount);
   }

   public record CellBatch(int x, int z, List<ManagedTerrainCell> cells) {
      public CellBatch {
         cells = List.copyOf(cells);
         if (cells.isEmpty()) {
            throw new IllegalArgumentException("Managed terrain batch must contain at least one cell");
         }
      }
   }
}
