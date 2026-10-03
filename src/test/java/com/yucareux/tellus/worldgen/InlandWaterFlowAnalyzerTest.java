package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlandWaterFlowAnalyzerTest {
   private static final InlandWaterFlowAnalyzer.Parameters PARAMETERS = new InlandWaterFlowAnalyzer.Parameters(
      3,
      8,
      8,
      6,
      8,
      4,
      32,
      2
   );

   @Test
   void hydroFlattensOrdinaryReachWithoutFluidTransitions() {
      Analysis analysis = analyzeLine(110, 109, 108, 107, 106, 105, 104);

      int first = analysis.surfaceAt(0);
      int last = analysis.surfaceAt(6);
      assertEquals(first, last);
      for (int i = 0; i < 6; i++) {
         assertTrue(analysis.activeAt(i));
         assertFalse(analysis.waterfallAt(i));
         assertEquals(analysis.surfaceAt(i), analysis.surfaceAt(i + 1));
      }
      assertTrue(analysis.activeAt(6));
   }

   @Test
   void carvesSmallUphillObstructionWithinBudget() {
      Analysis analysis = analyzeLine(110, 109, 111, 108, 107, 106);

      assertTrue(analysis.activeAt(2));
      assertTrue(analysis.correctionAt(2));
      assertTrue(111 - analysis.surfaceAt(2) <= PARAMETERS.maxHydroFlattenCut());
      for (int i = 0; i < 5; i++) {
         assertTrue(analysis.surfaceAt(i) >= analysis.surfaceAt(i + 1));
      }
      assertTrue(analysis.blendRadiusAt(2) > PARAMETERS.baseBlendBlocks());
   }

   @Test
   void severelyFlattensRidgeConflictInsidePolygonReach() {
      Analysis analysis = analyzeLine(110, 109, 124, 108, 107, 106);

      assertTrue(analysis.activeAt(2));
      assertFalse(analysis.waterfallAt(2));
      assertEquals(analysis.surfaceAt(1), analysis.surfaceAt(2));
      assertTrue(analysis.correctionAt(2));
   }

   @Test
   void hydroFlattensSupportedDemHumpWithoutCreatingAFalseWaterfall() {
      Analysis analysis = analyzeLine(100, 100, 106, 106, 100, 99, 99);

      assertTrue(analysis.activeAt(2));
      assertTrue(analysis.activeAt(3));
      assertTrue(analysis.correctionAt(2));
      assertTrue(analysis.correctionAt(3));
      assertEquals(analysis.surfaceAt(1), analysis.surfaceAt(2));
      assertEquals(analysis.surfaceAt(2), analysis.surfaceAt(3));
      assertFalse(analysis.waterfallAt(4));
   }

   @Test
   void overBudgetBracketedDemHumpIsStillHydroFlattened() {
      Analysis analysis = analyzeLine(100, 100, 120, 120, 100, 99, 99);

      assertTrue(analysis.activeAt(2));
      assertTrue(analysis.activeAt(3));
      assertFalse(analysis.waterfallAt(4));
      assertEquals(analysis.surfaceAt(1), analysis.surfaceAt(2));
      assertEquals(analysis.surfaceAt(2), analysis.surfaceAt(3));
   }

   @Test
   void preservesMajorDropForMinecraftWaterfallPhysics() {
      Analysis analysis = analyzeLine(121, 120, 119, 105, 104, 103, 102);

      assertTrue(analysis.activeAt(2));
      assertTrue(analysis.waterfallAt(3));
      assertFalse(analysis.activeAt(3));
      assertTrue(analysis.surfaceAt(3) >= analysis.surfaceAt(2));
      assertTrue(analysis.activeAt(4));
      assertTrue(analysis.protectedAt(3));
      assertEquals(0, analysis.blendRadiusAt(2));
   }

   @Test
   void markerDrivenModeDoesNotInferWaterfallsFromTheDem() {
      InlandWaterFlowAnalyzer.Parameters markerDriven = new InlandWaterFlowAnalyzer.Parameters(
         3, 8, 8, 6, 8, 4, 32, 2, false
      );
      Analysis analysis = analyzeLine(markerDriven, 121, 120, 119, 105, 104, 103, 102);

      int connectedSurface = analysis.surfaceAt(0);
      for (int i = 0; i < 7; i++) {
         assertTrue(analysis.activeAt(i));
         assertFalse(analysis.waterfallAt(i));
         assertEquals(connectedSurface, analysis.surfaceAt(i));
      }
   }

   @Test
   void leavesNaturallySupportedFlatWaterWithoutAdaptiveBankCorrection() {
      Analysis analysis = analyzeLine(90, 90, 90, 90, 90, 90);

      for (int i = 0; i < 6; i++) {
         assertTrue(analysis.activeAt(i));
         assertFalse(analysis.correctionAt(i));
         assertEquals(0, analysis.blendRadiusAt(i));
      }
   }

   @Test
   void severelyFlattensLongPolygonReachWithoutDrySeparators() {
      Analysis analysis = analyzeLine(
         130, 129, 128, 127, 126, 125, 124, 123, 122, 121,
         120, 119, 118, 117, 116, 115, 114, 113, 112, 111,
         110, 109, 108, 107, 106, 105, 104, 103, 102, 101, 100
      );

      int surface = analysis.surfaceAt(0);
      for (int i = 0; i < 31; i++) {
         assertFalse(analysis.waterfallAt(i));
         assertTrue(analysis.activeAt(i));
         assertFalse(analysis.protectedAt(i));
         assertEquals(surface, analysis.surfaceAt(i));
      }
   }

   @Test
   void marksWaterfallFrontAcrossWideMappedChannel() {
      int[] profile = new int[]{121, 120, 119, 105, 104, 103, 102};
      int gridSize = profile.length + 2;
      int area = gridSize * gridSize;
      int[] cells = new int[profile.length * 2];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int cellCount = 0;
      for (int z = 3; z <= 4; z++) {
         for (int x = 1; x <= profile.length; x++) {
            int cell = z * gridSize + x;
            cells[cellCount++] = cell;
            rawTerrain[cell] = profile[x - 1];
            ceiling[cell] = profile[x - 1];
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         cells.length,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         2,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      assertTrue(waterfall[3 * gridSize + 4]);
      assertTrue(waterfall[4 * gridSize + 4]);
      assertFalse(active[3 * gridSize + 4]);
      assertFalse(active[4 * gridSize + 4]);
   }

   @Test
   void niagaraLikeCliffCreatesOneDirectionalFrontNotConcentricTerraces() {
      int gridSize = 15;
      int area = gridSize * gridSize;
      int[] cells = new int[11 * 5];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 5; z <= 9; z++) {
         for (int x = 2; x <= 12; x++) {
            int cell = z * gridSize + x;
            int height = x <= 6 ? 128 - (6 - x) / 2 : 102 - (x - 7) / 3;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         5,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int waterfallCells = 0;
      for (int cell : cells) {
         int x = cell % gridSize;
         if (waterfall[cell]) {
            waterfallCells++;
            assertTrue(x >= 6 && x <= 8);
         }
         if (x <= 5 || x >= 9) {
            assertTrue(active[cell]);
         }
      }
      assertTrue(waterfallCells >= 3);

      int upstreamSurface = planned[7 * gridSize + 4];
      int downstreamSurface = planned[7 * gridSize + 10];
      assertTrue(upstreamSurface > downstreamSurface);
      for (int z = 5; z <= 9; z++) {
         assertEquals(upstreamSurface, planned[z * gridSize + 4]);
         assertEquals(downstreamSurface, planned[z * gridSize + 10]);
      }
   }

   @Test
   void preservesCliffWhenOnlyUpstreamPlateauTouchesAnalysisBoundary() {
      int gridSize = 15;
      int area = gridSize * gridSize;
      int[] cells = new int[13 * 5];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 0; z <= 12; z++) {
         for (int x = 5; x <= 9; x++) {
            int cell = z * gridSize + x;
            int height = z <= 5 ? 128 : 100;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         5,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int upstreamSurface = planned[3 * gridSize + 7];
      int downstreamSurface = planned[9 * gridSize + 7];
      assertTrue(upstreamSurface > downstreamSurface);
      for (int x = 5; x <= 9; x++) {
         int dropCell = 6 * gridSize + x;
         assertTrue(waterfall[dropCell]);
         assertFalse(active[dropCell]);
         assertEquals(upstreamSurface, waterfallTop[dropCell]);
         assertTrue(active[3 * gridSize + x]);
         assertTrue(active[9 * gridSize + x]);
      }
   }

   @Test
   void stillFlattensTwoSidedRidgeWhenBoundaryDirectionIsAmbiguous() {
      int gridSize = 17;
      int area = gridSize * gridSize;
      int[] cells = new int[15 * 5];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 0; z <= 14; z++) {
         for (int x = 6; x <= 10; x++) {
            int cell = z * gridSize + x;
            int height = z >= 5 && z <= 7 ? 128 : z >= 8 ? 99 : 100;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         5,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int connectedSurface = planned[12 * gridSize + 8];
      for (int cell : cells) {
         assertTrue(active[cell]);
         assertFalse(waterfall[cell]);
         assertEquals(connectedSurface, planned[cell]);
      }
   }

   @Test
   void hydroFlattensWideGradualReachWithoutParallelFlowFronts() {
      int gridSize = 11;
      int area = gridSize * gridSize;
      int[] cells = new int[7 * 3];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int cellCount = 0;
      for (int z = 4; z <= 6; z++) {
         for (int x = 2; x <= 8; x++) {
            int cell = z * gridSize + x;
            int height = 114 - x + Math.abs(z - 5);
            cells[cellCount++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         cells.length,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         3,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int surface = planned[5 * gridSize + 8];
      for (int cell : cells) {
         assertTrue(active[cell]);
         assertFalse(waterfall[cell]);
         assertEquals(surface, planned[cell]);
      }
   }

   @Test
   void wideOverBudgetReachHasOneConnectedSurfaceWithoutRetainingWalls() {
      int gridSize = 35;
      int area = gridSize * gridSize;
      int[] cells = new int[31 * 3];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int cellCount = 0;
      for (int z = 16; z <= 18; z++) {
         for (int x = 2; x <= 32; x++) {
            int cell = z * gridSize + x;
            int height = 132 - x + Math.abs(z - 17);
            cells[cellCount++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         cells.length,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         3,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int surface = planned[17 * gridSize + 32];
      for (int cell : cells) {
         assertFalse(waterfall[cell]);
         assertTrue(active[cell]);
         assertFalse(protection[cell]);
         assertEquals(surface, planned[cell]);
      }
   }

   @Test
   void preservesMapterhornSmoothedWaterfallRampBetweenPlateaus() {
      int gridSize = 129;
      int area = gridSize * gridSize;
      int channelMinX = 61;
      int channelMaxX = 67;
      int[] cells = new int[121 * (channelMaxX - channelMinX + 1)];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 4; z <= 124; z++) {
         int height;
         if (z <= 27) {
            height = 274;
         } else if (z >= 89) {
            height = 202;
         } else {
            height = 274 - (int)Math.round((z - 27) * 72.0 / 62.0);
         }
         for (int x = channelMinX; x <= channelMaxX; x++) {
            int cell = z * gridSize + x;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         channelMaxX - channelMinX + 1,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      for (int x = channelMinX; x <= channelMaxX; x++) {
         assertTrue(active[16 * gridSize + x]);
         assertEquals(274, planned[16 * gridSize + x]);
         assertTrue(active[106 * gridSize + x]);
         assertEquals(202, planned[106 * gridSize + x]);
      }
      int waterfallCells = 0;
      int previousWaterfallSurface = Integer.MAX_VALUE;
      for (int z = 4; z <= 124; z++) {
         for (int x = channelMinX; x <= channelMaxX; x++) {
            int cell = z * gridSize + x;
            if (waterfall[cell]) {
               waterfallCells++;
               assertFalse(active[cell]);
               assertTrue(waterfallTop[cell] >= rawTerrain[cell]);
               assertTrue(waterfallTop[cell] - rawTerrain[cell] <= 2);
               assertEquals(waterfallTop[cell], planned[cell]);
               if (x == channelMinX) {
                  assertTrue(planned[cell] <= previousWaterfallSurface);
                  previousWaterfallSurface = planned[cell];
               }
            }
         }
      }
      assertTrue(waterfallCells >= 50 * (channelMaxX - channelMinX + 1));
   }

   @Test
   void doesNotMistakeWideTwoSidedDemRidgeForSmoothedWaterfall() {
      int gridSize = 121;
      int area = gridSize * gridSize;
      int[] cells = new int[113 * 5];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 4; z <= 116; z++) {
         int distanceFromCenter = Math.abs(z - 60);
         int height = distanceFromCenter <= 12
            ? 150
            : distanceFromCenter >= 40
               ? 100
               : 150 - (int)Math.round((distanceFromCenter - 12) * 50.0 / 28.0);
         for (int x = 58; x <= 62; x++) {
            int cell = z * gridSize + x;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         5,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int connectedSurface = planned[8 * gridSize + 60];
      for (int cell : cells) {
         assertTrue(active[cell]);
         assertFalse(waterfall[cell]);
         assertEquals(connectedSurface, planned[cell]);
      }
   }

   @Test
   void hydroFlattensBroadSubWaterfallGradeBetweenPlateaus() {
      int gridSize = 129;
      int area = gridSize * gridSize;
      int[] cells = new int[121 * 5];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];
      int count = 0;
      for (int z = 4; z <= 124; z++) {
         int height;
         if (z <= 20) {
            height = 130;
         } else if (z >= 101) {
            height = 100;
         } else {
            height = 130 - (int)Math.round((z - 20) * 30.0 / 81.0);
         }
         for (int x = 62; x <= 66; x++) {
            int cell = z * gridSize + x;
            cells[count++] = cell;
            rawTerrain[cell] = height;
            ceiling[cell] = height;
         }
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         count,
         rawTerrain,
         ceiling,
         new boolean[area],
         1,
         5,
         PARAMETERS,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );

      int connectedSurface = planned[112 * gridSize + 64];
      for (int cell : cells) {
         assertTrue(active[cell]);
         assertFalse(waterfall[cell]);
         assertEquals(connectedSurface, planned[cell]);
      }
   }

   private static Analysis analyzeLine(int... terrainProfile) {
      return analyzeLine(PARAMETERS, terrainProfile);
   }

   private static Analysis analyzeLine(InlandWaterFlowAnalyzer.Parameters parameters, int... terrainProfile) {
      int gridSize = terrainProfile.length + 2;
      int area = gridSize * gridSize;
      int z = gridSize / 2;
      int[] cells = new int[terrainProfile.length];
      int[] rawTerrain = new int[area];
      int[] ceiling = new int[area];
      boolean[] ocean = new boolean[area];
      int[] planned = new int[area];
      boolean[] active = new boolean[area];
      boolean[] waterfall = new boolean[area];
      int[] waterfallTop = new int[area];
      boolean[] correction = new boolean[area];
      int[] blendRadius = new int[area];
      boolean[] protection = new boolean[area];

      for (int i = 0; i < terrainProfile.length; i++) {
         int cell = z * gridSize + i + 1;
         cells[i] = cell;
         rawTerrain[cell] = terrainProfile[i];
         ceiling[cell] = terrainProfile[i];
      }

      InlandWaterFlowAnalyzer.analyze(
         gridSize,
         cells,
         cells.length,
         rawTerrain,
         ceiling,
         ocean,
         1,
         3,
         parameters,
         planned,
         active,
         waterfall,
         waterfallTop,
         correction,
         blendRadius,
         protection,
         new InlandWaterFlowAnalyzer.Workspace()
      );
      return new Analysis(cells, planned, active, waterfall, correction, blendRadius, protection);
   }

   private record Analysis(
      int[] cells,
      int[] surfaces,
      boolean[] active,
      boolean[] waterfall,
      boolean[] correction,
      int[] blendRadius,
      boolean[] protection
   ) {
      private int surfaceAt(int index) {
         return this.surfaces[this.cells[index]];
      }

      private boolean activeAt(int index) {
         return this.active[this.cells[index]];
      }

      private boolean waterfallAt(int index) {
         return this.waterfall[this.cells[index]];
      }

      private boolean correctionAt(int index) {
         return this.correction[this.cells[index]];
      }

      private int blendRadiusAt(int index) {
         return this.blendRadius[this.cells[index]];
      }

      private boolean protectedAt(int index) {
         return this.protection[this.cells[index]];
      }

   }
}
