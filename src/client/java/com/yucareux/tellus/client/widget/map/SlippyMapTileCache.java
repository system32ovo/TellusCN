package com.yucareux.tellus.client.widget.map;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.cache.TellusCacheDomain;
import com.yucareux.tellus.cache.TellusCacheFiles;
import com.yucareux.tellus.cache.TellusCacheHandle;
import com.yucareux.tellus.cache.TellusCacheRegistry;
import com.yucareux.tellus.config.TellusEndpointConfig;
import com.yucareux.tellus.world.data.source.MapTileImageValidator;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;

public class SlippyMapTileCache implements TellusCacheHandle {
   private static final int CACHE_SIZE = 1024;
   private static final int LOAD_THREADS = 4;
   private static final int MAX_ACTIVE_LOADS = 256;
   private static final int MAX_TILE_BYTES = 4 * 1024 * 1024;
   private static final int MAX_TILE_DIMENSION = 512;
   private static final long RETRY_BASE_NANOS = TimeUnit.SECONDS.toNanos(1L);
   private static final long RETRY_MAX_NANOS = TimeUnit.SECONDS.toNanos(30L);
   private static final String USER_AGENT = "Tellus-Minecraft-Mod (+https://github.com/Yucareux/Tellus)";
   // 官方 OpenStreetMap 瓦片基础地址，作为镜像失效时的兜底候选
   private static final String OFFICIAL_MAP_TILE_BASE_URL = "https://tile.openstreetmap.org";
   private final Object schedulingLock = new Object();
   private final AtomicLong taskSequence = new AtomicLong();
   private final ThreadPoolExecutor loadingService = new ThreadPoolExecutor(
      LOAD_THREADS,
      LOAD_THREADS,
      0L,
      TimeUnit.MILLISECONDS,
      new PriorityBlockingQueue<>(),
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("tellus-map-load-%d").build()
   );
   private final Path cacheRoot = Minecraft.getInstance().gameDirectory.toPath().resolve("tellus/cache/map");
   private volatile boolean shuttingDown;
   private long viewportRevision;
   private Map<SlippyMapTilePos, Integer> viewportPriorities = Map.of();
   private final LoadingCache<SlippyMapTilePos, SlippyMapTileCache.TileEntry> tileCache = CacheBuilder.newBuilder()
      .maximumSize(CACHE_SIZE)
      .removalListener(notification -> {
         SlippyMapTileCache.TileEntry entry = (SlippyMapTileCache.TileEntry)notification.getValue();
         if (entry != null) {
            synchronized (SlippyMapTileCache.this.schedulingLock) {
               SlippyMapTileCache.this.cancelTaskLocked(entry);
            }
            entry.tile.delete();
         }
      })
      .build(new CacheLoader<SlippyMapTilePos, SlippyMapTileCache.TileEntry>() {
         public SlippyMapTileCache.TileEntry load(SlippyMapTilePos key) {
            return new SlippyMapTileCache.TileEntry(key);
         }
      });

   public SlippyMapTileCache() {
      TellusCacheRegistry.register(this);
   }

   public SlippyMapTile getTile(SlippyMapTilePos pos) {
      SlippyMapTileCache.TileEntry entry;
      try {
         entry = this.tileCache.getUnchecked(pos);
      } catch (RuntimeException error) {
         Tellus.LOGGER.warn("Failed to create map tile {}", pos, error);
         return new SlippyMapTile(pos);
      }

      synchronized (this.schedulingLock) {
         Integer priority = this.viewportPriorities.get(pos);
         if (priority != null) {
            this.requestLoadLocked(entry, priority, this.viewportRevision, System.nanoTime());
         }
      }
      return entry.tile;
   }

   public void updateViewport(List<SlippyMapTilePos> visibleTiles) {
      if (this.shuttingDown) {
         return;
      }
      Map<SlippyMapTilePos, Integer> plannedPriorities = buildViewportPriorities(visibleTiles);
      List<Map.Entry<SlippyMapTilePos, Integer>> ordered = new ArrayList<>(plannedPriorities.entrySet());
      ordered.sort(Map.Entry.comparingByValue());
      long now = System.nanoTime();

      synchronized (this.schedulingLock) {
         boolean viewportChanged = !plannedPriorities.equals(this.viewportPriorities);
         if (viewportChanged) {
            this.viewportRevision++;
            this.viewportPriorities = Map.copyOf(plannedPriorities);
         }

         if (viewportChanged) {
            for (Map.Entry<SlippyMapTilePos, SlippyMapTileCache.TileEntry> cached : this.tileCache.asMap().entrySet()) {
               SlippyMapTileCache.TileEntry entry = cached.getValue();
               SlippyMapTileCache.TileLoadTask task = entry.task;
               Integer priority = plannedPriorities.get(cached.getKey());
               if (task != null && (priority == null || !task.started && (task.revision != this.viewportRevision || task.priority != priority))) {
                  this.cancelTaskLocked(entry);
               }
            }
         }

         for (Map.Entry<SlippyMapTilePos, Integer> request : ordered) {
            if (this.currentLoadCount() >= MAX_ACTIVE_LOADS) {
               break;
            }
            SlippyMapTileCache.TileEntry entry = this.tileCache.getUnchecked(request.getKey());
            this.requestLoadLocked(entry, request.getValue(), this.viewportRevision, now);
         }
      }
   }

