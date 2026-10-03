package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.mask.TellusLandMaskSource;
import com.yucareux.tellus.world.data.osm.OsmQueryMode;
import com.yucareux.tellus.world.data.osm.OsmWaterFeature;
import com.yucareux.tellus.world.data.osm.OsmWaterKind;
import com.yucareux.tellus.world.data.osm.TellusOsmWaterSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhLodWaterResolverTest {
   @Test
   void preservesLandlockedOceanSurfaceBelowGlobalSeaLevel() {
      assertEquals(36, DhLodWaterResolver.resolvedOceanWaterSurface(36, Integer.MIN_VALUE));
      assertEquals(40, DhLodWaterResolver.resolvedOceanWaterSurface(36, 40));
   }

   @Test
   void oceanSafetyRampInheritsFullTerrainDefault() {
      assertEquals(512, DhLodWaterResolver.oceanFloorTransitionBlocks());
   }

   @Test
   void keepsRawOceanBathymetryInDhByDefault() {
      assertEquals(-4937, DhLodWaterResolver.displayOceanLodTerrainSurface(-4937, 63));
      assertEquals(-127, DhLodWaterResolver.displayOceanLodTerrainSurface(-127, 63));
   }

   @Test
   void retriesOnlyPendingNonBlockingCoverageSynchronously() {
      assertTrue(
         DhLodWaterResolver.shouldRetryPendingCoverage(
            TellusOsmWaterSource.CoverageStatus.PENDING, OsmQueryMode.NON_BLOCKING
         )
      );
      assertFalse(
         DhLodWaterResolver.shouldRetryPendingCoverage(
            TellusOsmWaterSource.CoverageStatus.PENDING, OsmQueryMode.BLOCKING
         )
      );
      assertFalse(
         DhLodWaterResolver.shouldRetryPendingCoverage(
            TellusOsmWaterSource.CoverageStatus.FAILED, OsmQueryMode.NON_BLOCKING
         )
      );
   }

   @Test
   void usesTwentyBlockSimpleInlandWaterDepth() {
      assertEquals(44, DhLodWaterResolver.simpleInlandWaterTerrainSurface(64));
   }

   @Test
   void keepsSimpleInlandDepthUniformAcrossLodBoundaries() {
      assertEquals(20, DhLodWaterResolver.simpleInlandWaterDepthForDistance(1));
      assertEquals(20, DhLodWaterResolver.simpleInlandWaterDepthForDistance(2));
      assertEquals(20, DhLodWaterResolver.simpleInlandWaterDepthForDistance(3));
      assertEquals(20, DhLodWaterResolver.simpleInlandWaterDepthForDistance(20));
      assertEquals(20, DhLodWaterResolver.simpleInlandWaterDepthForDistance(100));
   }

   @Test
   void reducesVectorWaterTileBudgetForDistantLodCells() {
      assertEquals(64, DhLodWaterResolver.waterQueryTileBudget(64));
      assertEquals(32, DhLodWaterResolver.waterQueryTileBudget(128));
      assertEquals(16, DhLodWaterResolver.waterQueryTileBudget(256));
      assertEquals(8, DhLodWaterResolver.waterQueryTileBudget(512));
      assertEquals(4, DhLodWaterResolver.waterQueryTileBudget(1024));
      assertEquals(4, DhLodWaterResolver.waterQueryTileBudget(4096));
   }

   @Test
   void doesNotTreatLodRequestEdgesAsInlandShoreline() {
      int[] terrainSurface = new int[9];
      int[] waterSurface = new int[9];
      boolean[] inlandWater = new boolean[9];
      java.util.Arrays.fill(terrainSurface, 60);
      java.util.Arrays.fill(waterSurface, 80);
      java.util.Arrays.fill(inlandWater, true);

      DhLodWaterResolver.applyInlandShorelineFloorRamp(
         terrainSurface, waterSurface, inlandWater, null, 3, 1
      );

      for (int terrain : terrainSurface) {
         assertEquals(60, terrain);
      }
   }

   @Test
   void keepsProtectedDetailedWaterInShorelineTopology() {
      int[] terrainSurface = new int[9];
      int[] waterSurface = new int[9];
      boolean[] inlandWater = new boolean[9];
      boolean[] protectedWater = new boolean[9];
      java.util.Arrays.fill(terrainSurface, 60);
      java.util.Arrays.fill(waterSurface, 80);
      java.util.Arrays.fill(inlandWater, true);
      protectedWater[4] = true;

      DhLodWaterResolver.applyInlandShorelineFloorRamp(
         terrainSurface, waterSurface, inlandWater, protectedWater, 3, 1
      );

      for (int terrain : terrainSurface) {
         assertEquals(60, terrain);
      }
   }

   @Test
   void stillRampsWaterBesideRealInBoundsShoreline() {
      int[] terrainSurface = new int[9];
      int[] waterSurface = new int[9];
      boolean[] inlandWater = new boolean[9];
      boolean[] protectedWater = new boolean[9];
      java.util.Arrays.fill(terrainSurface, 60);
      java.util.Arrays.fill(waterSurface, 80);
      java.util.Arrays.fill(inlandWater, true);
      inlandWater[4] = false;
      protectedWater[1] = true;

      DhLodWaterResolver.applyInlandShorelineFloorRamp(
         terrainSurface, waterSurface, inlandWater, protectedWater, 3, 1
      );

      assertEquals(60, terrainSurface[1]);
      assertEquals(79, terrainSurface[3]);
      assertEquals(60, terrainSurface[4]);
      assertEquals(79, terrainSurface[5]);
      assertEquals(79, terrainSurface[7]);
   }

   @Test
   void usesStableFeatureHintToKeepLakeSurfaceFlat() {
      assertEquals(80, DhLodWaterResolver.stableInlandComponentSurface(94, 80));
      assertEquals(94, DhLodWaterResolver.stableInlandComponentSurface(80, 94));
      assertEquals(94, DhLodWaterResolver.stableInlandComponentSurface(94, Integer.MIN_VALUE));
   }

   @Test
   void keepsIdentifiedLakeSurfaceStableAcrossRequestLocalFallbacksAndBorders() {
      int firstTile = DhLodWaterResolver.resolvedInlandComponentSurface(94, 80, Integer.MIN_VALUE, 90, false, 64);
      int secondTile = DhLodWaterResolver.resolvedInlandComponentSurface(75, 80, Integer.MIN_VALUE, 75, false, 64);

      assertEquals(80, firstTile);
      assertEquals(firstTile, secondTile);
   }

   @Test
   void normalizesSteepnessForFineAndCoarseLodCells() {
      assertFalse(DhLodWaterResolver.isSlopeTooSteep(1, 1, 5));
      assertTrue(DhLodWaterResolver.isSlopeTooSteep(2, 1, 5));
      assertFalse(DhLodWaterResolver.isSlopeTooSteep(2, 2, 5));
      assertTrue(DhLodWaterResolver.isSlopeTooSteep(3, 2, 5));
      assertFalse(DhLodWaterResolver.isSlopeTooSteep(5, 4, 5));
      assertTrue(DhLodWaterResolver.isSlopeTooSteep(6, 4, 5));
   }

   @Test
   void aggregatesTouchingLakeFeatureHintsLikeFullChunks() {
      int[] hints = new int[]{70, 90, 90, 90, 90};

      assertEquals(90, DhLodWaterResolver.aggregateFeatureSurfaceHints(hints, hints.length));
      assertEquals(Integer.MIN_VALUE, DhLodWaterResolver.aggregateFeatureSurfaceHints(hints, 0));
   }

   @Test
   void recognizesPolygonAndLineRiverGeometryAsFlowingWater() {
      OsmWaterFeature polygonRiver = new OsmWaterFeature(
         1L,
         false,
         false,
         OsmWaterKind.RIVER,
         new double[][]{{0.0, 0.001, 0.001, 0.0, 0.0}},
         new double[][]{{0.0, 0.0, 0.001, 0.001, 0.0}}
      );
      OsmWaterFeature lineRiver = new OsmWaterFeature(
         2L,
         true,
         false,
         OsmWaterKind.RIVER,
         new double[][]{{0.0, 0.001}},
         new double[][]{{0.0, 0.001}}
      );

      assertTrue(polygonRiver.flowingWater());
      assertTrue(DhLodWaterResolver.isFlowingWaterFeature(polygonRiver));
      assertTrue(DhLodWaterResolver.isFlowingWaterFeature(lineRiver));
   }

   @Test
   void onlyUsesStableSurfaceIdentityForNonFlowingInlandWater() {
      OsmWaterFeature lake = new OsmWaterFeature(
         7L,
         false,
         false,
         OsmWaterKind.LAKE,
         new double[][]{{0.0, 0.001, 0.001, 0.0, 0.0}},
         new double[][]{{0.0, 0.0, 0.001, 0.001, 0.0}}
      );
      OsmWaterFeature river = new OsmWaterFeature(
         8L,
         false,
         false,
         OsmWaterKind.RIVER,
         new double[][]{{0.0, 0.001, 0.001, 0.0, 0.0}},
         new double[][]{{0.0, 0.0, 0.001, 0.001, 0.0}}
      );

      assertEquals(7L, DhLodWaterResolver.stableOsmWaterBodyKey(lake));
      assertEquals(0L, DhLodWaterResolver.stableOsmWaterBodyKey(river));
   }

   @Test
   void keepsUnhintedOvertureWaterInlandWhenStrictOsmModeUsesMarineLandMask() {
      boolean ocean = DhLodWaterResolver.classifyWaterCellAsOcean(
         false, true, TellusLandMaskSource.LandMaskSample.known(false), 62, 80, 63
      );

      assertFalse(ocean);
   }

   @Test
   void classifiesUnhintedWaterAsOceanWhenNotInStrictOsmModeAndLandMaskIsMarine() {
      boolean ocean = DhLodWaterResolver.classifyWaterCellAsOcean(
         false, false, TellusLandMaskSource.LandMaskSample.known(false), 62, 80, 63
      );

      assertTrue(ocean);
   }

   @Test
   void keepsUnhintedOvertureWaterInlandWhenLandMaskIsLand() {
      boolean ocean = DhLodWaterResolver.classifyWaterCellAsOcean(
         false, false, TellusLandMaskSource.LandMaskSample.known(true), 62, 80, 63
      );

      assertFalse(ocean);
   }

   @Test
   void acceptsOvertureOceanHint() {
      boolean ocean = DhLodWaterResolver.classifyWaterCellAsOcean(
         true, true, TellusLandMaskSource.LandMaskSample.known(true), 80, 80, 63
      );

      assertTrue(ocean);
   }

   @Test
   void osmModeRequiresAnOsmWaterCandidateBeforeOceanClassification() {
      assertFalse(DhLodWaterResolver.shouldClassifyWaterCellAsOcean(true, false, false));
      assertTrue(DhLodWaterResolver.shouldClassifyWaterCellAsOcean(true, true, false));
      assertTrue(DhLodWaterResolver.shouldClassifyWaterCellAsOcean(true, false, true));
      assertTrue(DhLodWaterResolver.shouldClassifyWaterCellAsOcean(false, false, false));
   }

   @Test
   void samplesLandMaskForOceanCoverCandidatesAboveSeaLevel() {
      assertTrue(DhLodWaterResolver.shouldSampleLandMask(false, false, 80, false));
      assertTrue(DhLodWaterResolver.shouldSampleLandMask(false, false, 0, false));
   }

   @Test
   void samplesLandMaskForSampledOceanCandidatesAboveSeaLevel() {
      assertTrue(DhLodWaterResolver.shouldSampleLandMask(true, false, 10, false));
   }

   @Test
   void skipsLandMaskForOrdinaryDryLandAboveSeaLevel() {
      assertFalse(DhLodWaterResolver.shouldSampleLandMask(false, false, 10, false));
   }

   @Test
   void classifiesKnownOceanWaterCoverAboveSeaLevelAsOcean() {
      boolean ocean = DhLodWaterResolver.classifyWaterCellAsOcean(
         false, false, TellusLandMaskSource.LandMaskSample.known(false), 120, 80, 63
      );

      assertTrue(ocean);
   }

   @Test
   void keepsBorderlessHighInlandWaterAtComponentElevation() {
      assertEquals(151, DhLodWaterResolver.fallbackInlandComponentSurface(3, 452L, 153, 64));
   }

   @Test
   void capsInlandComponentSurfaceToSpillBank() {
      assertEquals(84, DhLodWaterResolver.cappedInlandComponentSurface(90, Integer.MIN_VALUE, 84, false, 64));
      assertEquals(84, DhLodWaterResolver.cappedInlandComponentSurface(82, 88, 84, false, 64));
      assertEquals(64, DhLodWaterResolver.cappedInlandComponentSurface(90, Integer.MIN_VALUE, Integer.MAX_VALUE, true, 64));
   }

   @Test
   void driesOverBudgetLakeLodWater() {
      int[] waterSurface = new int[]{80};
      int[] baseTerrainSurface = new int[]{93};
      boolean[] hasWater = new boolean[]{true};
      boolean[] ocean = new boolean[]{false};
      boolean[] lineWater = new boolean[]{false};

      assertTrue(DhLodWaterResolver.removeOverBudgetInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater));
      assertFalse(hasWater[0]);
      assertEquals(93, waterSurface[0]);
   }

   @Test
   void keepsLakeLodWaterWithinBudget() {
      int[] waterSurface = new int[]{80};
      int[] baseTerrainSurface = new int[]{92};
      boolean[] hasWater = new boolean[]{true};
      boolean[] ocean = new boolean[]{false};
      boolean[] lineWater = new boolean[]{false};

      assertFalse(DhLodWaterResolver.removeOverBudgetInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater));
      assertTrue(hasWater[0]);
      assertEquals(80, waterSurface[0]);
   }

   @Test
   void removesSimpleLakeWaterOnSteepLodTerrain() {
      int[] waterSurface = new int[]{
         80, 80, 80,
         80, 80, 80,
         80, 80, 80
      };
      int[] baseTerrainSurface = new int[]{
         80, 80, 80,
         80, 80, 100,
         80, 80, 80
      };
      boolean[] hasWater = new boolean[]{
         false, false, false,
         false, true, false,
         false, false, false
      };
      boolean[] ocean = new boolean[9];
      boolean[] lineWater = new boolean[9];

      assertTrue(DhLodWaterResolver.removeSteepInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater, 3, 4, 5));
      assertFalse(hasWater[4]);
      assertEquals(80, waterSurface[4]);
   }

   @Test
   void keepsSimpleLakeWaterOnGentleLodTerrain() {
      int[] waterSurface = new int[]{
         80, 80, 80,
         80, 80, 80,
         80, 80, 80
      };
      int[] baseTerrainSurface = new int[]{
         80, 81, 80,
         81, 82, 83,
         80, 81, 80
      };
      boolean[] hasWater = new boolean[]{
         false, false, false,
         false, true, false,
         false, false, false
      };
      boolean[] ocean = new boolean[9];
      boolean[] lineWater = new boolean[9];

      assertFalse(DhLodWaterResolver.removeSteepInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater, 3, 4, 5));
      assertTrue(hasWater[4]);
      assertEquals(80, waterSurface[4]);
   }

   @Test
   void keepsTerrainFollowingLineWaterOnSteepLodTerrain() {
      int[] waterSurface = new int[]{
         80, 80, 80,
         80, 80, 80,
         80, 80, 80
      };
      int[] baseTerrainSurface = new int[]{
         80, 80, 80,
         80, 80, 100,
         80, 80, 80
      };
      boolean[] hasWater = new boolean[]{
         false, false, false,
         false, true, false,
         false, false, false
      };
      boolean[] ocean = new boolean[9];
      boolean[] lineWater = new boolean[]{
         false, false, false,
         false, true, false,
         false, false, false
      };

      assertFalse(DhLodWaterResolver.removeSteepInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater, 3, 4, 5));
      assertTrue(hasWater[4]);
   }

   @Test
   void keepsLineWaterBelowTerrainForRiverCarving() {
      int[] waterSurface = new int[]{80, 80};
      int[] baseTerrainSurface = new int[]{86, 87};
      boolean[] hasWater = new boolean[]{true, true};
      boolean[] ocean = new boolean[]{false, false};
      boolean[] lineWater = new boolean[]{true, true};

      assertFalse(DhLodWaterResolver.removeOverBudgetInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater));
      assertTrue(hasWater[0]);
      assertTrue(hasWater[1]);
      assertEquals(80, waterSurface[1]);
   }

   @Test
   void driesLineWaterAboveTerrain() {
      int[] waterSurface = new int[]{89};
      int[] baseTerrainSurface = new int[]{87};
      boolean[] hasWater = new boolean[]{true};
      boolean[] ocean = new boolean[]{false};
      boolean[] lineWater = new boolean[]{true};

      assertTrue(DhLodWaterResolver.removeOverBudgetInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater));
      assertFalse(hasWater[0]);
      assertEquals(87, waterSurface[0]);
   }

   @Test
   void keepsDirectLineWaterOnTerrain() {
      int[] waterSurface = new int[]{87};
      int[] baseTerrainSurface = new int[]{87};
      boolean[] hasWater = new boolean[]{true};
      boolean[] ocean = new boolean[]{false};
      boolean[] lineWater = new boolean[]{true};

      assertFalse(DhLodWaterResolver.removeOverBudgetInlandWater(waterSurface, baseTerrainSurface, hasWater, ocean, lineWater));
      assertTrue(hasWater[0]);
      assertEquals(87, waterSurface[0]);
   }

   @Test
   void keepsBorderlessOceanLikeWaterAtSeaLevel() {
      assertEquals(64, DhLodWaterResolver.fallbackInlandComponentSurface(3, 90L, 31, 64));
   }

   @Test
   void fallsBackToSeaLevelForEmptyComponents() {
      assertEquals(64, DhLodWaterResolver.fallbackInlandComponentSurface(0, 0L, Integer.MIN_VALUE, 64));
   }

   @Test
   void detailedFastLodsReuseTheCoherentLakeBedProfile() {
      int size = 15;
      int area = size * size;
      int[] terrain = new int[area];
      int[] water = new int[area];
      boolean[] hasWater = new boolean[area];
      boolean[] ocean = new boolean[area];
      boolean[] directLine = new boolean[area];
      boolean[] waterfall = new boolean[area];
      boolean[] river = new boolean[area];
      boolean[] detailed = new boolean[area];
      int[] worldXs = new int[size];
      int[] worldZs = new int[size];
      for (int axis = 0; axis < size; axis++) {
         worldXs[axis] = 1_000 + axis * 4;
         worldZs[axis] = -2_000 + axis * 4;
      }
      for (int z = 1; z < size - 1; z++) {
         for (int x = 1; x < size - 1; x++) {
            int index = z * size + x;
            hasWater[index] = true;
            water[index] = 100;
         }
      }

      DhLodWaterResolver.applyInlandDepthProfile(
         terrain,
         water,
         hasWater,
         ocean,
         directLine,
         waterfall,
         river,
         detailed,
         worldXs,
         worldZs,
         size,
         4,
         true
      );

      assertEquals(98, terrain[size + 1]);
      int center = 7 * size + 7;
      assertEquals(100 - LakeBedProfile.sampledDepth(6, 4, worldXs[7], worldZs[7]), terrain[center]);
      assertTrue(detailed[center]);
   }

   @Test
   void coarseDetailedLakeShoreCannotCollapseAgainstTheWaterSurface() {
      int size = 3;
      int center = size + 1;
      int[] terrain = new int[size * size];
      int[] water = new int[size * size];
      boolean[] hasWater = new boolean[size * size];
      boolean[] empty = new boolean[size * size];
      boolean[] detailed = new boolean[size * size];
      hasWater[center] = true;
      water[center] = 100;

      DhLodWaterResolver.applyInlandDepthProfile(
         terrain,
         water,
         hasWater,
         empty,
         empty,
         empty,
         empty,
         detailed,
         new int[]{0, 16, 32},
         new int[]{0, 16, 32},
         size,
         16,
         true
      );

      assertEquals(92, terrain[center]);
      assertTrue(detailed[center]);
   }

   @Test
   void approximateFastLodsKeepTheirLegacyConstantDepth() {
      int[] terrain = new int[]{0};
      int[] water = new int[]{80};
      boolean[] hasWater = new boolean[]{true};
      boolean[] empty = new boolean[]{false};

      DhLodWaterResolver.applyInlandDepthProfile(
         terrain,
         water,
         hasWater,
         empty,
         empty,
         empty,
         empty,
         null,
         new int[]{0},
         new int[]{0},
         1,
         1,
         false
      );

      assertEquals(60, terrain[0]);
   }

   @Test
   void detailedLakeBedsDoNotChangeFastLodRiverDepths() {
      int[] terrain = new int[]{0};
      int[] water = new int[]{80};
      boolean[] hasWater = new boolean[]{true};
      boolean[] empty = new boolean[]{false};
      boolean[] river = new boolean[]{true};
      boolean[] detailed = new boolean[]{false};

      DhLodWaterResolver.applyInlandDepthProfile(
         terrain,
         water,
         hasWater,
         empty,
         empty,
         empty,
         river,
         detailed,
         new int[]{0},
         new int[]{0},
         1,
         1,
         true
      );

      assertEquals(60, terrain[0]);
      assertFalse(detailed[0]);
   }
}
