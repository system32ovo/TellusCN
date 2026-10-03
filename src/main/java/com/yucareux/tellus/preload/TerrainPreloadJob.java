package com.yucareux.tellus.preload;

import com.google.gson.JsonElement;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.world.data.source.DownloadProgressReporter;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import com.yucareux.tellus.worldgen.TellusWorldgenSources;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class TerrainPreloadJob {
   private static final double DEFAULT_PACKAGE_GRID_RESOLUTION_METERS = 8.0;
   private static final double DEFAULT_PACKAGE_MAX_GRID_RESOLUTION_METERS = 64.0;
   private static final int DEFAULT_PACKAGE_MAX_SAMPLES = 20_000_000;
   private static final int DEFAULT_PACKAGE_ROWS_IN_FLIGHT = 8;
   private final String id;
   private final TerrainPreloadArea area;
   private final EarthGeneratorSettings settings;
   private final String sourceDetail;
   private final TerrainPreloadStorage storage;
   private final ExecutorService coordinatorExecutor;
   private final ExecutorService packageExecutor;
   private final Object pauseLock = new Object();
   private final Object lifecycleLock = new Object();
   private final AtomicBoolean started = new AtomicBoolean(false);
   private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
   private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
   private final AtomicInteger activeDownloadRequests = new AtomicInteger();
   private final AtomicLong downloadBytesRead = new AtomicLong();
   private final AtomicLong downloadBytesExpected = new AtomicLong(-1L);
   private final AtomicLong preplannedBytesRemaining = new AtomicLong();
   private final AtomicLong lastByteProgressUpdateMillis = new AtomicLong();
   private final AtomicReference<TerrainPreloadProgress> progress;
   private volatile Future<?> future;
   private volatile Thread coordinatorThread;
   private volatile Path stagingDirectory;
   private volatile boolean published;
   private volatile boolean processedPackagePublished;

   TerrainPreloadJob(
      TerrainPreloadArea area,
      EarthGeneratorSettings settings,
      TerrainPreloadStorage storage,
      ExecutorService coordinatorExecutor,
      ExecutorService packageExecutor
   ) {
      this.id = "preload-" + UUID.randomUUID();
      this.area = Objects.requireNonNull(area, "area");
      this.settings = Objects.requireNonNull(settings, "settings");
      this.sourceDetail = sourceDetail(settings);
      this.storage = Objects.requireNonNull(storage, "storage");
      this.coordinatorExecutor = Objects.requireNonNull(coordinatorExecutor, "coordinatorExecutor");
      this.packageExecutor = Objects.requireNonNull(packageExecutor, "packageExecutor");
      this.progress = new AtomicReference<>(
         new TerrainPreloadProgress(
            this.id, TerrainPreloadStage.IDLE, "Ready", area.summary(), this.sourceDetail, 0, area.totalChunks(), 0L, -1L, System.currentTimeMillis(), 0, false, true, null
         )
      );
   }

   public String id() {
      return this.id;
   }

   public TerrainPreloadArea area() {
      return this.area;
   }

   public EarthGeneratorSettings settings() {
      return this.settings;
   }

   public TerrainPreloadProgress progress() {
      return this.progress.get();
   }

   public boolean processedPackagePublished() {
      return this.processedPackagePublished;
   }

   /** Waits for this job's normal completion path and returns its terminal progress snapshot. */
   public TerrainPreloadProgress awaitCompletion() {
      this.start();
      Future<?> running = this.future;
      if (running == null) {
         return this.progress.get();
      }

      try {
         running.get();
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         this.cancel();
      } catch (ExecutionException error) {
         // runDownload records failures in progress, but preserve a useful terminal state if
         // the executor itself ever fails before that handler is reached.
         TerrainPreloadProgress current = this.progress.get();
         if (!isTerminal(current.stage())) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            this.setProgress(
               TerrainPreloadStage.FAILED,
               "Preload failed",
               cause.getMessage(),
               current.completedUnits(),
               current.totalUnits(),
               current.startedAtMillis(),
               false,
               cause.toString()
            );
         }
      }
      return this.progress.get();
   }

   public void start() {
      synchronized (this.lifecycleLock) {
         if (this.started.compareAndSet(false, true)) {
            try {
               this.future = this.coordinatorExecutor.submit(this::runDownload);
            } catch (RuntimeException error) {
               this.started.set(false);
               throw error;
            }
         }
      }
   }

   public void pause() {
      this.pauseRequested.set(true);
      this.updatePaused(true);
   }

   public void resume() {
      this.pauseRequested.set(false);
      synchronized (this.pauseLock) {
         this.pauseLock.notifyAll();
      }

      this.updatePaused(false);
   }

   public void cancel() {
      synchronized (this.lifecycleLock) {
         if (isTerminal(this.progress.get().stage())) {
            return;
         }
         this.cancelRequested.set(true);
         TerrainPreloadProgress current = this.progress.get();
         this.setProgress(
            current.stage(),
            "Cancelling preload",
            "Stopping workers and cleaning the unfinished package",
            current.completedUnits(),
            current.totalUnits(),
            current.startedAtMillis(),
            false,
            null
         );
      }
      this.resume();
      Thread coordinator = this.coordinatorThread;
      if (coordinator != null) {
         coordinator.interrupt();
      }
   }

   private void runDownload() {
      this.coordinatorThread = Thread.currentThread();
      long started = System.currentTimeMillis();
      int totalChunks = this.area.totalChunks();
      TerrainPreloadJob.PackagePlan packagePlan = this.packagePlan();
      double packagePreviewResolutionMeters = packagePlan == null ? Double.NaN : packagePlan.previewResolutionMeters();
      int totalTasks = TellusWorldgenSources.preloadAreaTaskCount(
         this.area.minBlockX(),
         this.area.minBlockZ(),
         this.area.maxBlockX(),
         this.area.maxBlockZ(),
         this.settings,
         packagePreviewResolutionMeters
      );
      try {
         this.throwIfCancelled();
         this.downloadBytesRead.set(0L);
         this.downloadBytesExpected.set(-1L);
         this.preplannedBytesRemaining.set(0L);
         this.activeDownloadRequests.set(0);
         this.lastByteProgressUpdateMillis.set(0L);
         this.stagingDirectory = this.storage.createStagingDirectory(this.id);
         this.setProgress(TerrainPreloadStage.DOWNLOADING, "Downloading data", "Planning terrain source coverage", 0, totalTasks, started, true, null);
         try (BufferedWriter writer = this.storage.openChunkIndex(this.stagingDirectory)) {
            writer.write("min_chunk_x,min_chunk_z,max_chunk_x,max_chunk_z,status\n");
            writer.write(
               this.area.minChunkX() + "," + this.area.minChunkZ() + "," + this.area.maxChunkX() + "," + this.area.maxChunkZ() + ",source_data_downloaded\n"
            );
            this.downloadAreaInputs(totalTasks, started, packagePreviewResolutionMeters);
         }

         JsonElement settingsJson = this.storage.encodeSettings(this.settings);
         String fingerprint = this.storage.settingsFingerprint(settingsJson);
         this.throwIfCancelled();
         TerrainPreloadJob.PackageBuild packageBuild = this.buildTerrainPackage(packagePlan, fingerprint, started);
         TerrainPreloadManifest manifest = new TerrainPreloadManifest(
            this.id,
            TerrainPreloadManifest.FORMAT_VERSION,
            System.currentTimeMillis(),
            this.area,
            fingerprint,
            settingsJson,
            totalChunks,
            0,
            packageBuild == null ? "downloaded" : "prepared",
            packageBuild == null ? "" : TerrainPreloadPackage.FILE_NAME,
            packageBuild == null ? 0 : TerrainPreloadPackage.FORMAT_VERSION,
            packageBuild == null ? 0L : packageBuild.bytes(),
            packageBuild == null ? 0 : packageBuild.gridStep(),
            packageBuild == null ? 0 : packageBuild.gridWidth(),
            packageBuild == null ? 0 : packageBuild.gridDepth()
         );
         this.throwIfCancelled();
         this.storage.writeManifest(this.stagingDirectory, manifest);
         this.throwIfCancelled();
         synchronized (this.lifecycleLock) {
            this.throwIfCancelled();
            this.storage.publish(this.stagingDirectory, this.id);
            this.published = true;
            this.processedPackagePublished = packageBuild != null;
            this.setProgress(
               TerrainPreloadStage.COMPLETE,
               "Terrain data cached",
               packageBuild == null
                  ? "Downloaded source files are ready; the area is too large for a processed terrain package at this scale."
                  : "Downloaded sources and the processed terrain package are ready for fast world generation.",
               totalTasks,
               totalTasks,
               started,
               false,
               null
            );
         }
      } catch (TerrainPreloadJob.Cancelled ignored) {
         this.cleanupAfterCancel();
         this.setProgress(TerrainPreloadStage.CANCELLED, "Cancelled", "Preload stopped. Completed downloads remain cached.", 0, totalTasks, started, false, null);
      } catch (Exception error) {
         if (this.cancelRequested.get()) {
            this.cleanupAfterCancel();
            this.setProgress(TerrainPreloadStage.CANCELLED, "Cancelled", "Preload stopped. Completed downloads remain cached.", 0, totalTasks, started, false, null);
         } else {
            Tellus.LOGGER.warn("Terrain preload job {} failed", this.id, error);
            this.cleanupStagingOnly();
            this.setProgress(TerrainPreloadStage.FAILED, "Preload failed", error.getMessage(), 0, totalTasks, started, false, error.toString());
         }
      } finally {
         this.coordinatorThread = null;
      }
   }

   private TerrainPreloadJob.PackageBuild buildTerrainPackage(
      TerrainPreloadJob.PackagePlan plan, String fingerprint, long started
   ) throws IOException {
      if (plan == null) {
         Tellus.LOGGER.info(
            "Skipping processed terrain package {} because the selected area exceeds the configured sample or resolution budget",
            this.id
         );
         return null;
      }

      int total = Math.multiplyExact(plan.gridWidth(), plan.gridDepth());
      int[] terrainHeights = new int[total];
      byte[] coverClasses = new byte[total];
      byte[] landMaskClasses = new byte[total];
      byte[] elevationFlags = new byte[total];
      this.setProgress(
         TerrainPreloadStage.DOWNLOADING,
         "Processing terrain data",
         "Building fast terrain package (0/" + plan.gridDepth() + " rows)",
         0,
         plan.gridDepth(),
         started,
         true,
         null
      );

      ExecutorCompletionService<Integer> completion = new ExecutorCompletionService<>(this.packageExecutor);
      List<Future<Integer>> futures = new ArrayList<>();
      int rowsInFlight = intProperty(
         "tellus.preload.package.rowsInFlight", DEFAULT_PACKAGE_ROWS_IN_FLIGHT, 1, 64
      );
      int nextRow = 0;
      int completedRows = 0;
      try {
         while (nextRow < plan.gridDepth() && futures.size() < rowsInFlight) {
            futures.add(this.submitPackageRow(completion, plan, nextRow++, terrainHeights, coverClasses, landMaskClasses, elevationFlags));
         }

         while (completedRows < plan.gridDepth()) {
            this.awaitIfPaused();
            this.throwIfCancelled();
            Future<Integer> completedFuture;
            try {
               completedFuture = completion.take();
               completedFuture.get();
            } catch (InterruptedException error) {
               Thread.currentThread().interrupt();
               throw new TerrainPreloadJob.Cancelled();
            } catch (ExecutionException error) {
               Throwable cause = error.getCause();
               if (cause instanceof TerrainPreloadJob.Cancelled cancelled) {
                  throw cancelled;
               }
               if (cause instanceof RuntimeException runtime) {
                  throw runtime;
               }
               if (cause instanceof Error fatal) {
                  throw fatal;
               }
               throw new IOException("Failed to build terrain preload package", cause);
            }

            futures.remove(completedFuture);
            completedRows++;
            if (nextRow < plan.gridDepth()) {
               futures.add(this.submitPackageRow(completion, plan, nextRow++, terrainHeights, coverClasses, landMaskClasses, elevationFlags));
            }
            if (completedRows == plan.gridDepth() || completedRows % 8 == 0) {
               this.setProgress(
                  TerrainPreloadStage.DOWNLOADING,
                  "Processing terrain data",
                  "Building fast terrain package (" + completedRows + "/" + plan.gridDepth() + " rows)",
                  completedRows,
                  plan.gridDepth(),
                  started,
                  true,
                  null
               );
            }
         }
      } catch (RuntimeException | Error | IOException error) {
         for (Future<Integer> future : futures) {
            future.cancel(true);
         }
         throw error;
      }

      this.throwIfCancelled();
      this.setProgress(
         TerrainPreloadStage.DOWNLOADING,
         "Saving terrain data",
         "Compressing fast terrain package",
         plan.gridDepth(),
         plan.gridDepth(),
         started,
         true,
         null
      );
      Path packagePath = this.stagingDirectory.resolve(TerrainPreloadPackage.FILE_NAME);
      long bytes = TerrainPreloadPackage.write(
         packagePath,
         this.id,
         fingerprint,
         this.area,
         plan.gridStep(),
         plan.gridWidth(),
         plan.gridDepth(),
         terrainHeights,
         coverClasses,
         landMaskClasses,
         elevationFlags
      );
      Tellus.LOGGER.info(
         "Prepared terrain preload package {}: step={} blocks, grid={}x{}, samples={}, compressedBytes={}",
         this.id,
         plan.gridStep(),
         plan.gridWidth(),
         plan.gridDepth(),
         total,
         bytes
      );
      return new TerrainPreloadJob.PackageBuild(plan.gridStep(), plan.gridWidth(), plan.gridDepth(), bytes);
   }

   private Future<Integer> submitPackageRow(
      ExecutorCompletionService<Integer> completion,
      TerrainPreloadJob.PackagePlan plan,
      int row,
      int[] terrainHeights,
      byte[] coverClasses,
      byte[] landMaskClasses,
      byte[] elevationFlags
   ) {
      return completion.submit(() -> {
         this.activeDownloadRequests.incrementAndGet();
         try {
            this.awaitIfPaused();
            int blockZ = plan.sampleBlockZ(row);
            int offset = row * plan.gridWidth();
            for (int column = 0; column < plan.gridWidth(); column++) {
               if ((column & 63) == 0) {
                  this.awaitIfPaused();
                  this.throwIfCancelled();
               }

               int blockX = plan.sampleBlockX(column);
               TerrainPreloadPackage.Sample sample = TellusWorldgenSources.samplePreloadTerrain(
                  blockX, blockZ, this.settings, plan.previewResolutionMeters()
               );
               int index = offset + column;
               terrainHeights[index] = sample.terrainHeight();
               coverClasses[index] = (byte)sample.coverClass();
               landMaskClasses[index] = TerrainPreloadPackage.encodeLandMask(sample);
               elevationFlags[index] = TerrainPreloadPackage.encodeElevationFlags(sample);
            }
            return row;
         } finally {
            this.activeDownloadRequests.updateAndGet(value -> Math.max(0, value - 1));
         }
      });
   }

   TerrainPreloadJob.PackagePlan packagePlan() {
      double worldScale = this.settings.worldScale();
      if (!Double.isFinite(worldScale) || worldScale <= 0.0) {
         return null;
      }
      double preferredMeters = doubleProperty(
         "tellus.preload.package.gridResolutionMeters", DEFAULT_PACKAGE_GRID_RESOLUTION_METERS, 1.0, 1024.0
      );
      double maxMeters = doubleProperty(
         "tellus.preload.package.maxGridResolutionMeters", DEFAULT_PACKAGE_MAX_GRID_RESOLUTION_METERS, preferredMeters, 4096.0
      );
      int preferredStep = Math.max(1, (int)Math.ceil(preferredMeters / worldScale));
      int maxStep = Math.max(preferredStep, (int)Math.ceil(maxMeters / worldScale));
      int maxSamples = Math.min(
         TerrainPreloadPackage.MAX_LOADED_SAMPLE_COUNT,
         intProperty("tellus.preload.package.maxSamples", DEFAULT_PACKAGE_MAX_SAMPLES, 1, TerrainPreloadPackage.HARD_MAX_SAMPLE_COUNT)
      );
      long spanX = (long)this.area.maxBlockX() - this.area.minBlockX();
      long spanZ = (long)this.area.maxBlockZ() - this.area.minBlockZ();
      int step = preferredStep;
      long total = gridSampleCount(spanX, spanZ, step);
      if (total > maxSamples) {
         long targetSide = Math.max(2L, (long)Math.floor(Math.sqrt(maxSamples)));
         long requiredX = divideCeil(spanX, targetSide - 1L);
         long requiredZ = divideCeil(spanZ, targetSide - 1L);
         long required = Math.max(requiredX, requiredZ);
         if (required > Integer.MAX_VALUE) {
            return null;
         }
         step = Math.max(step, (int)required);
         total = gridSampleCount(spanX, spanZ, step);
      }

      if (step > maxStep || total > maxSamples || total > Integer.MAX_VALUE) {
         return null;
      }

      int gridWidth = Math.toIntExact(divideCeil(spanX, step) + 1L);
      int gridDepth = Math.toIntExact(divideCeil(spanZ, step) + 1L);
      return new TerrainPreloadJob.PackagePlan(
         this.area.minBlockX(), this.area.minBlockZ(), step, gridWidth, gridDepth, Math.max(worldScale, step * worldScale)
      );
   }

   private static long gridSampleCount(long spanX, long spanZ, int step) {
      long width = divideCeil(spanX, step) + 1L;
      long depth = divideCeil(spanZ, step) + 1L;
      try {
         return Math.multiplyExact(width, depth);
      } catch (ArithmeticException ignored) {
         return Long.MAX_VALUE;
      }
   }

   private static long divideCeil(long value, long divisor) {
      return value <= 0L ? 0L : 1L + (value - 1L) / divisor;
   }

   private static int intProperty(String key, int defaultValue, int min, int max) {
      String value = System.getProperty(key);
      if (value == null) {
         return Math.max(min, Math.min(max, defaultValue));
      }
      try {
         return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
      } catch (NumberFormatException ignored) {
         return Math.max(min, Math.min(max, defaultValue));
      }
   }

   private static double doubleProperty(String key, double defaultValue, double min, double max) {
      String value = System.getProperty(key);
      if (value == null) {
         return Math.max(min, Math.min(max, defaultValue));
      }
      try {
         double parsed = Double.parseDouble(value.trim());
         return Double.isFinite(parsed) ? Math.max(min, Math.min(max, parsed)) : Math.max(min, Math.min(max, defaultValue));
      } catch (NumberFormatException ignored) {
         return Math.max(min, Math.min(max, defaultValue));
      }
   }

   private void downloadAreaInputs(int totalTasks, long started, double packagePreviewResolutionMeters) {
      try (DownloadProgressReporter.Scope scope = DownloadProgressReporter.push(new DownloadProgressReporter.Listener() {
         @Override
         public void onExpectedBytesKnown(long expectedBytes) {
            if (expectedBytes > 0L) {
               TerrainPreloadJob.this.addExpectedDownloadBytes(expectedBytes);
               TerrainPreloadJob.this.preplannedBytesRemaining.updateAndGet(
                  current -> {
                     long remaining = Long.MAX_VALUE - current;
                     return expectedBytes > remaining ? Long.MAX_VALUE : current + expectedBytes;
                  }
               );
               TerrainPreloadJob.this.updateByteProgress(started);
            }
         }

         @Override
         public void onRequestStarted(long expectedBytes) {
            TerrainPreloadJob.this.activeDownloadRequests.incrementAndGet();
            if (expectedBytes > 0L) {
               long planned = TerrainPreloadJob.this.consumePreplannedBytes(expectedBytes);
               long unplanned = Math.max(0L, expectedBytes - planned);
               if (unplanned > 0L) {
                  TerrainPreloadJob.this.addExpectedDownloadBytes(unplanned);
               }
            }

            TerrainPreloadJob.this.updateByteProgress(started);
         }

         @Override
         public void onBytesRead(int bytes) {
            TerrainPreloadJob.this.downloadBytesRead.addAndGet(bytes);
            TerrainPreloadJob.this.updateByteProgress(started);
         }

         @Override
         public void onRequestFinished() {
            TerrainPreloadJob.this.activeDownloadRequests.updateAndGet(value -> Math.max(0, value - 1));
            TerrainPreloadJob.this.updateByteProgress(started);
         }
      })) {
         Objects.requireNonNull(scope, "downloadProgressScope");
         TellusWorldgenSources.preloadAreaInputs(
            this.area.minBlockX(),
            this.area.minBlockZ(),
            this.area.maxBlockX(),
            this.area.maxBlockZ(),
            this.settings,
            packagePreviewResolutionMeters,
            (completed, detail) -> {
               this.awaitIfPaused();
               this.throwIfCancelled();
               this.setProgress(
                  TerrainPreloadStage.DOWNLOADING,
                  "Downloading data",
                  detail,
                  Math.min(Math.max(0, completed), totalTasks),
                  totalTasks,
                  started,
                  true,
                  null
               );
            }
         );
      }
   }

   private void addExpectedDownloadBytes(long expectedBytes) {
      if (expectedBytes > 0L) {
         this.downloadBytesExpected.updateAndGet(
            current -> {
               if (current < 0L) {
                  return expectedBytes;
               }

               long remaining = Long.MAX_VALUE - current;
               return expectedBytes > remaining ? Long.MAX_VALUE : current + expectedBytes;
            }
         );
      }
   }

   private long consumePreplannedBytes(long expectedBytes) {
      if (expectedBytes <= 0L) {
         return 0L;
      }

      AtomicLong consumed = new AtomicLong();
      this.preplannedBytesRemaining.updateAndGet(
         current -> {
            long value = Math.min(current, expectedBytes);
            consumed.set(value);
            return current - value;
         }
      );
      return consumed.get();
   }

   private void awaitIfPaused() {
      synchronized (this.pauseLock) {
         while (this.pauseRequested.get() && !this.cancelRequested.get()) {
            try {
               this.pauseLock.wait(250L);
            } catch (InterruptedException ignored) {
               Thread.currentThread().interrupt();
               throw new TerrainPreloadJob.Cancelled();
            }
         }
      }
   }

   private void throwIfCancelled() {
      if (this.cancelRequested.get() || Thread.currentThread().isInterrupted()) {
         throw new TerrainPreloadJob.Cancelled();
      }
   }

   private void setProgress(
      TerrainPreloadStage stage,
      String status,
      String detail,
      int completedUnits,
      int totalUnits,
      long startedAtMillis,
      boolean cancellable,
      String error
   ) {
      long bytesRead = switch (stage) {
         case DOWNLOADING, COMPLETE -> this.downloadBytesRead.get();
         default -> 0L;
      };
      long bytesExpected = switch (stage) {
         case DOWNLOADING, COMPLETE -> this.downloadBytesExpected.get();
         default -> -1L;
      };
      int activeCount = this.activeDownloadRequests.get();
      this.progress.set(
         new TerrainPreloadProgress(
            this.id,
            stage,
            status,
            detail == null ? "" : detail,
            this.sourceDetail,
            Math.max(0, completedUnits),
            Math.max(0, totalUnits),
            bytesRead,
            bytesExpected,
            startedAtMillis,
            activeCount,
            this.pauseRequested.get(),
            cancellable,
            error
         )
      );
   }

   private void updateByteProgress(long startedAtMillis) {
      long now = System.currentTimeMillis();
      long last = this.lastByteProgressUpdateMillis.get();
      if (now - last < 500L && this.activeDownloadRequests.get() > 0) {
         return;
      }

      if (!this.lastByteProgressUpdateMillis.compareAndSet(last, now)) {
         return;
      }

      TerrainPreloadProgress current = this.progress.get();
      if (current.stage() == TerrainPreloadStage.DOWNLOADING) {
         this.setProgress(
            current.stage(),
            current.status(),
            current.detail(),
            current.completedUnits(),
            current.totalUnits(),
            startedAtMillis,
            current.cancellable(),
            current.error()
         );
      }
   }

   private void updatePaused(boolean paused) {
      TerrainPreloadProgress current = this.progress.get();
      this.progress.set(
         new TerrainPreloadProgress(
            current.jobId(),
            current.stage(),
            current.status(),
            current.detail(),
            current.sourceDetail(),
            current.completedUnits(),
            current.totalUnits(),
            current.bytesRead(),
            current.bytesExpected(),
            current.startedAtMillis(),
            current.activeWorkers(),
            paused,
            current.cancellable(),
            current.error()
         )
      );
   }

   private void cleanupAfterCancel() {
      this.cleanupStagingOnly();
      if (this.published) {
         this.storage.deletePackage(this.id);
      }
   }

   private static boolean isTerminal(TerrainPreloadStage stage) {
      return stage == TerrainPreloadStage.COMPLETE || stage == TerrainPreloadStage.CANCELLED || stage == TerrainPreloadStage.FAILED;
   }

   private void cleanupStagingOnly() {
      try {
         TerrainPreloadStorage.deleteTree(this.stagingDirectory);
      } catch (IOException error) {
         Tellus.LOGGER.warn("Failed to delete terrain preload staging folder {}", this.stagingDirectory, error);
      }
   }

   static String sourceDetail(EarthGeneratorSettings settings) {
      List<String> sources = new ArrayList<>();
      sources.add("DEM elevation");
      sources.add("ESA WorldCover land cover");
      if (settings.customTrees()) {
         sources.add("ETH canopy height");
      }
      sources.add("land mask");
      sources.add("OSM sand");
      if (settings.enableWater()) {
         sources.add("Overture water");
      }

      if (settings.enableRoads() && settings.worldScale() <= 15.0) {
         sources.add("OSM roads");
         sources.add("OSM infrastructure");
      }

      if (settings.enableBuildings() && settings.worldScale() <= 15.0) {
         sources.add("OSM buildings");
      }

      return String.join(", ", sources);
   }

   record PackagePlan(
      int minBlockX,
      int minBlockZ,
      int gridStep,
      int gridWidth,
      int gridDepth,
      double previewResolutionMeters
   ) {
      private int sampleBlockX(int column) {
         return Math.toIntExact((long)this.minBlockX + (long)column * this.gridStep);
      }

      private int sampleBlockZ(int row) {
         return Math.toIntExact((long)this.minBlockZ + (long)row * this.gridStep);
      }
   }

   private record PackageBuild(int gridStep, int gridWidth, int gridDepth, long bytes) {
   }

   private static final class Cancelled extends RuntimeException {
   }
}
