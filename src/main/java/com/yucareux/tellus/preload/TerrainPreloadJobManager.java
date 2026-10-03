package com.yucareux.tellus.preload;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class TerrainPreloadJobManager {
   private static final int PRELOAD_THREADS = Math.max(2, Integer.getInteger("tellus.preload.threads", 4));
   private static final int PACKAGE_PRELOAD_THREADS = Math.max(
      1, Integer.getInteger("tellus.preload.package.threads", Math.max(1, PRELOAD_THREADS - 1))
   );
   private static final TerrainPreloadJobManager INSTANCE = new TerrainPreloadJobManager();

   private final ExecutorService executor = Executors.newFixedThreadPool(
      PRELOAD_THREADS,
      new ThreadFactory() {
         private final AtomicInteger index = new AtomicInteger();

         @Override
         public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "tellus-preload-" + this.index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
         }
      }
   );
   private final ExecutorService packageExecutor = Executors.newFixedThreadPool(
      PACKAGE_PRELOAD_THREADS,
      new ThreadFactory() {
         private final AtomicInteger index = new AtomicInteger();

         @Override
         public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "tellus-preload-package-" + this.index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
         }
      }
   );
   private final AtomicReference<TerrainPreloadJob> currentJob = new AtomicReference<>();

   private TerrainPreloadJobManager() {
   }

   public static TerrainPreloadJobManager instance() {
      return INSTANCE;
   }

   static int preloadThreadCount() {
      return PRELOAD_THREADS;
   }

   static int packagePreloadThreadCount() {
      return PACKAGE_PRELOAD_THREADS;
   }

   public synchronized TerrainPreloadJob start(TerrainPreloadArea area, EarthGeneratorSettings settings) {
      TerrainPreloadJob previous = this.currentJob.getAndSet(null);
      if (previous != null) {
         TerrainPreloadProgress progress = previous.progress();
         if (progress.stage() != TerrainPreloadStage.COMPLETE
            && progress.stage() != TerrainPreloadStage.CANCELLED
            && progress.stage() != TerrainPreloadStage.FAILED) {
            previous.cancel();
         }
      }

      TerrainPreloadJob job = new TerrainPreloadJob(
         Objects.requireNonNull(area, "area"),
         Objects.requireNonNull(settings, "settings"),
         TerrainPreloadStorage.instance(),
         this.executor,
         this.packageExecutor
      );
      this.currentJob.set(job);
      job.start();
      return job;
   }

   /**
    * Starts a background preload without replacing the UI-owned job. Managed Distant Horizons
    * coverage uses this path so it can share the exact preload pipeline and executors.
    */
   public TerrainPreloadJob startAdditional(TerrainPreloadArea area, EarthGeneratorSettings settings) {
      TerrainPreloadJob job = new TerrainPreloadJob(
         Objects.requireNonNull(area, "area"),
         Objects.requireNonNull(settings, "settings"),
         TerrainPreloadStorage.instance(),
         this.executor,
         this.packageExecutor
      );
      job.start();
      return job;
   }

   public Optional<TerrainPreloadJob> currentJob() {
      return Optional.ofNullable(this.currentJob.get());
   }
}
