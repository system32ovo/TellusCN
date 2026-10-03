package com.yucareux.tellus.worldgen;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Applies the full-detail inland-water flow rules to an already sampled preview
 * grid. Preview cells may cover more than one block, so horizontal distances
 * are scaled while DEM and water elevations remain in block-height units.
 */
final class PreviewWaterFlowResolver {
   private static final int[] CARDINAL_OFFSETS = new int[]{1, 0, -1, 0, 0, 1, 0, -1};
   private static final int[] NEIGHBOR_OFFSETS_8 = new int[]{1, 0, -1, 0, 0, 1, 0, -1, 1, 1, 1, -1, -1, 1, -1, -1};
   private static final int[] NEIGHBOR_COSTS_8 = new int[]{10, 10, 10, 10, 14, 14, 14, 14};
   private static final int DIST_COST_CARDINAL = 10;
   private PreviewWaterFlowResolver() {
   }

   static Result resolve(
      int gridSize,
      int cellSizeBlocks,
      int baseWorldX,
      int baseWorldZ,
      int[] rawTerrain,
      boolean[] inlandWater,
      boolean[] oceanWater,
      boolean[] lineWater,
      boolean[] areaWater,
      boolean[] flowingWater,
      boolean[] waterfallNoCarve,
      int[] oceanWaterSurface,
      int riverMinLength,
      int riverMaxWidth,
      int lakeMaxTerrainCut,
      int riverConnectGapBlocks,
      int cliffSlopeThreshold,
      boolean limitCliffSlope,
      InlandWaterFlowAnalyzer.Parameters flowParameters
   ) {
      int area = checkedArea(gridSize);
      requireLength(rawTerrain, area, "raw terrain");
      requireLength(inlandWater, area, "inland-water mask");
      requireLength(oceanWater, area, "ocean-water mask");
      requireLength(lineWater, area, "line-water mask");
      requireLength(areaWater, area, "area-water mask");
      requireLength(flowingWater, area, "flowing-water mask");
      requireLength(waterfallNoCarve, area, "waterfall no-carve mask");
      requireLength(oceanWaterSurface, area, "ocean-water surface");
      if (cellSizeBlocks <= 0) {
         throw new IllegalArgumentException("Preview water cell size must be positive");
      }

      int[] terrainSurface = Arrays.copyOf(rawTerrain, area);
      int[] waterSurface = Arrays.copyOf(rawTerrain, area);
      boolean[] mappedInlandWater = Arrays.copyOf(inlandWater, area);
      boolean[] activeInlandWater = Arrays.copyOf(inlandWater, area);
      boolean[] previewLineWater = Arrays.copyOf(lineWater, area);
      boolean[] previewAreaWater = Arrays.copyOf(areaWater, area);
      boolean[] previewFlowingWater = Arrays.copyOf(flowingWater, area);
      boolean[] explicitNoCarve = Arrays.copyOf(waterfallNoCarve, area);
      boolean[] directLineWater = new boolean[area];
      boolean[] waterfallDrop = new boolean[area];
      boolean[] correctionRequired = new boolean[area];
      boolean[] waterfallProtection = Arrays.copyOf(explicitNoCarve, area);
      int[] waterfallTop = new int[area];
      int[] adaptiveBlendRadius = new int[area];
      int[] surfaceCeiling = Arrays.copyOf(rawTerrain, area);
      int[] componentIds = new int[area];
      Arrays.fill(waterfallTop, Integer.MIN_VALUE);
      Arrays.fill(componentIds, -1);

      for (int index = 0; index < area; index++) {
         if (oceanWater[index]) {
            activeInlandWater[index] = false;
            mappedInlandWater[index] = false;
            previewLineWater[index] = false;
            previewAreaWater[index] = false;
            previewFlowingWater[index] = false;
            waterSurface[index] = oceanWaterSurface[index];
         }
      }

      int maximumGapCells = divideRoundUp(Math.max(0, riverConnectGapBlocks), cellSizeBlocks);
      WaterSurfaceResolver.repairFlowingWaterGaps(
         activeInlandWater,
         oceanWater,
         previewLineWater,
         previewFlowingWater,
         gridSize,
         maximumGapCells
      );
      for (int index = 0; index < area; index++) {
         mappedInlandWater[index] |= activeInlandWater[index];
      }

      boolean[] componentRiver = new boolean[area];
      int[] componentWidthBlocks = new int[area];
      InlandWaterFlowAnalyzer.Workspace flowWorkspace = new InlandWaterFlowAnalyzer.Workspace();
      IntArrayList componentCells = new IntArrayList();
      IntArrayList polygonCells = new IntArrayList();
      IntArrayList borderHeights = new IntArrayList();
      IntArrayList componentHeights = new IntArrayList();
      int componentCount = 0;

      for (int start = 0; start < area; start++) {
         if (!activeInlandWater[start] || componentIds[start] >= 0) {
            continue;
         }

         componentCells.clear();
         polygonCells.clear();
         borderHeights.clear();
         componentHeights.clear();
         componentCells.add(start);
         componentIds[start] = componentCount;
         int minX = gridSize;
         int maxX = -1;
         int minZ = gridSize;
         int maxZ = -1;
         int lineCellCount = 0;
         int flowingCellCount = 0;
         boolean touchesEdge = false;

         for (int cursor = 0; cursor < componentCells.size(); cursor++) {
            int cell = componentCells.getInt(cursor);
            int x = cell % gridSize;
            int z = cell / gridSize;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            touchesEdge |= x == 0 || z == 0 || x == gridSize - 1 || z == gridSize - 1;
            lineCellCount += previewLineWater[cell] ? 1 : 0;
            flowingCellCount += previewFlowingWater[cell] ? 1 : 0;
            if (previewAreaWater[cell] && !explicitNoCarve[cell]) {
               polygonCells.add(cell);
            }
            componentHeights.add(rawTerrain[cell]);

            for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
               int nx = x + CARDINAL_OFFSETS[offset];
               int nz = z + CARDINAL_OFFSETS[offset + 1];
               if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                  continue;
               }
               int neighbor = nz * gridSize + nx;
               if (activeInlandWater[neighbor]) {
                  if (componentIds[neighbor] < 0) {
                     componentIds[neighbor] = componentCount;
                     componentCells.add(neighbor);
                  }
               } else if (!oceanWater[neighbor]) {
                  borderHeights.add(rawTerrain[neighbor]);
               }
            }
         }