   public void shutdown() {
      this.shuttingDown = true;
      this.clearCache();
      this.loadingService.shutdownNow();
   }

   @Override
   public TellusCacheDomain cacheDomain() {
      return TellusCacheDomain.OSM;
   }

   @Override
   public void clearCache() {
      synchronized (this.schedulingLock) {
         for (SlippyMapTileCache.TileEntry entry : this.tileCache.asMap().values()) {
            this.cancelTaskLocked(entry);
         }
      }
      this.tileCache.invalidateAll();
      this.tileCache.cleanUp();
   }

   private int currentLoadCount() {
      return this.loadingService.getActiveCount() + this.loadingService.getQueue().size();
   }

   private void requestLoadLocked(SlippyMapTileCache.TileEntry entry, int priority, long revision, long now) {
      if (this.shuttingDown || entry.loaded || entry.task != null || now < entry.retryAtNanos || this.currentLoadCount() >= MAX_ACTIVE_LOADS) {
         return;
      }

      long generation = TellusCacheRegistry.generation(TellusCacheDomain.OSM);
      SlippyMapTileCache.TileLoadTask task = new SlippyMapTileCache.TileLoadTask(
         entry, generation, revision, priority, this.taskSequence.incrementAndGet()
      );
      entry.task = task;
      try {
         this.loadingService.execute(task);
      } catch (RejectedExecutionException ignored) {
         if (entry.task == task) {
            entry.task = null;
            entry.retryAtNanos = now + RETRY_BASE_NANOS;
         }
      }
   }

   private void cancelTaskLocked(SlippyMapTileCache.TileEntry entry) {
      SlippyMapTileCache.TileLoadTask task = entry.task;
      if (task != null) {
         entry.task = null;
         task.cancel();
      }
   }

   private void loadTile(SlippyMapTileCache.TileLoadTask task) {
      NativeImage image;
      try {
         byte[] data = this.readTileData(task.entry.pos, task.generation, task);
         if (task.cancelled || data == null) {
            this.finishCancelled(task);
            return;
         }
         image = NativeImage.read(new ByteArrayInputStream(data));
      } catch (IOException error) {
         if (this.isCancelledLoad(error, task)) {
            this.finishCancelled(task);
         } else {
            this.finishFailed(task, error);
         }
         return;
      } catch (RuntimeException error) {
         if (task.cancelled || this.shuttingDown) {
            this.finishCancelled(task);
         } else {
            this.finishFailed(task, error);
         }
         return;
      }

      boolean accepted;
      synchronized (this.schedulingLock) {
         accepted = !task.cancelled
            && !this.shuttingDown
            && task.entry.task == task
            && TellusCacheRegistry.isCurrent(TellusCacheDomain.OSM, task.generation);
         if (task.entry.task == task) {
            task.entry.task = null;
         }
         if (accepted) {
            task.entry.loaded = true;
            task.entry.failures = 0;
            task.entry.retryAtNanos = 0L;
         }
      }

      if (accepted) {
         task.entry.tile.supplyImage(image);
      } else {
         image.close();
      }
   }

   private void finishCancelled(SlippyMapTileCache.TileLoadTask task) {
      synchronized (this.schedulingLock) {
         if (task.entry.task == task) {
            task.entry.task = null;
         }
      }
   }

