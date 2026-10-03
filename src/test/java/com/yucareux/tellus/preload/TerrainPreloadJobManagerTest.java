package com.yucareux.tellus.preload;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TerrainPreloadJobManagerTest {
   @Test
   void singletonInitializesAfterPositiveExecutorSizesAreResolved() {
      TerrainPreloadJobManager manager = TerrainPreloadJobManager.instance();

      assertNotNull(manager);
      assertSame(manager, TerrainPreloadJobManager.instance());
      assertTrue(TerrainPreloadJobManager.preloadThreadCount() >= 2);
      assertTrue(TerrainPreloadJobManager.packagePreloadThreadCount() >= 1);
   }

   @Test
   void maximumManagedOneToOneAreaKeepsAnAdaptiveProcessedPackage() {
      TerrainPreloadArea area = TerrainPreloadArea.centered(27.9881, 86.925, 8480, 1.0);
      EarthGeneratorSettings settings = TerrainPreloadSettingsOverrides.from(EarthGeneratorSettings.DEFAULT)
         .withWorldScale(1.0)
         .apply(EarthGeneratorSettings.DEFAULT);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
         TerrainPreloadJob job = new TerrainPreloadJob(
            area, settings, TerrainPreloadStorage.instance(), executor, executor
         );

         TerrainPreloadJob.PackagePlan plan = job.packagePlan();

         assertNotNull(plan);
         assertTrue(plan.gridStep() >= 31 && plan.gridStep() <= 64);
         assertTrue((long)plan.gridWidth() * plan.gridDepth() <= TerrainPreloadPackage.MAX_LOADED_SAMPLE_COUNT);
         assertTrue(plan.previewResolutionMeters() <= 64.0);
      } finally {
         executor.shutdownNow();
      }
   }
}