         int widthCells = maxX - minX + 1;
         int heightCells = maxZ - minZ + 1;
         int maxDimensionBlocks = Math.max(widthCells, heightCells) * cellSizeBlocks;
         int minDimensionBlocks = Math.max(1, Math.min(widthCells, heightCells) * cellSizeBlocks);
         double aspect = (double)Math.max(widthCells, heightCells) / Math.max(1, Math.min(widthCells, heightCells));
         int cells = componentCells.size();
         boolean lineDominated = WaterSurfaceResolver.isLineDominatedWaterComponent(cells, lineCellCount);
         boolean flowDominated = WaterSurfaceResolver.isFlowDominatedWaterComponent(cells, flowingCellCount);
         boolean river = flowDominated || WaterSurfaceResolver.shouldClassifyInlandComponentAsRiver(
            maxDimensionBlocks,
            minDimensionBlocks,
            aspect,
            riverMinLength,
            riverMaxWidth,
            cells,
            lineCellCount,
            false,
            touchesEdge,
            false
         );
         if (river && !lineDominated && !flowDominated && shouldTreatAsLake(widthCells, heightCells, minDimensionBlocks, aspect, cells, riverMaxWidth)) {
            river = false;
         }
         componentRiver[componentCount] = river;
         componentWidthBlocks[componentCount] = minDimensionBlocks;