   private void finishFailed(SlippyMapTileCache.TileLoadTask task, Throwable error) {
      int failures;
      long retryDelay;
      synchronized (this.schedulingLock) {
         if (task.cancelled || this.shuttingDown || task.entry.task != task) {
            return;
         }
         task.entry.task = null;
         failures = Math.min(task.entry.failures + 1, 30);
         task.entry.failures = failures;
         retryDelay = retryDelayNanos(error, failures);
         task.entry.retryAtNanos = System.nanoTime() + retryDelay;
      }

      if (failures == 1) {
         Tellus.LOGGER.warn(
            "Failed to load map tile {}; retrying in {} ms",
            task.entry.pos,
            TimeUnit.NANOSECONDS.toMillis(retryDelay),
            error
         );
      } else {
         Tellus.LOGGER.debug("Map tile {} failed again (attempt {})", task.entry.pos, failures, error);
      }
   }

   private byte[] readTileData(SlippyMapTilePos pos, long generation, SlippyMapTileCache.TileLoadTask task) throws IOException {
      this.throwIfCancelled(task);
      Path cachePath = this.cacheRoot.resolve(pos.getCacheName());
      if (Files.isRegularFile(cachePath)) {
         if (Files.size(cachePath) <= MAX_TILE_BYTES) {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(cachePath))) {
               byte[] data = MapTileImageValidator.readBounded(input, MAX_TILE_BYTES);
               MapTileImageValidator.validatePng(data, MAX_TILE_DIMENSION, MAX_TILE_DIMENSION);
               this.throwIfCancelled(task);
               return data;
            } catch (IOException invalidCache) {
               if (this.isCancelledLoad(invalidCache, task)) {
                  throw invalidCache;
               }
               Files.deleteIfExists(cachePath);
            }
         } else {
            Files.deleteIfExists(cachePath);
         }
      }

      // 解析地图瓦片候选地址：优先国内镜像，官方 OpenStreetMap 兜底
      List<String> tileUrls = getTileUrls(pos);
      // 记录最后一个失败原因，全部候选都失败时用它作为最终异常抛出
      IOException lastError = null;
      // 最后一个候选的下标，用于判断"还有没有下一个可以试"
      int lastIndex = tileUrls.size() - 1;
      for (int index = 0; index <= lastIndex; index++) {
         // 本次要尝试的瓦片地址
         String tileUrl = tileUrls.get(index);
         try {
            // 成功就直接返回，不再尝试后面的候选
            return this.downloadTile(pos, tileUrl, cachePath, generation, task);
         } catch (IOException error) {
            // 喵~防御：任务被取消时必须立刻向上抛，不能继续试下一个候选，否则会白白多发一次请求
            if (this.isCancelledLoad(error, task)) {
               throw error;
            }
            lastError = error;
            // 只有"后面还有候选"时才打降级警告，避免重复日志
            if (index < lastIndex) {
               Tellus.LOGGER.warn("[TellusCN] Map tile {} failed on {}, falling back to the next candidate endpoint", pos, tileUrl, error);
            }
         }
      }

      // 喵~防御：理论上循环至少会设置一次 lastError，这里仍做兜底判断，保证不会抛出空指针
      throw lastError != null ? lastError : new IOException("Map tile request failed for " + pos);
   }

   /**
    * 用指定地址下载一张地图瓦片，校验通过后写入缓存。
    *
    * 输入：瓦片坐标、完整瓦片地址、缓存文件路径、缓存代次与加载任务。
    * 输出：校验通过的 PNG 字节数组。
    * 边界条件：非 200 响应、响应体超限、PNG 校验失败或任务被取消时抛 IOException，
    * 由调用方决定是降级到下一个候选地址还是直接失败。
    */
   private byte[] downloadTile(SlippyMapTilePos pos, String tileUrl, Path cachePath, long generation, SlippyMapTileCache.TileLoadTask task) throws IOException {
      Tellus.LOGGER.info("[TellusCN] Downloading map tile: {} from URL: {}", pos, tileUrl);
      URI uri = URI.create(tileUrl);
      URL url = uri.toURL();
      HttpURLConnection connection = (HttpURLConnection)url.openConnection();
      task.attachConnection(connection);
      try {
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(7000);
         connection.setUseCaches(true);
         connection.setRequestProperty("Accept", "image/png");
         connection.setRequestProperty("User-Agent", USER_AGENT);
         int responseCode = connection.getResponseCode();
         if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new SlippyMapTileCache.TileHttpException(responseCode, "OpenStreetMap tile request failed with HTTP " + responseCode + " for " + pos);
         }
         long contentLength = connection.getContentLengthLong();
         if (contentLength > MAX_TILE_BYTES) {
            throw new IOException("OpenStreetMap tile response exceeds the safety limit for " + pos);
         }

         InputStream stream = Objects.requireNonNull(connection.getInputStream(), "tileStream");
         task.attachStream(stream);
         try (InputStream input = new BufferedInputStream(stream)) {
            byte[] data = MapTileImageValidator.readBounded(input, MAX_TILE_BYTES);
            MapTileImageValidator.validatePng(data, MAX_TILE_DIMENSION, MAX_TILE_DIMENSION);
            this.throwIfCancelled(task);
            if (!this.shuttingDown && TellusCacheRegistry.isCurrent(TellusCacheDomain.OSM, generation)) {
               this.cacheData(cachePath, data, generation);
            }
            return data;
         } finally {
            task.detachStream(stream);
         }
      } finally {
         task.detachConnection(connection);
         connection.disconnect();
      }
   }

   private void throwIfCancelled(SlippyMapTileCache.TileLoadTask task) throws InterruptedIOException {
      if (task.cancelled || this.shuttingDown || Thread.currentThread().isInterrupted()) {
         throw new InterruptedIOException("Map tile request was cancelled");
      }
   }

   private boolean isCancelledLoad(IOException error, SlippyMapTileCache.TileLoadTask task) {
      return task.cancelled
         || this.shuttingDown
         || Thread.currentThread().isInterrupted()
         || error instanceof InterruptedIOException
         || error instanceof ClosedByInterruptException;
   }

   private void cacheData(Path cachePath, byte[] data, long generation) {
      try {
         TellusCacheFiles.writeBytesIfCurrent(TellusCacheDomain.OSM, generation, cachePath, data);
      } catch (IOException error) {
         Tellus.LOGGER.debug("Failed to cache map tile {}", cachePath, error);
      }
   }

   private static long retryDelayNanos(Throwable error, int failures) {
      if (error instanceof SlippyMapTileCache.TileHttpException httpError && (httpError.status == 429 || httpError.status >= 500)) {
         return RETRY_MAX_NANOS;
      }
      int shift = Math.min(Math.max(0, failures - 1), 5);
      return Math.min(RETRY_MAX_NANOS, RETRY_BASE_NANOS << shift);
   }

   private static Map<SlippyMapTilePos, Integer> buildViewportPriorities(List<SlippyMapTilePos> visibleTiles) {
      if (visibleTiles == null || visibleTiles.isEmpty()) {
         return Map.of();
      }

      int viewZoom = visibleTiles.get(0).getZoom();
      double minX = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      double minY = Double.POSITIVE_INFINITY;
      double maxY = Double.NEGATIVE_INFINITY;
      for (SlippyMapTilePos pos : visibleTiles) {
         if (isValid(pos) && pos.getZoom() == viewZoom) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
         }
      }
      if (!Double.isFinite(minX)) {
         return Map.of();
      }

      double centerX = (minX + maxX + 1.0) * 0.5;
      double centerY = (minY + maxY + 1.0) * 0.5;
      Map<SlippyMapTilePos, Integer> priorities = new HashMap<>();
      for (SlippyMapTilePos visible : visibleTiles) {
         if (!isValid(visible) || visible.getZoom() != viewZoom) {
            continue;
         }

         SlippyMapTilePos candidate = visible;
         int depth = 0;
         while (candidate.getZoom() >= SlippyMap.MIN_ZOOM) {
            int scale = 1 << depth;
            double projectedX = (candidate.getX() + 0.5) * scale;
            double projectedY = (candidate.getY() + 0.5) * scale;
            int spatialPriority = (int)Math.min(10_000.0, Math.round((Math.abs(projectedX - centerX) + Math.abs(projectedY - centerY)) * 100.0));
            int layerPriority = depth == 0 ? 0 : depth == 1 ? 150 : 400 + depth * 50;
            int priority = layerPriority + spatialPriority;
            priorities.merge(candidate, priority, Math::min);
            if (candidate.getZoom() == SlippyMap.MIN_ZOOM) {
               break;
            }
            candidate = new SlippyMapTilePos(candidate.getX() >> 1, candidate.getY() >> 1, candidate.getZoom() - 1);
            depth++;
         }
      }
      return priorities;
   }

   private static boolean isValid(SlippyMapTilePos pos) {
      int zoom = pos.getZoom();
      if (zoom < SlippyMap.MIN_ZOOM || zoom > SlippyMap.MAX_ZOOM) {
         return false;
      }
      int size = 1 << zoom;
      return pos.getX() >= 0 && pos.getY() >= 0 && pos.getX() < size && pos.getY() < size;
   }

   private static final class TileEntry {
      private final SlippyMapTilePos pos;
      private final SlippyMapTile tile;
      private SlippyMapTileCache.TileLoadTask task;
      private boolean loaded;
      private int failures;
      private long retryAtNanos;

      private TileEntry(SlippyMapTilePos pos) {
         this.pos = pos;
         this.tile = new SlippyMapTile(pos);
      }
   }

   private final class TileLoadTask implements Runnable, Comparable<SlippyMapTileCache.TileLoadTask> {
      private final SlippyMapTileCache.TileEntry entry;
      private final long generation;
      private final long revision;
      private final int priority;
      private final long sequence;
      private volatile boolean cancelled;
      private volatile boolean started;
      private volatile Thread runner;
      private volatile HttpURLConnection connection;
      private volatile InputStream stream;

      private TileLoadTask(SlippyMapTileCache.TileEntry entry, long generation, long revision, int priority, long sequence) {
         this.entry = entry;
         this.generation = generation;
         this.revision = revision;
         this.priority = priority;
         this.sequence = sequence;
      }

      @Override
      public void run() {
         if (this.cancelled) {
            return;
         }
         this.started = true;
         this.runner = Thread.currentThread();
         try {
            if (!this.cancelled) {
               SlippyMapTileCache.this.loadTile(this);
            }
         } finally {
            this.closeNetwork();
            this.runner = null;
         }
      }

      private void cancel() {
         this.cancelled = true;
         SlippyMapTileCache.this.loadingService.remove(this);
         this.closeNetwork();
         Thread thread = this.runner;
         if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
         }
      }

      private void attachConnection(HttpURLConnection connection) throws InterruptedIOException {
         this.connection = connection;
         if (this.cancelled) {
            connection.disconnect();
            throw new InterruptedIOException("Map tile request was cancelled");
         }
      }

      private void detachConnection(HttpURLConnection connection) {
         if (this.connection == connection) {
            this.connection = null;
         }
      }

      private void attachStream(InputStream stream) throws InterruptedIOException {
         this.stream = stream;
         if (this.cancelled) {
            closeQuietly(stream);
            throw new InterruptedIOException("Map tile request was cancelled");
         }
      }

      private void detachStream(InputStream stream) {
         if (this.stream == stream) {
            this.stream = null;
         }
      }

      private void closeNetwork() {
         InputStream activeStream = this.stream;
         if (activeStream != null) {
            closeQuietly(activeStream);
         }
         HttpURLConnection activeConnection = this.connection;
         if (activeConnection != null) {
            activeConnection.disconnect();
         }
      }

      @Override
      public int compareTo(SlippyMapTileCache.TileLoadTask other) {
         int revisionOrder = Long.compare(other.revision, this.revision);
         if (revisionOrder != 0) {
            return revisionOrder;
         }
         int priorityOrder = Integer.compare(this.priority, other.priority);
         return priorityOrder != 0 ? priorityOrder : Long.compare(this.sequence, other.sequence);
      }
   }

   private static final class TileHttpException extends IOException {
      private final int status;

      private TileHttpException(int status, String message) {
         super(message);
         this.status = status;
      }
   }

   private static void closeQuietly(InputStream stream) {
      try {
         stream.close();
      } catch (IOException ignored) {
      }
   }

   /**
    * 解析地图瓦片的所有候选地址（TellusCN 镜像支持）。
    *
    * 优先级：游戏内镜像设置 > JVM 启动参数 tellus.map.tiles.endpoint > 官方 OpenStreetMap。
    * 输入：瓦片坐标对象；输出：按优先级排列、可直接发起 HTTP 请求的 PNG 地址列表。
    * 边界条件：候选列表至少包含官方地址，因此返回值不会为空；镜像地址为空白时会被
    * TellusEndpointConfig 过滤掉，不会拼出缺少主机名的坏地址。
    */
   private List<String> getTileUrls(SlippyMapTilePos pos) {
      // 取出候选基础地址：镜像优先、官方 OpenStreetMap 兜底
      List<String> baseUrls = TellusEndpointConfig.getMapTilesCandidates(OFFICIAL_MAP_TILE_BASE_URL);
      // 按候选数量预分配容量，避免扩容
      List<String> tileUrls = new ArrayList<>(baseUrls.size());
      for (String baseUrl : baseUrls) {
         // 把基础地址拼成具体瓦片地址：<基础地址>/<缩放级别>/<X>/<Y>.png
         tileUrls.add(String.format("%s/%s/%s/%s.png", baseUrl, pos.getZoom(), pos.getX(), pos.getY()));
      }
      // 返回不可变副本，防止调用方意外修改
      return List.copyOf(tileUrls);
   }
}
