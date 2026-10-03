package com.yucareux.tellus.preload;

import com.google.gson.JsonElement;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class TerrainPreloadPackageRegistry {
   private static final long REFRESH_INTERVAL_MILLIS = 5000L;
   private static final int INDEX_BUCKET_CHUNKS = 256;
   private static final long MAX_INDEX_BUCKETS_PER_ENTRY = 1_000_000L;
   private static final ExecutorService REFRESH_EXECUTOR = Executors.newSingleThreadExecutor(daemonFactory("tellus-preload-index-"));
   private static final ExecutorService LOAD_EXECUTOR = Executors.newSingleThreadExecutor(daemonFactory("tellus-preload-load-"));
   private static final TerrainPreloadPackageRegistry INSTANCE = new TerrainPreloadPackageRegistry();
   private final ConcurrentMap<EarthGeneratorSettings, TerrainPreloadPackageRegistry.SettingsView> settingsViews = new ConcurrentHashMap<>();
   private final AtomicBoolean refreshScheduled = new AtomicBoolean();
   private final AtomicLong invalidationGeneration = new AtomicLong();
   private final AtomicLong snapshotGeneration = new AtomicLong();
   private volatile long appliedInvalidationGeneration = -1L;
   private volatile long lastRefreshMillis;
   private volatile TerrainPreloadPackageRegistry.Snapshot snapshot = TerrainPreloadPackageRegistry.Snapshot.empty();

   private TerrainPreloadPackageRegistry() {
   }

   public static TerrainPreloadPackageRegistry instance() {
      return INSTANCE;
   }

   public TerrainPreloadPackageRegistry.SettingsView viewFor(EarthGeneratorSettings settings) {
      Objects.requireNonNull(settings, "settings");
      this.requestRefreshIfNeeded();
      return this.settingsViews.computeIfAbsent(settings, this::createSettingsView);
   }

   public void invalidate() {
      this.invalidationGeneration.incrementAndGet();
      this.lastRefreshMillis = 0L;
      this.requestRefreshIfNeeded();
   }

   private TerrainPreloadPackageRegistry.SettingsView createSettingsView(EarthGeneratorSettings settings) {
      TerrainPreloadStorage storage = TerrainPreloadStorage.instance();
      JsonElement settingsJson = storage.encodeSettings(settings);
      String settingsFingerprint = storage.settingsFingerprint(settingsJson);
      return new TerrainPreloadPackageRegistry.SettingsView(this, settingsFingerprint);
   }

   private void requestRefreshIfNeeded() {
      long invalidation = this.invalidationGeneration.get();
      long now = System.currentTimeMillis();
      if (this.appliedInvalidationGeneration == invalidation && now - this.lastRefreshMillis <= REFRESH_INTERVAL_MILLIS) {
         return;
      }

      if (!this.refreshScheduled.compareAndSet(false, true)) {
         return;
      }

      try {
         REFRESH_EXECUTOR.execute(this::runRefreshLoop);
      } catch (RejectedExecutionException error) {
         this.refreshScheduled.set(false);
         Tellus.LOGGER.warn("Failed to schedule terrain preload package index refresh", error);
      }
   }

   private void runRefreshLoop() {
      try {
         while (true) {
            long targetInvalidation = this.invalidationGeneration.get();
            try {
               this.refreshSnapshot();
            } catch (RuntimeException error) {
               Tellus.LOGGER.warn("Failed to refresh terrain preload package index", error);
            }
            this.appliedInvalidationGeneration = targetInvalidation;
            this.lastRefreshMillis = System.currentTimeMillis();
            if (targetInvalidation == this.invalidationGeneration.get()) {
               return;
            }
         }
      } finally {
         this.refreshScheduled.set(false);
         if (this.appliedInvalidationGeneration != this.invalidationGeneration.get()) {
            this.requestRefreshIfNeeded();
         }
      }
   }

   private void refreshSnapshot() {
      TerrainPreloadStorage storage = TerrainPreloadStorage.instance();
      TerrainPreloadPackageRegistry.Snapshot previous = this.snapshot;
      Map<Path, TerrainPreloadPackageRegistry.Entry> previousByPath = new HashMap<>();
      for (TerrainPreloadPackageRegistry.Entry entry : previous.entries()) {
         previousByPath.put(entry.path(), entry);
      }

      List<TerrainPreloadPackageRegistry.Entry> refreshed = new ArrayList<>();
      for (TerrainPreloadManifest manifest : storage.listManifests()) {
         TerrainPreloadPackageRegistry.Entry entry = this.entryForManifest(storage, manifest, previousByPath);
         if (entry != null) {
            refreshed.add(entry);
         }
      }

      refreshed.sort(
         Comparator.comparingLong((TerrainPreloadPackageRegistry.Entry entry) -> entry.manifest().createdAtMillis())
            .reversed()
            .thenComparingDouble(TerrainPreloadPackageRegistry.Entry::resolutionMeters)
            .thenComparing(entry -> entry.path().toString())
      );
      if (previous.entries().equals(refreshed)) {
         return;
      }
      long generation = this.snapshotGeneration.incrementAndGet();
      this.snapshot = TerrainPreloadPackageRegistry.Snapshot.create(generation, refreshed);
   }

   private TerrainPreloadPackageRegistry.Entry entryForManifest(
      TerrainPreloadStorage storage,
      TerrainPreloadManifest manifest,
      Map<Path, TerrainPreloadPackageRegistry.Entry> previousByPath
   ) {
      if (manifest == null
         || !TerrainPreloadStorage.isValidIdentifier(manifest.id())
         || manifest.area() == null
         || manifest.settingsFingerprint() == null
         || !TerrainPreloadPackage.FILE_NAME.equals(manifest.packageFile())
         || manifest.packageFormatVersion() != TerrainPreloadPackage.FORMAT_VERSION) {
         return null;
      }

      try {
         TerrainPreloadPackage.checkedSampleCount(manifest.packageGridWidth(), manifest.packageGridDepth());
      } catch (IOException error) {
         Tellus.LOGGER.warn("Ignoring invalid terrain preload package manifest {}: {}", manifest.id(), error.getMessage());
         return null;
      }
      if (manifest.packageGridStep() <= 0
         || !Double.isFinite(manifest.area().worldScale())
         || manifest.area().worldScale() <= 0.0
         || manifest.area().minChunkX() > manifest.area().maxChunkX()
         || manifest.area().minChunkZ() > manifest.area().maxChunkZ()) {
         Tellus.LOGGER.warn("Ignoring invalid terrain preload package manifest {}", manifest.id());
         return null;
      }

      Path storageRoot = storage.root().toAbsolutePath().normalize();
      Path publishedDirectory = storage.publishedDirectory(manifest.id()).toAbsolutePath().normalize();
      Path path = publishedDirectory.resolve(manifest.packageFile()).normalize();
      if (!publishedDirectory.startsWith(storageRoot)
         || !path.startsWith(publishedDirectory)
         || Files.isSymbolicLink(publishedDirectory)
         || Files.isSymbolicLink(path)
         || !Files.isRegularFile(path)) {
         return null;
      }

      TerrainPreloadPackageRegistry.Entry previous = previousByPath.get(path);
      if (previous != null && previous.canReuse(manifest)) {
         return previous;
      }
      return new TerrainPreloadPackageRegistry.Entry(manifest, path);
   }

   private static ThreadFactory daemonFactory(String prefix) {
      return new ThreadFactory() {
         private final AtomicInteger index = new AtomicInteger();

         @Override
         public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + this.index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
         }
      };
   }

   public static final class SettingsView {
      private final TerrainPreloadPackageRegistry registry;
      private final String settingsFingerprint;
      private volatile TerrainPreloadPackageRegistry.CachedIndex cachedIndex = TerrainPreloadPackageRegistry.CachedIndex.empty();

      private SettingsView(TerrainPreloadPackageRegistry registry, String settingsFingerprint) {
         this.registry = registry;
         this.settingsFingerprint = settingsFingerprint;
      }

      public TerrainPreloadPackage.Sample sample(int blockX, int blockZ) {
         return this.sample(blockX, blockZ, Double.POSITIVE_INFINITY);
      }

      public TerrainPreloadPackage.Sample sample(int blockX, int blockZ, double requestedResolutionMeters) {
         for (TerrainPreloadPackageRegistry.Entry entry : this.entriesFor(blockX, blockZ)) {
            if (!entry.contains(blockX, blockZ) || !entry.supportsResolution(requestedResolutionMeters)) {
               continue;
            }

            TerrainPreloadPackage pack = entry.readyOrScheduleLoad();
            if (pack == null) {
               if (!entry.failed()) {
                  return null;
               }
               continue;
            }
            if (pack.matches(this.settingsFingerprint, blockX, blockZ) && pack.supportsResolution(requestedResolutionMeters)) {
               return pack.sample(blockX, blockZ);
            }
         }
         return null;
      }

      public boolean contains(int blockX, int blockZ) {
         return this.contains(blockX, blockZ, Double.POSITIVE_INFINITY);
      }

      public boolean contains(int blockX, int blockZ, double requestedResolutionMeters) {
         for (TerrainPreloadPackageRegistry.Entry entry : this.entriesFor(blockX, blockZ)) {
            if (!entry.contains(blockX, blockZ) || !entry.supportsResolution(requestedResolutionMeters)) {
               continue;
            }

            TerrainPreloadPackage pack = entry.readyOrScheduleLoad();
            if (pack == null) {
               if (!entry.failed()) {
                  return false;
               }
               continue;
            }
            if (pack.matches(this.settingsFingerprint, blockX, blockZ) && pack.supportsResolution(requestedResolutionMeters)) {
               return true;
            }
         }
         return false;
      }

      private List<TerrainPreloadPackageRegistry.Entry> entriesFor(int blockX, int blockZ) {
         this.registry.requestRefreshIfNeeded();
         TerrainPreloadPackageRegistry.Snapshot current = this.registry.snapshot;
         TerrainPreloadPackageRegistry.CachedIndex cached = this.cachedIndex;
         if (cached.generation() != current.generation()) {
            cached = new TerrainPreloadPackageRegistry.CachedIndex(
               current.generation(), current.indexFor(this.settingsFingerprint)
            );
            this.cachedIndex = cached;
         }
         return cached.index().entriesFor(blockX, blockZ);
      }
   }

   private static final class Entry {
      private final TerrainPreloadManifest manifest;
      private final Path path;
      private final AtomicBoolean loadScheduled = new AtomicBoolean();
      private volatile TerrainPreloadPackage pack;
      private volatile boolean failed;

      private Entry(TerrainPreloadManifest manifest, Path path) {
         this.manifest = manifest;
         this.path = path;
      }

      private TerrainPreloadManifest manifest() {
         return this.manifest;
      }

      private Path path() {
         return this.path;
      }

      private boolean canReuse(TerrainPreloadManifest nextManifest) {
         return !this.failed && this.manifest.equals(nextManifest);
      }

      private boolean contains(int blockX, int blockZ) {
         return this.manifest.area().containsChunk(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
      }

      private double resolutionMeters() {
         return this.manifest.packageGridStep() * this.manifest.area().worldScale();
      }

      private boolean supportsResolution(double requestedResolutionMeters) {
         return !Double.isFinite(requestedResolutionMeters)
            || requestedResolutionMeters <= 0.0
            || requestedResolutionMeters + Math.ulp(requestedResolutionMeters) >= this.resolutionMeters();
      }

      private boolean failed() {
         return this.failed;
      }

      private TerrainPreloadPackage readyOrScheduleLoad() {
         TerrainPreloadPackage current = this.pack;
         if (current != null || this.failed) {
            return current;
         }

         if (this.loadScheduled.compareAndSet(false, true)) {
            try {
               LOAD_EXECUTOR.execute(this::load);
            } catch (RejectedExecutionException error) {
               this.loadScheduled.set(false);
               Tellus.LOGGER.warn("Failed to schedule terrain preload package load {}", this.path, error);
            }
         }
         return null;
      }

      private void load() {
         try {
            TerrainPreloadPackage loaded = TerrainPreloadPackage.read(this.path);
            if (!loaded.id().equals(this.manifest.id())
               || !loaded.settingsFingerprint().equals(this.manifest.settingsFingerprint())
               || !loaded.area().equals(this.manifest.area())
               || loaded.gridStep() != this.manifest.packageGridStep()
               || loaded.gridWidth() != this.manifest.packageGridWidth()
               || loaded.gridDepth() != this.manifest.packageGridDepth()) {
               throw new IOException("Terrain preload package header does not match its manifest");
            }
            this.pack = loaded;
         } catch (Exception error) {
            this.failed = true;
            Tellus.LOGGER.warn("Failed to load terrain preload package {}", this.path, error);
         } catch (OutOfMemoryError error) {
            this.failed = true;
            Tellus.LOGGER.warn("Not enough memory to load terrain preload package {} within the configured safety bound", this.path);
         }
      }
   }

   private record CachedIndex(long generation, TerrainPreloadPackageRegistry.EntryIndex index) {
      private static TerrainPreloadPackageRegistry.CachedIndex empty() {
         return new TerrainPreloadPackageRegistry.CachedIndex(Long.MIN_VALUE, TerrainPreloadPackageRegistry.EntryIndex.empty());
      }
   }

   private static final class EntryIndex {
      private static final TerrainPreloadPackageRegistry.EntryIndex EMPTY = new TerrainPreloadPackageRegistry.EntryIndex(Map.of());
      private final Map<Long, List<TerrainPreloadPackageRegistry.Entry>> entriesByBucket;

      private EntryIndex(Map<Long, List<TerrainPreloadPackageRegistry.Entry>> entriesByBucket) {
         this.entriesByBucket = entriesByBucket;
      }

      private static TerrainPreloadPackageRegistry.EntryIndex empty() {
         return EMPTY;
      }

      private static TerrainPreloadPackageRegistry.EntryIndex create(List<TerrainPreloadPackageRegistry.Entry> entries) {
         if (entries.isEmpty()) {
            return EMPTY;
         }

         Map<Long, List<TerrainPreloadPackageRegistry.Entry>> mutable = new HashMap<>();
         for (TerrainPreloadPackageRegistry.Entry entry : entries) {
            TerrainPreloadArea area = entry.manifest().area();
            int minBucketX = Math.floorDiv(area.minChunkX(), INDEX_BUCKET_CHUNKS);
            int maxBucketX = Math.floorDiv(area.maxChunkX(), INDEX_BUCKET_CHUNKS);
            int minBucketZ = Math.floorDiv(area.minChunkZ(), INDEX_BUCKET_CHUNKS);
            int maxBucketZ = Math.floorDiv(area.maxChunkZ(), INDEX_BUCKET_CHUNKS);
            long width = (long)maxBucketX - minBucketX + 1L;
            long depth = (long)maxBucketZ - minBucketZ + 1L;
            if (width <= 0L || depth <= 0L || width > MAX_INDEX_BUCKETS_PER_ENTRY / depth) {
               Tellus.LOGGER.warn("Ignoring terrain preload package {} with excessive index bounds", entry.manifest().id());
               continue;
            }

            for (int bucketZ = minBucketZ; bucketZ <= maxBucketZ; bucketZ++) {
               for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                  mutable.computeIfAbsent(bucketKey(bucketX, bucketZ), ignored -> new ArrayList<>()).add(entry);
                  if (bucketX == Integer.MAX_VALUE) {
                     break;
                  }
               }
               if (bucketZ == Integer.MAX_VALUE) {
                  break;
               }
            }
         }

         Map<Long, List<TerrainPreloadPackageRegistry.Entry>> immutable = new HashMap<>(mutable.size());
         mutable.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
         return new TerrainPreloadPackageRegistry.EntryIndex(Map.copyOf(immutable));
      }

      private List<TerrainPreloadPackageRegistry.Entry> entriesFor(int blockX, int blockZ) {
         int chunkX = Math.floorDiv(blockX, 16);
         int chunkZ = Math.floorDiv(blockZ, 16);
         int bucketX = Math.floorDiv(chunkX, INDEX_BUCKET_CHUNKS);
         int bucketZ = Math.floorDiv(chunkZ, INDEX_BUCKET_CHUNKS);
         return this.entriesByBucket.getOrDefault(bucketKey(bucketX, bucketZ), List.of());
      }

      private static long bucketKey(int bucketX, int bucketZ) {
         return (long)bucketX << 32 | bucketZ & 0xFFFFFFFFL;
      }
   }

   private record Snapshot(
      long generation,
      List<TerrainPreloadPackageRegistry.Entry> entries,
      Map<String, TerrainPreloadPackageRegistry.EntryIndex> indexesByFingerprint
   ) {
      private static TerrainPreloadPackageRegistry.Snapshot empty() {
         return new TerrainPreloadPackageRegistry.Snapshot(0L, List.of(), Map.of());
      }

      private static TerrainPreloadPackageRegistry.Snapshot create(
         long generation, List<TerrainPreloadPackageRegistry.Entry> entries
      ) {
         Map<String, List<TerrainPreloadPackageRegistry.Entry>> grouped = new HashMap<>();
         for (TerrainPreloadPackageRegistry.Entry entry : entries) {
            grouped.computeIfAbsent(entry.manifest().settingsFingerprint(), ignored -> new ArrayList<>()).add(entry);
         }

         Map<String, TerrainPreloadPackageRegistry.EntryIndex> indexes = new HashMap<>(grouped.size());
         grouped.forEach((fingerprint, matching) -> indexes.put(fingerprint, TerrainPreloadPackageRegistry.EntryIndex.create(matching)));
         return new TerrainPreloadPackageRegistry.Snapshot(generation, List.copyOf(entries), Map.copyOf(indexes));
      }

      private TerrainPreloadPackageRegistry.EntryIndex indexFor(String fingerprint) {
         return this.indexesByFingerprint.getOrDefault(fingerprint, TerrainPreloadPackageRegistry.EntryIndex.empty());
      }
   }
}