         for (int cursor = 0; cursor < componentCells.size(); cursor++) {
            int cell = componentCells.getInt(cursor);
            if (explicitNoCarve[cell]
               || WaterSurfaceResolver.shouldUseDirectLineWaterCell(previewLineWater[cell], previewAreaWater[cell])) {
               directLineWater[cell] = true;
               waterSurface[cell] = WaterSurfaceResolver.directLineRiverWaterSurface(rawTerrain[cell]);
            }
         }

         if (river && !polygonCells.isEmpty()) {
            prepareRiverSurfaceCeilings(
               gridSize,
               polygonCells,
               rawTerrain,
               activeInlandWater,
               oceanWater,
               oceanWaterSurface,
               surfaceCeiling,
               waterSurface
            );
            InlandWaterFlowAnalyzer.analyze(
               gridSize,
               polygonCells.elements(),
               polygonCells.size(),
               rawTerrain,
               surfaceCeiling,
               oceanWater,
               cellSizeBlocks,
               minDimensionBlocks,
               flowParameters,
               waterSurface,
               activeInlandWater,
               waterfallDrop,
               waterfallTop,
               correctionRequired,
               adaptiveBlendRadius,
               waterfallProtection,
               flowWorkspace
            );
         } else if (!river) {
            int stableSurface = stableLakeSurface(borderHeights, componentHeights);
            for (int cursor = 0; cursor < componentCells.size(); cursor++) {
               int cell = componentCells.getInt(cursor);
               if (directLineWater[cell]) {
                  continue;
               }
               if (rawTerrain[cell] - stableSurface > lakeMaxTerrainCut) {
                  activeInlandWater[cell] = false;
                  waterSurface[cell] = rawTerrain[cell];
               } else {
                  waterSurface[cell] = stableSurface;
                  correctionRequired[cell] = rawTerrain[cell] > stableSurface;
               }
            }
         }

