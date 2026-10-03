package com.yucareux.tellus.worldgen;

import java.util.Arrays;

/**
 * Builds hydro-flat reaches for a connected inland-water polygon. The mapped
 * geometry constrains where water may exist and DEM heights determine flow
 * direction and genuine major drops. Between those drops, every four-connected
 * water cell receives exactly one surface height: Minecraft source-water
 * columns at different heights cannot form a stable river surface.
 */
final class InlandWaterFlowAnalyzer {
   private static final int[] NEIGHBOR_OFFSETS = new int[]{1, 0, -1, 0, 0, 1, 0, -1};
   private static final int FLOW_STEP_COST = 10;
   private static final int UPHILL_ROUTE_PENALTY = 80;
   private static final int OUTLET_HEIGHT_BAND = 1;
   private static final int SMOOTHED_CLIFF_PLATEAU_BLOCKS = 8;
   private static final int SMOOTHED_CLIFF_ENTRY_BLOCKS = 12;
   private static final int SMOOTHED_CLIFF_MIN_LOOKAHEAD_BLOCKS = 64;
   private static final int SMOOTHED_CLIFF_HYDRO_LOOKAHEAD_MULTIPLIER = 3;
   private static final int SMOOTHED_CLIFF_WATERFALL_LOOKAHEAD_MULTIPLIER = 12;
   private static final int SMOOTHED_CLIFF_MIN_DROP_PER_FIVE_BLOCKS = 3;

   private InlandWaterFlowAnalyzer() {
   }

   static void analyze(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int[] rawTerrain,
      int[] surfaceCeiling,
      boolean[] oceanMask,
      int cellSizeBlocks,
      int channelWidthBlocks,
      Parameters parameters,
      int[] plannedWaterSurface,
      boolean[] activeWater,
      boolean[] waterfallDrop,
      int[] waterfallTop,
      boolean[] correctionRequired,
      int[] adaptiveBlendRadius,
      boolean[] waterfallProtection,
      Workspace workspace
   ) {
      if (componentCount <= 0) {
         return;
      }
      int area = gridSize * gridSize;
      if (gridSize <= 0
         || componentCount > componentCells.length
         || rawTerrain.length < area
         || surfaceCeiling.length < area
         || oceanMask.length < area
         || plannedWaterSurface.length < area
         || activeWater.length < area
         || waterfallDrop.length < area
         || waterfallTop.length < area
         || correctionRequired.length < area
         || adaptiveBlendRadius.length < area
         || waterfallProtection.length < area) {
         throw new IllegalArgumentException("Invalid inland-water flow analysis dimensions");
      }

      workspace.ensureCapacity(area, componentCount);
      workspace.reset(componentCells, componentCount);
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         workspace.member[cell] = true;
      }
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         activeWater[cell] = true;
         waterfallDrop[cell] = false;
         waterfallTop[cell] = Integer.MIN_VALUE;
         correctionRequired[cell] = false;
         adaptiveBlendRadius[cell] = 0;
         workspace.parent[cell] = -1;
         workspace.cost[cell] = Long.MAX_VALUE;
         workspace.settled[cell] = false;
         workspace.edgeWaterfall[cell] = false;
         workspace.demCliffDrop[cell] = false;
         workspace.demCliffUpstream[cell] = -1;
         workspace.majorDrop[cell] = false;
         workspace.reachVisited[cell] = false;
         workspace.outletSeed[cell] = false;
         workspace.hydroLevel[cell] = Integer.MAX_VALUE;

