package com.yucareux.tellus.integration.distant_horizons.managed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import com.yucareux.tellus.preload.TerrainPreloadArea;
import org.junit.jupiter.api.Test;

class ManagedTerrainTargetTest {
   @Test
   void safetyRingIsOneEighthAlignedAndClamped() {
      assertEquals(32, ManagedTerrainTarget.initial(0, 0, 128).safetyRingChunks());
      assertEquals(64, ManagedTerrainTarget.initial(0, 0, 512).safetyRingChunks());
      assertEquals(128, ManagedTerrainTarget.initial(0, 0, 4096).safetyRingChunks());
   }

   @Test
   void recentersOnlyAfterCrossingSafetyRing() {
      ManagedTerrainTarget initial = ManagedTerrainTarget.initial(10, -20, 512);
      assertSame(initial, initial.update(73, -20, 512));
      ManagedTerrainTarget moved = initial.update(74, -20, 512);
      assertEquals(74, moved.centerChunkX());
   }

   @Test
   void cellsAreProgressiveFromPlayerCenter() {
      ManagedTerrainTarget target = ManagedTerrainTarget.initial(40, 40, 32);
      List<ManagedTerrainCell> cells = target.prioritizedCells(40, 40);
      assertEquals(ManagedTerrainCell.containingChunk(40, 40), cells.get(0));
      assertTrue(cells.stream().allMatch(target::contains));
   }

   @Test
   void batchesKeepProgressiveOrderWithoutLosingCells() {
      ManagedTerrainTarget target = ManagedTerrainTarget.initial(40, 40, 512);
      List<ManagedTerrainCell> cells = target.prioritizedCells(40, 40);
      List<ManagedTerrainTarget.CellBatch> batches = target.prioritizedBatches(40, 40, 8);

      assertTrue(batches.get(0).cells().contains(ManagedTerrainCell.containingChunk(40, 40)));
      assertTrue(batches.stream().allMatch(batch -> batch.cells().size() <= 64));
      Set<ManagedTerrainCell> batchedCells = batches.stream().flatMap(batch -> batch.cells().stream()).collect(Collectors.toSet());
      assertEquals(Set.copyOf(cells), batchedCells);
   }

   @Test
   void downloadBatchesScaleWithRenderRadius() {
      assertEquals(8, ManagedTerrainDownloadManager.downloadBatchCellsPerSide(512));
      assertEquals(16, ManagedTerrainDownloadManager.downloadBatchCellsPerSide(1024));
      assertEquals(32, ManagedTerrainDownloadManager.downloadBatchCellsPerSide(2048));
      assertEquals(64, ManagedTerrainDownloadManager.downloadBatchCellsPerSide(4096));

      ManagedTerrainTarget target = ManagedTerrainTarget.initial(0, 0, 4096);
      assertEquals(70_225, target.prioritizedCells(0, 0).size());
      assertEquals(100, target.prioritizedBatches(0, 0, 32).size());
   }

   @Test
   void maximumDhRadiusBecomesOneFullPlayerCenteredPreloadArea() {
      ManagedTerrainTarget target = ManagedTerrainTarget.initial(0, 0, 4096);

      TerrainPreloadArea area = ManagedTerrainDownloadManager.areaForCells(target.prioritizedCells(0, 0), 1.0);

      assertEquals(8480, area.chunksPerSide());
      assertEquals(-4224, area.minChunkX());
      assertEquals(4255, area.maxChunkX());
   }

   @Test
   void incrementalCoverageCollapsesAPlayerMovementStripIntoOneRectangle() {
      List<ManagedTerrainCell> prioritized = ManagedTerrainTarget.initial(0, 0, 128).prioritizedCells(0, 0);
      Set<ManagedTerrainCell> pending = new HashSet<>();
      for (int z = -5; z <= 5; z++) {
         for (int x = 4; x <= 5; x++) {
            pending.add(new ManagedTerrainCell(x, z));
         }
      }

      List<ManagedTerrainCell> rectangle = ManagedTerrainDownloadManager.nextPendingRectangle(prioritized, pending);

      assertEquals(22, rectangle.size());
      assertEquals(pending, Set.copyOf(rectangle));
   }

   @Test
   void managedRetryBackoffIsBounded() {
      assertEquals(2_000L, ManagedTerrainDownloadManager.retryDelayMillis(1));
      assertEquals(4_000L, ManagedTerrainDownloadManager.retryDelayMillis(2));
      assertEquals(30_000L, ManagedTerrainDownloadManager.retryDelayMillis(20));
   }
}