         componentCount++;
      }

      int[] distanceFromShoreCells = distanceFromShore(
         gridSize,
         activeInlandWater,
         oceanWater,
         waterfallDrop,
         componentIds
      );
      for (int index = 0; index < area; index++) {
         if (directLineWater[index]) {
            waterSurface[index] = WaterSurfaceResolver.directLineRiverWaterSurface(rawTerrain[index]);
            terrainSurface[index] = WaterSurfaceResolver.directLineRiverTerrainSurface(waterSurface[index]);
            correctionRequired[index] = false;
            adaptiveBlendRadius[index] = 0;
            continue;
         }
         if (waterfallDrop[index]) {
            terrainSurface[index] = rawTerrain[index];
            continue;
         }
         if (!activeInlandWater[index]) {
            terrainSurface[index] = rawTerrain[index];
            continue;
         }

         int componentId = componentIds[index];
         boolean river = componentId >= 0 && componentRiver[componentId];
         int distanceCells = distanceFromShoreCells[index];
         int distanceBlocks = distanceCells < 0 && !river
            ? LakeBedProfile.maximumShoreInfluenceBlocks()
            : Math.max(0, distanceCells) * cellSizeBlocks;
         int localX = index % gridSize;
         int localZ = index / gridSize;
         int depth = river
            ? WaterSurfaceResolver.computeRiverDepth(distanceBlocks)
            : LakeBedProfile.depth(
               distanceBlocks,
               baseWorldX + localX * cellSizeBlocks,
               baseWorldZ + localZ * cellSizeBlocks
            );
         int floor = waterSurface[index] - Math.max(1, depth);
         if (waterfallProtection[index]) {
            floor = Math.min(rawTerrain[index], waterSurface[index] - 1);
         }
         terrainSurface[index] = floor;

         int cut = Math.max(0, rawTerrain[index] - waterSurface[index]);
         if (cut > 0) {
            correctionRequired[index] = true;
            int blendCut = river ? Math.min(cut, flowParameters.maxProfileCut()) : cut;
            int localSlope = localComponentSlope(index, gridSize, rawTerrain, componentIds, componentId);
            int channelWidth = componentId >= 0 ? componentWidthBlocks[componentId] : cellSizeBlocks;
            int radius = flowParameters.baseBlendBlocks()
               + blendCut * 3
               + Math.min(12, localSlope * 2)
               + Math.min(12, Math.max(1, channelWidth) / 3);
            adaptiveBlendRadius[index] = Math.min(
               flowParameters.maxAdaptiveBlendBlocks(),
               Math.max(adaptiveBlendRadius[index], Math.max(flowParameters.baseBlendBlocks(), radius))
            );
         }
      }

      applyAdaptiveBankBlend(
         gridSize,
         cellSizeBlocks,
         terrainSurface,
         rawTerrain,
         waterSurface,
         mappedInlandWater,
         activeInlandWater,
         oceanWater,
         waterfallDrop,
         correctionRequired,
         adaptiveBlendRadius,
         waterfallProtection,
         cliffSlopeThreshold,
         limitCliffSlope
      );

      return new Result(terrainSurface, waterSurface, activeInlandWater, waterfallDrop, waterfallProtection);
   }

   private static void prepareRiverSurfaceCeilings(
      int gridSize,
      IntArrayList componentCells,
      int[] rawTerrain,
      boolean[] inlandWater,
      boolean[] oceanWater,
      int[] oceanWaterSurface,
      int[] surfaceCeiling,
      int[] waterSurface
   ) {
      for (int cursor = 0; cursor < componentCells.size(); cursor++) {
         int cell = componentCells.getInt(cursor);
         int x = cell % gridSize;
         int z = cell / gridSize;
         int ceiling = rawTerrain[cell];
         for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
            int nx = x + CARDINAL_OFFSETS[offset];
            int nz = z + CARDINAL_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (oceanWater[neighbor]) {
               ceiling = Math.min(ceiling, oceanWaterSurface[neighbor]);
            } else if (!inlandWater[neighbor]) {
               ceiling = Math.min(ceiling, rawTerrain[neighbor]);
            }
         }
         surfaceCeiling[cell] = ceiling;
         waterSurface[cell] = ceiling;
      }
   }

   private static int stableLakeSurface(IntArrayList borderHeights, IntArrayList componentHeights) {
      int terrainHint = percentile(componentHeights, 0.25);
      return borderHeights.isEmpty()
         ? terrainHint
         : Math.min(percentile(borderHeights, 0.1), terrainHint);
   }

   private static int percentile(IntArrayList values, double percentile) {
      if (values.isEmpty()) {
         return 0;
      }
      int[] sorted = values.toIntArray();
      Arrays.sort(sorted);
      int index = (int)Math.floor(Math.max(0.0, Math.min(1.0, percentile)) * (sorted.length - 1));
      return sorted[index];
   }

   private static boolean shouldTreatAsLake(
      int widthCells,
      int heightCells,
      int minDimensionBlocks,
      double aspect,
      int cellCount,
      int riverMaxWidth
   ) {
      if (aspect >= 4.5) {
         return false;
      }
      int minimumLakeWidth = Math.max(12, (int)Math.round(riverMaxWidth * 0.75));
      if (minDimensionBlocks < minimumLakeWidth) {
         return false;
      }
      int boundsArea = widthCells * heightCells;
      return boundsArea > 0 && (double)cellCount / boundsArea >= 0.6;
   }

   private static int[] distanceFromShore(
      int gridSize,
      boolean[] inlandWater,
      boolean[] oceanWater,
      boolean[] waterfallDrop,
      int[] componentIds
   ) {
      int area = gridSize * gridSize;
      int[] distance = new int[area];
      Arrays.fill(distance, -1);
      IntArrayList queue = new IntArrayList();
      for (int index = 0; index < area; index++) {
         if (!inlandWater[index]) {
            continue;
         }
         int x = index % gridSize;
         int z = index / gridSize;
         boolean shore = false;
         for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
            int nx = x + CARDINAL_OFFSETS[offset];
            int nz = z + CARDINAL_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               shore = true;
               break;
            }
            int neighbor = nz * gridSize + nx;
            if (!inlandWater[neighbor] || oceanWater[neighbor] || waterfallDrop[neighbor]) {
               shore = true;
               break;
            }
         }
         if (shore) {
            distance[index] = 0;
            queue.add(index);
         }
      }

      for (int cursor = 0; cursor < queue.size(); cursor++) {
         int cell = queue.getInt(cursor);
         int x = cell % gridSize;
         int z = cell / gridSize;
         for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
            int nx = x + CARDINAL_OFFSETS[offset];
            int nz = z + CARDINAL_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (inlandWater[neighbor]
               && distance[neighbor] < 0
               && componentIds[neighbor] == componentIds[cell]) {
               distance[neighbor] = distance[cell] + 1;
               queue.add(neighbor);
            }
         }
      }
      return distance;
   }

   private static int localComponentSlope(
      int index,
      int gridSize,
      int[] terrain,
      int[] componentIds,
      int componentId
   ) {
      int slope = 0;
      int x = index % gridSize;
      int z = index / gridSize;
      for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
         int nx = x + CARDINAL_OFFSETS[offset];
         int nz = z + CARDINAL_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
            int neighbor = nz * gridSize + nx;
            if (componentIds[neighbor] == componentId) {
               slope = Math.max(slope, Math.abs(terrain[index] - terrain[neighbor]));
            }
         }
      }
      return slope;
   }

   private static void applyAdaptiveBankBlend(
      int gridSize,
      int cellSizeBlocks,
      int[] terrainSurface,
      int[] rawTerrain,
      int[] waterSurface,
      boolean[] mappedInlandWater,
      boolean[] inlandWater,
      boolean[] oceanWater,
      boolean[] waterfallDrop,
      boolean[] correctionRequired,
      int[] adaptiveBlendRadius,
      boolean[] waterfallProtection,
      int cliffSlopeThreshold,
      boolean limitCliffSlope
   ) {
      int area = gridSize * gridSize;
      boolean[] blockedLand = new boolean[area];
      boolean[] cliffLand = new boolean[area];
      for (int index = 0; index < area; index++) {
         blockedLand[index] = mappedInlandWater[index] && !inlandWater[index] && !waterfallDrop[index];
      }
      for (int index = 0; index < area; index++) {
         if (!inlandWater[index]) {
            continue;
         }
         int x = index % gridSize;
         int z = index / gridSize;
         for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
            int nx = x + CARDINAL_OFFSETS[offset];
            int nz = z + CARDINAL_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (!inlandWater[neighbor]
               && !oceanWater[neighbor]
               && rawTerrain[neighbor] - rawTerrain[index] >= cliffSlopeThreshold) {
               cliffLand[neighbor] = true;
            }
         }
      }

      int[] remainingInfluence = new int[area];
      int[] sourceRadius = new int[area];
      int[] nearestSurface = new int[area];
      Arrays.fill(remainingInfluence, -1);
      Arrays.fill(nearestSurface, Integer.MAX_VALUE);
      PriorityQueue<Influence> queue = new PriorityQueue<>();
      int cardinalCost = DIST_COST_CARDINAL * cellSizeBlocks;

      for (int index = 0; index < area; index++) {
         int radius = adaptiveBlendRadius[index];
         if (!inlandWater[index]
            || !correctionRequired[index]
            || radius <= 0
            || waterfallProtection[index]) {
            continue;
         }
         int initialRemaining = radius * DIST_COST_CARDINAL - cardinalCost;
         if (initialRemaining < 0) {
            continue;
         }
         int x = index % gridSize;
         int z = index / gridSize;
         for (int offset = 0; offset < CARDINAL_OFFSETS.length; offset += 2) {
            int nx = x + CARDINAL_OFFSETS[offset];
            int nz = z + CARDINAL_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (!eligibleBlendLand(neighbor, inlandWater, oceanWater, waterfallDrop, waterfallProtection, blockedLand, cliffLand)) {
               continue;
            }
            if (shouldReplaceInfluence(initialRemaining, waterSurface[index], remainingInfluence[neighbor], nearestSurface[neighbor])) {
               remainingInfluence[neighbor] = initialRemaining;
               sourceRadius[neighbor] = radius;
               nearestSurface[neighbor] = waterSurface[index];
               queue.add(new Influence(neighbor, initialRemaining));
            }
         }
      }

      while (!queue.isEmpty()) {
         Influence influence = queue.remove();
         int index = influence.index();
         int remaining = influence.remaining();
         if (remainingInfluence[index] != remaining || remaining <= 0) {
            continue;
         }
         int x = index % gridSize;
         int z = index / gridSize;
         for (int offset = 0; offset < NEIGHBOR_OFFSETS_8.length; offset += 2) {
            int nx = x + NEIGHBOR_OFFSETS_8[offset];
            int nz = z + NEIGHBOR_OFFSETS_8[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (!eligibleBlendLand(neighbor, inlandWater, oceanWater, waterfallDrop, waterfallProtection, blockedLand, cliffLand)) {
               continue;
            }
            int stepCost = NEIGHBOR_COSTS_8[offset / 2] * cellSizeBlocks;
            int nextRemaining = remaining - stepCost;
            if (nextRemaining < 0
               || !shouldReplaceInfluence(nextRemaining, nearestSurface[index], remainingInfluence[neighbor], nearestSurface[neighbor])) {
               continue;
            }
            remainingInfluence[neighbor] = nextRemaining;
            sourceRadius[neighbor] = sourceRadius[index];
            nearestSurface[neighbor] = nearestSurface[index];
            queue.add(new Influence(neighbor, nextRemaining));
         }
      }

      for (int index = 0; index < area; index++) {
         int radius = sourceRadius[index];
         if (radius <= 0 || remainingInfluence[index] < 0 || waterfallProtection[index]) {
            continue;
         }
         int distanceCost = radius * DIST_COST_CARDINAL - remainingInfluence[index];
         int naturalized = WaterSurfaceResolver.naturalizedInlandBankSurface(
            rawTerrain[index],
            nearestSurface[index],
            distanceCost,
            radius,
            limitCliffSlope
         );
         terrainSurface[index] = Math.min(terrainSurface[index], naturalized);
      }
   }

   private static boolean eligibleBlendLand(
      int index,
      boolean[] inlandWater,
      boolean[] oceanWater,
      boolean[] waterfallDrop,
      boolean[] waterfallProtection,
      boolean[] blockedLand,
      boolean[] cliffLand
   ) {
      return !inlandWater[index]
         && !oceanWater[index]
         && !waterfallDrop[index]
         && !waterfallProtection[index]
         && !blockedLand[index]
         && !cliffLand[index];
   }

   private static boolean shouldReplaceInfluence(
      int remaining,
      int waterSurface,
      int existingRemaining,
      int existingSurface
   ) {
      return remaining > existingRemaining || remaining == existingRemaining && waterSurface < existingSurface;
   }

   private static int checkedArea(int gridSize) {
      if (gridSize <= 0 || gridSize > 46340) {
         throw new IllegalArgumentException("Invalid preview water grid size: " + gridSize);
      }
      return gridSize * gridSize;
   }

   private static void requireLength(int[] values, int expected, String label) {
      if (values == null || values.length < expected) {
         throw new IllegalArgumentException("Preview " + label + " must contain " + expected + " cells");
      }
   }

   private static void requireLength(boolean[] values, int expected, String label) {
      if (values == null || values.length < expected) {
         throw new IllegalArgumentException("Preview " + label + " must contain " + expected + " cells");
      }
   }

   private static int divideRoundUp(int value, int divisor) {
      return value <= 0 ? 0 : (value + divisor - 1) / divisor;
   }

   record Result(
      int[] terrainSurface,
      int[] waterSurface,
      boolean[] inlandWater,
      boolean[] waterfallDrop,
      boolean[] waterfallProtection
   ) {
   }

   private record Influence(int index, int remaining) implements Comparable<Influence> {
      @Override
      public int compareTo(Influence other) {
         int byRemaining = Integer.compare(other.remaining, this.remaining);
         return byRemaining != 0 ? byRemaining : Integer.compare(this.index, other.index);
      }
   }
}