         int median = localMedianSurface(cell, gridSize, surfaceCeiling, workspace.member, workspace.localSamples);
         int target = Math.min(rawTerrain[cell], Math.min(surfaceCeiling[cell], median));
         workspace.target[cell] = target;
         workspace.monotone[cell] = target;
         workspace.planned[cell] = target;
      }

      seedDownstreamOutlets(
         gridSize,
         componentCells,
         componentCount,
         oceanMask,
         workspace
      );
      buildFlowTree(gridSize, componentCells, componentCount, workspace);

      int maxProfileCut = Math.max(0, parameters.maxProfileCut());
      int hydroLookAheadCells = Math.max(
         2,
         divideRoundUp(parameters.hydroFlattenLookAheadBlocks(), Math.max(1, cellSizeBlocks))
      );
      resolveBracketedDemHumps(
         gridSize,
         componentCells,
         componentCount,
         rawTerrain,
         hydroLookAheadCells,
         correctionRequired,
         workspace
      );

      if (parameters.inferWaterfalls()) {
         int waterfallDropBlocks = Math.max(2, parameters.waterfallMinDrop());
         int lookAheadCells = Math.max(2, divideRoundUp(parameters.waterfallLookAheadBlocks(), Math.max(1, cellSizeBlocks)));
         detectDemCliffFronts(
            gridSize,
            componentCells,
            componentCount,
            waterfallDropBlocks,
            workspace
         );
         detectSmoothedDemCliffRamps(
            gridSize,
            componentCells,
            componentCount,
            Math.max(1, cellSizeBlocks),
            waterfallDropBlocks,
            parameters,
            workspace
         );
         for (int i = 0; i < workspace.orderCount; i++) {
            int cell = workspace.order[i];
            int parent = workspace.parent[cell];
            if (parent >= 0
               && workspace.target[cell] - workspace.target[parent] >= waterfallDropBlocks
               && isSupportedFlowDrop(cell, parent, gridSize, workspace)) {
               workspace.edgeWaterfall[cell] = true;
            }
         }
         detectShortReachWaterfalls(
            gridSize,
            componentCells,
            componentCount,
            waterfallDropBlocks,
            lookAheadCells,
            workspace
         );
      }

      // Correct DEM inversions before splitting genuine cliff drops. Polygon
      // water has no carving ceiling: a lower downstream anchor must win even
      // when the source DEM would otherwise send Minecraft water upstream.
      for (int orderIndex = workspace.orderCount - 1; orderIndex >= 0; orderIndex--) {
         int cell = workspace.order[orderIndex];
         int parent = workspace.parent[cell];
         if (parent < 0
            || workspace.edgeWaterfall[cell]
            || workspace.demCliffDrop[cell]
            || workspace.demCliffDrop[parent]
            || waterfallDrop[cell]
            || waterfallDrop[parent]) {
            continue;
         }
         if (workspace.monotone[parent] > workspace.monotone[cell]) {
            workspace.monotone[parent] = workspace.monotone[cell];
            correctionRequired[parent] = true;
         }
      }

      markWaterfallDropCells(componentCells, componentCount, activeWater, waterfallDrop, workspace);

      // Tree branches in a wide polygon can split and rejoin. Flatten by actual
      // four-connected reach, not by parent branch, so every touching ordinary
      // water column has the same height. Only a DEM-confirmed waterfall drop
      // cell is allowed to break that connectivity.
      flattenConnectedReaches(
         gridSize,
         componentCells,
         componentCount,
         rawTerrain,
         plannedWaterSurface,
         activeWater,
         waterfallDrop,
         correctionRequired,
         workspace
      );

      // Use the upstream source level for the DH visual column. Full-detail
      // generation leaves these cells dry and lets Minecraft fluid physics fall.
      for (int i = 0; i < workspace.orderCount; i++) {
         int cell = workspace.order[i];
         if (!workspace.edgeWaterfall[cell]) {
            continue;
         }
         int dropCell = workspace.parent[cell];
         if (dropCell >= 0) {
            int top = workspace.planned[cell];
            waterfallTop[dropCell] = waterfallTop[dropCell] == Integer.MIN_VALUE
               ? top
               : Math.max(waterfallTop[dropCell], top);
         }
      }
      for (int i = 0; i < componentCount; i++) {
         int dropCell = componentCells[i];
         if (!workspace.demCliffDrop[dropCell]) {
            continue;
         }
         int upstream = workspace.demCliffUpstream[dropCell];
         if (upstream >= 0) {
            int top = workspace.planned[upstream];
            waterfallTop[dropCell] = waterfallTop[dropCell] == Integer.MIN_VALUE
               ? top
               : Math.max(waterfallTop[dropCell], top);
         }
      }
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         if (waterfallDrop[cell]) {
            activeWater[cell] = false;
            correctionRequired[cell] = false;
            plannedWaterSurface[cell] = waterfallTop[cell] == Integer.MIN_VALUE
               ? rawTerrain[cell]
               : waterfallTop[cell];
         }
      }

      int maximumAdaptiveRadius = Math.max(parameters.baseBlendBlocks(), parameters.maxAdaptiveBlendBlocks());
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         if (!activeWater[cell] || waterfallDrop[cell] || !correctionRequired[cell]) {
            continue;
         }
         int cut = Math.max(0, rawTerrain[cell] - plannedWaterSurface[cell]);
         int blendCut = Math.min(cut, maxProfileCut);
         int localSlope = localMaximumSlope(cell, gridSize, rawTerrain, workspace.member);
         int widthContribution = Math.min(12, Math.max(0, channelWidthBlocks) / 3);
         int radius = parameters.baseBlendBlocks()
            + blendCut * 3
            + Math.min(12, localSlope * 2)
            + widthContribution;
         adaptiveBlendRadius[cell] = Math.min(maximumAdaptiveRadius, Math.max(parameters.baseBlendBlocks(), radius));
      }

      int protectionCells = divideRoundUp(parameters.waterfallProtectionBlocks(), Math.max(1, cellSizeBlocks));
      for (int i = 0; i < componentCount; i++) {
         int dropCell = componentCells[i];
         if (workspace.majorDrop[dropCell]) {
            waterfallProtection[dropCell] = true;
            adaptiveBlendRadius[dropCell] = 0;
         }
      }
      if (protectionCells > 0) {
         for (int i = 0; i < componentCount; i++) {
            int dropCell = componentCells[i];
            if (!workspace.majorDrop[dropCell]) {
               continue;
            }
            int dropX = dropCell % gridSize;
            int dropZ = dropCell / gridSize;
            for (int dz = -protectionCells; dz <= protectionCells; dz++) {
               int z = dropZ + dz;
               if (z < 0 || z >= gridSize) {
                  continue;
               }
               for (int dx = -protectionCells; dx <= protectionCells; dx++) {
                  int x = dropX + dx;
                  if (x >= 0 && x < gridSize) {
                     int protectedCell = z * gridSize + x;
                     waterfallProtection[protectedCell] = true;
                     adaptiveBlendRadius[protectedCell] = 0;
                  }
               }
            }
         }
      }

      workspace.clearMembership(componentCells, componentCount);
   }

   private static void seedDownstreamOutlets(
      int gridSize,
      int[] componentCells,
      int componentCount,
      boolean[] oceanMask,
      Workspace workspace
   ) {
      boolean hasOceanCandidate = false;
      boolean hasGridEdgeCandidate = false;
      boolean hasTipCandidate = false;
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         hasOceanCandidate |= touchesMask(cell, gridSize, oceanMask);
         hasGridEdgeCandidate |= isGridEdge(cell, gridSize);
         hasTipCandidate |= memberNeighborCount(cell, gridSize, workspace.member) <= 1;
      }

      int minimumOutlet = Integer.MAX_VALUE;
      int lowestCandidate = -1;
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         if (isOutletCandidate(cell, gridSize, oceanMask, workspace.member, hasOceanCandidate, hasGridEdgeCandidate, hasTipCandidate)) {
            if (workspace.target[cell] < minimumOutlet
               || workspace.target[cell] == minimumOutlet && (lowestCandidate < 0 || cell < lowestCandidate)) {
               minimumOutlet = workspace.target[cell];
               lowestCandidate = cell;
            }
         }
      }
      if (minimumOutlet == Integer.MAX_VALUE) {
         for (int i = 0; i < componentCount; i++) {
            int cell = componentCells[i];
            if (workspace.target[cell] < minimumOutlet
               || workspace.target[cell] == minimumOutlet && (lowestCandidate < 0 || cell < lowestCandidate)) {
               minimumOutlet = workspace.target[cell];
               lowestCandidate = cell;
            }
         }
      }

      if (hasOceanCandidate) {
         for (int i = 0; i < componentCount; i++) {
            int cell = componentCells[i];
            if (workspace.target[cell] == minimumOutlet
               && isOutletCandidate(cell, gridSize, oceanMask, workspace.member, true, hasGridEdgeCandidate, hasTipCandidate)) {
               workspace.cost[cell] = 0L;
               workspace.push(cell, 0L);
            }
         }
      } else if (lowestCandidate >= 0) {
         // A truncated channel can expose an outlet front several cells wide.
         // Seed that one connected front, never two opposite river ends whose
         // elevations merely happen to fall in the same rounding band.
         int queueHead = 0;
         int queueTail = 0;
         workspace.order[queueTail++] = lowestCandidate;
         workspace.outletSeed[lowestCandidate] = true;
         while (queueHead < queueTail) {
            int cell = workspace.order[queueHead++];
            workspace.cost[cell] = 0L;
            workspace.push(cell, 0L);
            int x = cell % gridSize;
            int z = cell / gridSize;
            for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
               int nx = x + NEIGHBOR_OFFSETS[offset];
               int nz = z + NEIGHBOR_OFFSETS[offset + 1];
               if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                  continue;
               }
               int neighbor = nz * gridSize + nx;
               if (!workspace.outletSeed[neighbor]
                  && workspace.member[neighbor]
                  && workspace.target[neighbor] == minimumOutlet
                  && isOutletCandidate(
                     neighbor,
                     gridSize,
                     oceanMask,
                     workspace.member,
                     false,
                     hasGridEdgeCandidate,
                     hasTipCandidate
                  )) {
                  workspace.outletSeed[neighbor] = true;
                  workspace.order[queueTail++] = neighbor;
               }
            }
         }
         for (int i = 0; i < queueTail; i++) {
            workspace.outletSeed[workspace.order[i]] = false;
         }
      }
      if (workspace.heapSize == 0) {
         int lowestCell = lowestCandidate >= 0 ? lowestCandidate : componentCells[0];
         workspace.cost[lowestCell] = 0L;
         workspace.push(lowestCell, 0L);
      }
   }

   private static boolean isOutletCandidate(
      int cell,
      int gridSize,
      boolean[] oceanMask,
      boolean[] member,
      boolean hasOceanCandidate,
      boolean hasGridEdgeCandidate,
      boolean hasTipCandidate
   ) {
      if (hasOceanCandidate) {
         return touchesMask(cell, gridSize, oceanMask);
      }
      if (hasGridEdgeCandidate) {
         return isGridEdge(cell, gridSize);
      }
      if (hasTipCandidate) {
         return memberNeighborCount(cell, gridSize, member) <= 1;
      }
      return true;
   }

   private static void buildFlowTree(
      int gridSize,
      int[] componentCells,
      int componentCount,
      Workspace workspace
   ) {
      while (workspace.heapSize > 0) {
         int cell = workspace.pop();
         long cellCost = workspace.lastPoppedCost;
         if (workspace.settled[cell] || cellCost != workspace.cost[cell]) {
            continue;
         }
         workspace.settled[cell] = true;
         workspace.order[workspace.orderCount++] = cell;
         int x = cell % gridSize;
         int z = cell / gridSize;
         for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
            int nx = x + NEIGHBOR_OFFSETS[offset];
            int nz = z + NEIGHBOR_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (!workspace.member[neighbor] || workspace.settled[neighbor]) {
               continue;
            }
            int uphillViolation = Math.max(0, workspace.target[cell] - workspace.target[neighbor]);
            long nextCost = cellCost + FLOW_STEP_COST + (long)uphillViolation * UPHILL_ROUTE_PENALTY;
            if (nextCost < workspace.cost[neighbor]) {
               workspace.cost[neighbor] = nextCost;
               workspace.parent[neighbor] = cell;
               workspace.push(neighbor, nextCost);
            }
         }
      }

      // Components are four-connected before analysis. This is a defensive
      // fallback for malformed callers so no cell retains an undefined profile.
      if (workspace.orderCount < componentCount) {
         for (int i = 0; i < componentCount; i++) {
            int cell = componentCells[i];
            if (!workspace.settled[cell]) {
               workspace.order[workspace.orderCount++] = cell;
            }
         }
      }
   }

   /**
    * Resolves a DEM ridge only when a low, supported reach exists on both
    * sides. A bracketed ridge is mapping/DEM disagreement rather than a natural
    * waterfall, so the polygon reach cuts through it regardless of depth.
    */
   private static void resolveBracketedDemHumps(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int[] rawTerrain,
      int lookAheadCells,
      boolean[] correctionRequired,
      Workspace workspace
   ) {
      for (int i = 0; i < componentCount; i++) {
         int upstreamAnchor = componentCells[i];
         int anchorSurface = workspace.target[upstreamAnchor];
         if (!hasSupportedUpstreamLevel(upstreamAnchor, anchorSurface, gridSize, workspace)) {
            continue;
         }

         int cursor = workspace.parent[upstreamAnchor];
         int maximumReturnSurface = anchorSurface + OUTLET_HEIGHT_BAND;
         if (cursor < 0 || workspace.target[cursor] <= maximumReturnSurface) {
            continue;
         }

         int steps = 0;
         while (cursor >= 0 && steps < lookAheadCells && workspace.target[cursor] > maximumReturnSurface) {
            cursor = workspace.parent[cursor];
            steps++;
         }
         if (cursor < 0
            || steps == 0
            || workspace.target[cursor] > maximumReturnSurface
            || !hasSupportedDownstreamLevel(cursor, anchorSurface, gridSize, workspace)) {
            continue;
         }

         int humpCell = workspace.parent[upstreamAnchor];
         while (humpCell >= 0 && humpCell != cursor) {
            workspace.hydroLevel[humpCell] = Math.min(workspace.hydroLevel[humpCell], anchorSurface);
            humpCell = workspace.parent[humpCell];
         }
      }

      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         int hydroLevel = workspace.hydroLevel[cell];
         if (hydroLevel == Integer.MAX_VALUE) {
            continue;
         }
         workspace.target[cell] = Math.min(workspace.target[cell], hydroLevel);
         workspace.monotone[cell] = workspace.target[cell];
         workspace.planned[cell] = workspace.target[cell];
         correctionRequired[cell] = rawTerrain[cell] > workspace.target[cell];
      }
   }

   private static boolean hasSupportedUpstreamLevel(
      int cell, int surface, int gridSize, Workspace workspace
   ) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
            continue;
         }
         int neighbor = nz * gridSize + nx;
         if (workspace.member[neighbor]
            && workspace.parent[neighbor] == cell
            && workspace.target[neighbor] <= surface + OUTLET_HEIGHT_BAND) {
            return true;
         }
      }
      return false;
   }

   private static boolean hasSupportedDownstreamLevel(
      int cell, int surface, int gridSize, Workspace workspace
   ) {
      if (workspace.target[cell] > surface + OUTLET_HEIGHT_BAND) {
         return false;
      }
      int parent = workspace.parent[cell];
      if (parent < 0) {
         return hasMemberNeighborOtherThan(cell, -1, gridSize, workspace.member);
      }
      return workspace.target[parent] <= surface + OUTLET_HEIGHT_BAND;
   }

   private static boolean isSupportedFlowDrop(
      int upstream, int downstream, int gridSize, Workspace workspace
   ) {
      if (upstream < 0
         || downstream < 0
         || !hasWatercourseContinuation(upstream, downstream, gridSize, workspace.member)) {
         return false;
      }
      return hasUpstreamDropSupport(upstream, downstream, gridSize, workspace)
         && hasDownstreamDropSupport(downstream, upstream, gridSize, workspace);
   }

   /**
    * Finds an abrupt, supported DEM front without consulting the inferred flow
    * tree. A clipped analysis window can expose only the upper river plateau;
    * in that case the boundary seed points the tree upstream and a real
    * waterfall would otherwise be hydro-flattened away. Bracketed DEM humps
    * have already been resolved before this pass, so this only preserves a
    * remaining high-to-low cliff with continuing water on both sides.
    */
   private static void detectDemCliffFronts(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int waterfallDropBlocks,
      Workspace workspace
   ) {
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         int x = cell % gridSize;
         int z = cell / gridSize;
         for (int offset = 0; offset <= 4; offset += 4) {
            int nx = x + NEIGHBOR_OFFSETS[offset];
            int nz = z + NEIGHBOR_OFFSETS[offset + 1];
            if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
               continue;
            }
            int neighbor = nz * gridSize + nx;
            if (!workspace.member[neighbor]) {
               continue;
            }

            int upstream = cell;
            int downstream = neighbor;
            if (workspace.target[upstream] < workspace.target[downstream]) {
               upstream = neighbor;
               downstream = cell;
            }
            if (workspace.target[upstream] - workspace.target[downstream] < waterfallDropBlocks
               || !isSupportedDemCliff(upstream, downstream, gridSize, workspace)) {
               continue;
            }

            workspace.demCliffDrop[downstream] = true;
            int previousUpstream = workspace.demCliffUpstream[downstream];
            if (previousUpstream < 0
               || workspace.target[upstream] > workspace.target[previousUpstream]) {
               workspace.demCliffUpstream[downstream] = upstream;
            }
         }
      }
   }

   private static boolean isSupportedDemCliff(
      int upstream, int downstream, int gridSize, Workspace workspace
   ) {
      if (!hasWatercourseContinuation(upstream, downstream, gridSize, workspace.member)) {
         return false;
      }
      return hasDemLevelSupport(upstream, downstream, true, gridSize, workspace)
         && hasDemLevelSupport(downstream, upstream, false, gridSize, workspace);
   }

   private static boolean hasDemLevelSupport(
      int cell, int excluded, boolean highSide, int gridSize, Workspace workspace
   ) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      int surface = workspace.target[cell];
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
            continue;
         }
         int neighbor = nz * gridSize + nx;
         if (neighbor == excluded || !workspace.member[neighbor]) {
            continue;
         }
         int neighborSurface = workspace.target[neighbor];
         if (highSide ? neighborSurface >= surface - 2 : neighborSurface <= surface + 2) {
            return true;
         }
      }
      return false;
   }

   /**
    * Mapterhorn preserves the elevation loss at a waterfall, but source-pixel
    * interpolation can spread that loss over dozens of one-block samples. No
    * individual DEM edge then reaches the waterfall threshold. Detect a large
    * one-way descent between supported upper and lower plateaus and preserve
    * the complete transition band so it cannot be hydro-flattened into either
    * pool. A comparable low reach on the opposite side identifies a bracketed
    * DEM ridge instead; those remain ordinary hydro-flat corrections.
    */
   private static void detectSmoothedDemCliffRamps(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int cellSizeBlocks,
      int waterfallDropBlocks,
      Parameters parameters,
      Workspace workspace
   ) {
      int plateauCells = Math.max(2, divideRoundUp(SMOOTHED_CLIFF_PLATEAU_BLOCKS, cellSizeBlocks));
      int entryCells = Math.max(2, divideRoundUp(SMOOTHED_CLIFF_ENTRY_BLOCKS, cellSizeBlocks));
      int lookAheadBlocks = Math.max(
         SMOOTHED_CLIFF_MIN_LOOKAHEAD_BLOCKS,
         Math.max(
            parameters.hydroFlattenLookAheadBlocks() * SMOOTHED_CLIFF_HYDRO_LOOKAHEAD_MULTIPLIER,
            parameters.waterfallLookAheadBlocks() * SMOOTHED_CLIFF_WATERFALL_LOOKAHEAD_MULTIPLIER
         )
      );
      int lookAheadCells = Math.max(
         plateauCells * 2 + entryCells,
         divideRoundUp(lookAheadBlocks, cellSizeBlocks)
      );
      int minimumRampDrop = Math.max(
         waterfallDropBlocks + 4,
         waterfallDropBlocks * 2
      );

      for (int i = 0; i < componentCount; i++) {
         int upperPlateauCell = componentCells[i];
         int upperSurface = workspace.target[upperPlateauCell];
         for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
            int dx = NEIGHBOR_OFFSETS[offset];
            int dz = NEIGHBOR_OFFSETS[offset + 1];
            if (!hasDirectionalPlateau(
               upperPlateauCell,
               -dx,
               -dz,
               plateauCells,
               upperSurface,
               gridSize,
               workspace
            )) {
               continue;
            }

            int transitionStart = -1;
            for (int distance = 1; distance <= entryCells; distance++) {
               int cell = directionalCell(upperPlateauCell, dx, dz, distance, gridSize);
               if (cell < 0 || !workspace.member[cell]) {
                  break;
               }
               int surface = workspace.target[cell];
               if (surface > upperSurface + OUTLET_HEIGHT_BAND) {
                  transitionStart = -1;
                  break;
               }
               if (surface < upperSurface - OUTLET_HEIGHT_BAND) {
                  transitionStart = Math.max(1, distance - 1);
                  break;
               }
            }
            if (transitionStart < 0) {
               continue;
            }

            int lowerPlateauDistance = -1;
            int lowerPlateauSurface = Integer.MAX_VALUE;
            for (int distance = transitionStart; distance <= lookAheadCells; distance++) {
               int cell = directionalCell(upperPlateauCell, dx, dz, distance, gridSize);
               if (cell < 0 || !workspace.member[cell]) {
                  break;
               }
               int surface = workspace.target[cell];
               if (surface > upperSurface + OUTLET_HEIGHT_BAND) {
                  break;
               }
               if (upperSurface - surface < minimumRampDrop
                  || !hasDirectionalPlateau(
                     cell,
                     dx,
                     dz,
                     plateauCells,
                     surface,
                     gridSize,
                     workspace
                  )) {
                  continue;
               }
               if (surface < lowerPlateauSurface
                  || surface == lowerPlateauSurface && distance > lowerPlateauDistance) {
                  lowerPlateauSurface = surface;
                  lowerPlateauDistance = distance;
               }
            }
            if (lowerPlateauDistance < 0
               || hasDirectionalLowReturn(
                  upperPlateauCell,
                  -dx,
                  -dz,
                  lookAheadCells,
                  lowerPlateauSurface,
                  gridSize,
                  workspace
               )) {
               continue;
            }

            int transitionEnd = lowerPlateauDistance;
            while (transitionEnd > transitionStart) {
               int cell = directionalCell(upperPlateauCell, dx, dz, transitionEnd, gridSize);
               if (cell < 0
                  || !workspace.member[cell]
                  || workspace.target[cell] > lowerPlateauSurface + OUTLET_HEIGHT_BAND) {
                  break;
               }
               transitionEnd--;
            }
            int transitionBlocks = Math.max(1, transitionEnd - transitionStart + 1) * cellSizeBlocks;
            if ((long)(upperSurface - lowerPlateauSurface) * 5L
               < (long)transitionBlocks * SMOOTHED_CLIFF_MIN_DROP_PER_FIVE_BLOCKS) {
               continue;
            }
            for (int distance = transitionStart; distance <= transitionEnd; distance++) {
               int dropCell = directionalCell(upperPlateauCell, dx, dz, distance, gridSize);
               if (dropCell < 0 || !workspace.member[dropCell]) {
                  break;
               }
               workspace.demCliffDrop[dropCell] = true;
               int localUpstream = directionalCell(upperPlateauCell, dx, dz, distance - 1, gridSize);
               int previousUpstream = workspace.demCliffUpstream[dropCell];
               if (previousUpstream < 0
                  || workspace.target[localUpstream] > workspace.target[previousUpstream]) {
                  workspace.demCliffUpstream[dropCell] = localUpstream;
               }
            }
         }
      }
   }

   private static boolean hasDirectionalPlateau(
      int cell,
      int dx,
      int dz,
      int supportCells,
      int surface,
      int gridSize,
      Workspace workspace
   ) {
      for (int distance = 1; distance <= supportCells; distance++) {
         int support = directionalCell(cell, dx, dz, distance, gridSize);
         if (support < 0
            || !workspace.member[support]
            || Math.abs(workspace.target[support] - surface) > OUTLET_HEIGHT_BAND) {
            return false;
         }
      }
      return true;
   }

   private static boolean hasDirectionalLowReturn(
      int cell,
      int dx,
      int dz,
      int lookAheadCells,
      int lowerPlateauSurface,
      int gridSize,
      Workspace workspace
   ) {
      for (int distance = 1; distance <= lookAheadCells; distance++) {
         int opposite = directionalCell(cell, dx, dz, distance, gridSize);
         if (opposite < 0 || !workspace.member[opposite]) {
            return false;
         }
         if (workspace.target[opposite] <= lowerPlateauSurface + OUTLET_HEIGHT_BAND) {
            return true;
         }
      }
      return false;
   }

   private static int directionalCell(int cell, int dx, int dz, int distance, int gridSize) {
      int x = cell % gridSize + dx * distance;
      int z = cell / gridSize + dz * distance;
      return x < 0 || z < 0 || x >= gridSize || z >= gridSize ? -1 : z * gridSize + x;
   }

   private static boolean hasUpstreamDropSupport(
      int cell, int excluded, int gridSize, Workspace workspace
   ) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      int minimumSupportedSurface = workspace.target[cell] - 2;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
            continue;
         }
         int neighbor = nz * gridSize + nx;
         if (neighbor != excluded
            && workspace.member[neighbor]
            && workspace.cost[neighbor] > workspace.cost[cell]
            && workspace.target[neighbor] >= minimumSupportedSurface) {
            return true;
         }
      }
      return false;
   }

   private static boolean hasDownstreamDropSupport(
      int cell, int excluded, int gridSize, Workspace workspace
   ) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      int maximumSupportedSurface = workspace.target[cell] + 2;
      boolean outlet = workspace.parent[cell] < 0;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
            continue;
         }
         int neighbor = nz * gridSize + nx;
         if (neighbor != excluded
            && workspace.member[neighbor]
            && (outlet || workspace.cost[neighbor] < workspace.cost[cell])
            && workspace.target[neighbor] <= maximumSupportedSurface) {
            return true;
         }
      }
      return false;
   }

   private static void detectShortReachWaterfalls(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int waterfallDropBlocks,
      int lookAheadCells,
      Workspace workspace
   ) {
      for (int i = 0; i < componentCount; i++) {
         int upstream = componentCells[i];
         int cursor = upstream;
         int strongestEdgeCell = -1;
         int strongestDrop = Integer.MIN_VALUE;
         int steps = 0;
         while (steps < lookAheadCells && workspace.parent[cursor] >= 0) {
            int parent = workspace.parent[cursor];
            int edgeDrop = workspace.target[cursor] - workspace.target[parent];
            if (edgeDrop > strongestDrop) {
               strongestDrop = edgeDrop;
               strongestEdgeCell = cursor;
            }
            cursor = parent;
            steps++;
         }
         if (steps < 2 || strongestEdgeCell < 0) {
            continue;
         }
         int reachDrop = workspace.target[upstream] - workspace.target[cursor];
         if (reachDrop >= waterfallDropBlocks
            && strongestDrop >= Math.max(2, waterfallDropBlocks / 2)
            && isSupportedFlowDrop(
               strongestEdgeCell,
               workspace.parent[strongestEdgeCell],
               gridSize,
               workspace
            )) {
            workspace.edgeWaterfall[strongestEdgeCell] = true;
         }
      }
   }

   private static void markWaterfallDropCells(
      int[] componentCells,
      int componentCount,
      boolean[] activeWater,
      boolean[] waterfallDrop,
      Workspace workspace
   ) {
      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         if (workspace.edgeWaterfall[cell]) {
            int dropCell = workspace.parent[cell];
            if (dropCell >= 0) {
               waterfallDrop[dropCell] = true;
               activeWater[dropCell] = false;
               workspace.majorDrop[dropCell] = true;
            }
         }
         if (workspace.demCliffDrop[cell]) {
            waterfallDrop[cell] = true;
            activeWater[cell] = false;
            workspace.majorDrop[cell] = true;
         }
      }
   }

   private static void flattenConnectedReaches(
      int gridSize,
      int[] componentCells,
      int componentCount,
      int[] rawTerrain,
      int[] plannedWaterSurface,
      boolean[] activeWater,
      boolean[] waterfallDrop,
      boolean[] correctionRequired,
      Workspace workspace
   ) {
      for (int i = 0; i < componentCount; i++) {
         int seed = componentCells[i];
         if (waterfallDrop[seed] || workspace.reachVisited[seed]) {
            continue;
         }

         int queueHead = 0;
         int queueTail = 0;
         int reachSurface = Integer.MAX_VALUE;
         workspace.reachQueue[queueTail++] = seed;
         workspace.reachVisited[seed] = true;
         while (queueHead < queueTail) {
            int cell = workspace.reachQueue[queueHead++];
            reachSurface = Math.min(reachSurface, workspace.monotone[cell]);
            int x = cell % gridSize;
            int z = cell / gridSize;
            for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
               int nx = x + NEIGHBOR_OFFSETS[offset];
               int nz = z + NEIGHBOR_OFFSETS[offset + 1];
               if (nx < 0 || nz < 0 || nx >= gridSize || nz >= gridSize) {
                  continue;
               }
               int neighbor = nz * gridSize + nx;
               if (workspace.member[neighbor]
                  && !waterfallDrop[neighbor]
                  && !workspace.reachVisited[neighbor]) {
                  workspace.reachVisited[neighbor] = true;
                  workspace.reachQueue[queueTail++] = neighbor;
               }
            }
         }

         for (int queueIndex = 0; queueIndex < queueTail; queueIndex++) {
            int cell = workspace.reachQueue[queueIndex];
            activeWater[cell] = true;
            workspace.planned[cell] = reachSurface;
            plannedWaterSurface[cell] = reachSurface;
            correctionRequired[cell] = rawTerrain[cell] > reachSurface;
         }
      }

      for (int i = 0; i < componentCount; i++) {
         int cell = componentCells[i];
         if (waterfallDrop[cell]) {
            activeWater[cell] = false;
            correctionRequired[cell] = false;
         }
      }
   }

   private static int localMedianSurface(
      int cell,
      int gridSize,
      int[] surfaces,
      boolean[] member,
      int[] samples
   ) {
      int count = 0;
      samples[count++] = surfaces[cell];
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
            int neighbor = nz * gridSize + nx;
            if (member[neighbor]) {
               samples[count++] = surfaces[neighbor];
            }
         }
      }
      Arrays.sort(samples, 0, count);
      return samples[count / 2];
   }

   private static int localMaximumSlope(int cell, int gridSize, int[] surfaces, boolean[] member) {
      int slope = 0;
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
            int neighbor = nz * gridSize + nx;
            if (member[neighbor]) {
               slope = Math.max(slope, Math.abs(surfaces[cell] - surfaces[neighbor]));
            }
         }
      }
      return slope;
   }

   private static boolean hasWatercourseContinuation(int upstream, int downstream, int gridSize, boolean[] member) {
      return upstream >= 0
         && downstream >= 0
         && hasMemberNeighborOtherThan(upstream, downstream, gridSize, member)
         && hasMemberNeighborOtherThan(downstream, upstream, gridSize, member);
   }

   private static boolean hasMemberNeighborOtherThan(int cell, int excluded, int gridSize, boolean[] member) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize) {
            int neighbor = nz * gridSize + nx;
            if (neighbor != excluded && member[neighbor]) {
               return true;
            }
         }
      }
      return false;
   }

   private static boolean touchesMask(int cell, int gridSize, boolean[] mask) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize && mask[nz * gridSize + nx]) {
            return true;
         }
      }
      return false;
   }

   private static int memberNeighborCount(int cell, int gridSize, boolean[] member) {
      int count = 0;
      int x = cell % gridSize;
      int z = cell / gridSize;
      for (int offset = 0; offset < NEIGHBOR_OFFSETS.length; offset += 2) {
         int nx = x + NEIGHBOR_OFFSETS[offset];
         int nz = z + NEIGHBOR_OFFSETS[offset + 1];
         if (nx >= 0 && nz >= 0 && nx < gridSize && nz < gridSize && member[nz * gridSize + nx]) {
            count++;
         }
      }
      return count;
   }

   private static boolean isGridEdge(int cell, int gridSize) {
      int x = cell % gridSize;
      int z = cell / gridSize;
      return x == 0 || z == 0 || x == gridSize - 1 || z == gridSize - 1;
   }

   private static int divideRoundUp(int value, int divisor) {
      return value <= 0 ? 0 : (value + divisor - 1) / divisor;
   }

   record Parameters(
      int maxProfileCut,
      int maxHydroFlattenCut,
      int hydroFlattenLookAheadBlocks,
      int waterfallMinDrop,
      int waterfallLookAheadBlocks,
      int baseBlendBlocks,
      int maxAdaptiveBlendBlocks,
      int waterfallProtectionBlocks,
      boolean inferWaterfalls
   ) {
      Parameters(
         int maxProfileCut,
         int maxHydroFlattenCut,
         int hydroFlattenLookAheadBlocks,
         int waterfallMinDrop,
         int waterfallLookAheadBlocks,
         int baseBlendBlocks,
         int maxAdaptiveBlendBlocks,
         int waterfallProtectionBlocks
      ) {
         this(
            maxProfileCut,
            maxHydroFlattenCut,
            hydroFlattenLookAheadBlocks,
            waterfallMinDrop,
            waterfallLookAheadBlocks,
            baseBlendBlocks,
            maxAdaptiveBlendBlocks,
            waterfallProtectionBlocks,
            true
         );
      }

      Parameters {
         if (maxProfileCut < 0
            || maxHydroFlattenCut < maxProfileCut
            || hydroFlattenLookAheadBlocks < 1
            || waterfallMinDrop < 1
            || waterfallLookAheadBlocks < 1
            || baseBlendBlocks < 0
            || maxAdaptiveBlendBlocks < baseBlendBlocks
            || waterfallProtectionBlocks < 0) {
            throw new IllegalArgumentException("Invalid inland-water flow parameters");
         }
      }
   }

   static final class Workspace {
      private int capacity;
      private boolean[] member;
      private boolean[] settled;
      private boolean[] edgeWaterfall;
      private boolean[] demCliffDrop;
      private int[] demCliffUpstream;
      private boolean[] majorDrop;
      private boolean[] reachVisited;
      private boolean[] outletSeed;
      private long[] cost;
      private int[] parent;
      private int[] target;
      private int[] monotone;
      private int[] planned;
      private int[] hydroLevel;
      private int[] order;
      private int[] reachQueue;
      private int orderCount;
      private int[] heapCells;
      private long[] heapCosts;
      private int heapSize;
      private long lastPoppedCost;
      private final int[] localSamples = new int[5];

      private void ensureCapacity(int area, int componentCount) {
         if (area > this.capacity) {
            this.capacity = area;
            this.member = new boolean[area];
            this.settled = new boolean[area];
            this.edgeWaterfall = new boolean[area];
            this.demCliffDrop = new boolean[area];
            this.demCliffUpstream = new int[area];
            this.majorDrop = new boolean[area];
            this.reachVisited = new boolean[area];
            this.outletSeed = new boolean[area];
            this.cost = new long[area];
            this.parent = new int[area];
            this.target = new int[area];
            this.monotone = new int[area];
            this.planned = new int[area];
            this.hydroLevel = new int[area];
         }
         if (this.order == null || componentCount > this.order.length) {
            this.order = new int[componentCount];
            this.reachQueue = new int[componentCount];
         } else if (this.reachQueue == null || componentCount > this.reachQueue.length) {
            this.reachQueue = new int[componentCount];
         }
         int initialHeapCapacity = Math.max(16, componentCount * 2);
         if (this.heapCells == null || initialHeapCapacity > this.heapCells.length) {
            this.heapCells = new int[initialHeapCapacity];
            this.heapCosts = new long[initialHeapCapacity];
         }
      }

      private void reset(int[] componentCells, int componentCount) {
         this.orderCount = 0;
         this.heapSize = 0;
         for (int i = 0; i < componentCount; i++) {
            int cell = componentCells[i];
            this.member[cell] = false;
         }
      }

      private void clearMembership(int[] componentCells, int componentCount) {
         for (int i = 0; i < componentCount; i++) {
            this.member[componentCells[i]] = false;
         }
      }

      private void push(int cell, long nodeCost) {
         if (this.heapSize >= this.heapCells.length) {
            int newLength = this.heapCells.length + Math.max(16, this.heapCells.length / 2);
            this.heapCells = Arrays.copyOf(this.heapCells, newLength);
            this.heapCosts = Arrays.copyOf(this.heapCosts, newLength);
         }
         int index = this.heapSize++;
         while (index > 0) {
            int parentIndex = (index - 1) >>> 1;
            if (compare(nodeCost, cell, this.heapCosts[parentIndex], this.heapCells[parentIndex]) >= 0) {
               break;
            }
            this.heapCosts[index] = this.heapCosts[parentIndex];
            this.heapCells[index] = this.heapCells[parentIndex];
            index = parentIndex;
         }
         this.heapCosts[index] = nodeCost;
         this.heapCells[index] = cell;
      }

      private int pop() {
         int resultCell = this.heapCells[0];
         this.lastPoppedCost = this.heapCosts[0];
         int lastIndex = --this.heapSize;
         if (lastIndex > 0) {
            int replacementCell = this.heapCells[lastIndex];
            long replacementCost = this.heapCosts[lastIndex];
            int index = 0;
            int half = lastIndex >>> 1;
            while (index <= half) {
               int left = index * 2 + 1;
               if (left >= lastIndex) {
                  break;
               }
               int right = left + 1;
               int child = right < lastIndex
                  && compare(this.heapCosts[right], this.heapCells[right], this.heapCosts[left], this.heapCells[left]) < 0
                  ? right
                  : left;
               if (compare(replacementCost, replacementCell, this.heapCosts[child], this.heapCells[child]) <= 0) {
                  break;
               }
               this.heapCosts[index] = this.heapCosts[child];
               this.heapCells[index] = this.heapCells[child];
               index = child;
            }
            this.heapCosts[index] = replacementCost;
            this.heapCells[index] = replacementCell;
         }
         return resultCell;
      }

      private static int compare(long firstCost, int firstCell, long secondCost, int secondCell) {
         int costComparison = Long.compare(firstCost, secondCost);
         return costComparison != 0 ? costComparison : Integer.compare(firstCell, secondCell);
      }
   }
}
