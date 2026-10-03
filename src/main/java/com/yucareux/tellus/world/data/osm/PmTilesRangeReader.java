package com.yucareux.tellus.world.data.osm;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainNetworkPolicy;
import com.yucareux.tellus.world.data.pmtiles.PmTilesSafety;
import com.yucareux.tellus.world.data.source.DownloadProgressReporter;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class PmTilesRangeReader {
   // 独立日志器：与 Tellus.LOGGER 同一个日志分类，但不会连带触发 Tellus 类的静态初始化
   // （Tellus 的静态初始化会访问 Minecraft 注册表，在未引导游戏的单元测试环境里会直接抛错）
   private static final Logger LOGGER = LoggerFactory.getLogger("tellus");
   private static final int HEADER_SIZE = 127;
   private static final int MAX_DIRECTORY_DEPTH = 6;
   private static final int PMTILES_VERSION = 3;
   // 目录缓存与瓦片缓存各自的内存上限，单位：字节；超出后按权重淘汰，避免长时间运行吃满堆
   private static final long MAX_DIRECTORY_CACHE_BYTES = 128L * 1024L * 1024L;
   private static final long MAX_TILE_PAYLOAD_CACHE_BYTES = 128L * 1024L * 1024L;
   // 共享读取器缓存：同一组候选地址只保留一个实例，避免建筑与道路重复建立目录缓存
   private static final ConcurrentHashMap<ReaderKey, PmTilesRangeReader> SHARED_READERS = new ConcurrentHashMap<>();
   // 按列表顺序尝试的候选数据源地址，第一个能读到合法文件头的会被固定下来
   private final List<URI> candidateUris;
   // 当前生效的数据源地址，读取期间保持不变，避免不同版本的文件偏移被混用
   private volatile URI activeUri;
   private final int connectTimeoutMs;
   private final int readTimeoutMs;
   private final LoadingCache<PmTilesRangeReader.DirectoryKey, PmTilesRangeReader.Directory> directoryCache;
   private final LoadingCache<Long, TilePayload> tilePayloadCache;
   
   private PmTilesRangeReader.PmTilesHeader header;
   
   private PmTilesRangeReader.Directory rootDirectory;

   // 单地址构造：保持与水域、沙地等既有调用方兼容
   public PmTilesRangeReader(String url, int connectTimeoutMs, int readTimeoutMs, int directoryCacheEntries) {
      // 把单个地址包装成只有一个候选的列表，复用下面的多地址逻辑
      this(List.of(Objects.requireNonNull(url, "url")), connectTimeoutMs, readTimeoutMs, directoryCacheEntries);
   }

   // 多地址构造：按列表顺序尝试，通常第一个是国内镜像，第二个是官方源兜底
   public PmTilesRangeReader(List<String> urls, int connectTimeoutMs, int readTimeoutMs, int directoryCacheEntries) {
      // 喵~防御：候选列表整体为 null 时立刻抛错，避免后面取 size 时出现难定位的空指针
      Objects.requireNonNull(urls, "urls");
      // 预先按候选数量分配容量，避免解析过程中扩容
      List<URI> parsedUris = new ArrayList<>(urls.size());
      // 逐个把字符串地址解析并校验成合法的 http/https 地址
      for (String url : urls) {
         // 喵~防御：地址为 null 时立刻抛错，避免后面出现难以定位的空指针异常
         // 喵~防御：requireHttpUri 会拒绝 file:// 等非 http(s) 协议，防止候选地址被伪协议误导
         parsedUris.add(requireHttpUri(URI.create(Objects.requireNonNull(url, "url"))));
      }
      // 喵~防御：候选列表为空时抛错，避免造出一个永远读不到数据、却又不报错的读取器
      if (parsedUris.isEmpty()) {
         throw new IllegalArgumentException("PMTiles requires at least one source URL");
      }
      // 保存不可变副本，保证读取过程中候选列表不会被外部改动
      this.candidateUris = List.copyOf(parsedUris);
      // 先把生效地址指向第一个候选，保证尚未解析文件头时也有可用地址
      this.activeUri = this.candidateUris.get(0);
      // 连接超时至少 1 毫秒，避免 0 或负数导致无限等待
      this.connectTimeoutMs = Math.max(1, connectTimeoutMs);
      // 读取超时同样至少 1 毫秒，避免 0 或负数导致无限等待
      this.readTimeoutMs = Math.max(1, readTimeoutMs);
      this.directoryCache = CacheBuilder.newBuilder()
         .maximumWeight(directoryCacheBudget(directoryCacheEntries))
         .weigher((PmTilesRangeReader.DirectoryKey key, PmTilesRangeReader.Directory directory) -> directoryWeight(directory.entries.size()))
         .build(new CacheLoader<PmTilesRangeReader.DirectoryKey, PmTilesRangeReader.Directory>() {
            public PmTilesRangeReader.Directory load(PmTilesRangeReader.DirectoryKey key) throws Exception {
               return PmTilesRangeReader.this.readDirectory(key.offset, key.length);
            }
         });
      this.tilePayloadCache = CacheBuilder.newBuilder()
         .maximumWeight(MAX_TILE_PAYLOAD_CACHE_BYTES)
         .weigher((Long tileId, TilePayload payload) -> Math.max(64, payload.bytes().length))
         .build(new CacheLoader<Long, TilePayload>() {
            @Override
            public TilePayload load(Long tileId) throws Exception {
               return PmTilesRangeReader.this.loadTilePayload(tileId);
            }
         });
   }

   /**
    * 共享读取器工厂（单地址重载）：只有一个数据源时使用，内部委托给候选列表版本。
    *
    * 输入：单个数据源地址、连接超时、读取超时、目录缓存条目数。
    * 输出：可复用的 PmTilesRangeReader 实例。
    * 边界条件：地址为 null 时由 List.of 与下层构造函数共同拦截并抛异常。
    */
   public static PmTilesRangeReader shared(String url, int connectTimeoutMs, int readTimeoutMs, int directoryCacheEntries) {
      // 喵~防御：地址为 null 时立刻抛错，避免把 null 包装进列表后在下层才失败
      return shared(List.of(Objects.requireNonNull(url, "url")), connectTimeoutMs, readTimeoutMs, directoryCacheEntries);
   }

   /**
    * 共享读取器工厂：把"同一组候选地址 + 同一组超时/缓存参数"的读取器合并成一个实例。
    *
    * 整体思路：建筑层与道路层经常读取同一个 Overture PMTiles 压缩包，
    * 如果各自 new 一个读取器，文件头与目录缓存会被重复下载与重复占用内存。
    * 这里用 ConcurrentHashMap 做进程内去重，key 为规范化后的候选地址列表与三个参数。
    *
    * 输入：候选地址列表（通常"镜像在前、官方在后"）、连接超时、读取超时、目录缓存条目数。
    * 输出：可复用的 PmTilesRangeReader 实例。
    * 边界条件：地址列表为 null 或空、列表里某个地址为 null 时直接抛异常，
    * 避免返回一个永远读不到数据却又不报错的读取器。
    */
   public static PmTilesRangeReader shared(List<String> urls, int connectTimeoutMs, int readTimeoutMs, int directoryCacheEntries) {
      // 喵~防御：候选地址列表为 null 时立刻抛错，避免后面遍历时出现难定位的空指针
      Objects.requireNonNull(urls, "urls");
      // 规范化每个候选地址，去掉 "./"、"../" 这类多余路径段，保证同一地址只对应一个缓存键
      List<URI> normalizedUris = new ArrayList<>(urls.size());
      // 逐个规范化候选地址
      for (String url : urls) {
         // 喵~防御：单个地址为 null 时立刻抛错，避免拼出非法 URI
         // 喵~防御：requireHttpUri 会拒绝 file:// 等非 http(s) 协议，避免共享读取器指向本地文件
         normalizedUris.add(requireHttpUri(URI.create(Objects.requireNonNull(url, "url")).normalize()));
      }
      // 喵~防御：候选列表为空时抛错，避免造出无法工作的共享读取器
      if (normalizedUris.isEmpty()) {
         throw new IllegalArgumentException("PMTiles requires at least one source URL");
      }
      // 用"规范化候选地址列表 + 三个超时/缓存参数"作为缓存键，同键只保留一个实例
      ReaderKey key = new ReaderKey(
         List.copyOf(normalizedUris),
         Math.max(1, connectTimeoutMs),
         Math.max(1, readTimeoutMs),
         Math.max(1, directoryCacheEntries)
      );
      // 已存在就直接复用，否则按同样的候选列表新建一个读取器
      return SHARED_READERS.computeIfAbsent(
         key,
         ignored -> new PmTilesRangeReader(
            key.uris().stream().map(URI::toString).toList(),
            key.connectTimeoutMs(),
            key.readTimeoutMs(),
            key.directoryCacheEntries()
         )
      );
   }

   public synchronized PmTilesRangeReader.PmTilesHeader header() throws IOException {
      if (this.header == null) {
         this.header = this.readHeader();
      }

      return this.header;
   }

   
   public byte[] getTileBytes(int z, int x, int y) throws IOException {
      long tileId = zxyToTileId(z, x, y);
      try {
         TilePayload payload = this.tilePayloadCache.get(tileId);
         return payload.found() ? payload.bytes() : null;
      } catch (ExecutionException error) {
         if (error.getCause() instanceof IOException io) {
            throw io;
         }
         throw new IOException("Failed to read PMTiles tile " + z + "/" + x + "/" + y, error.getCause());
      }
   }

   private TilePayload loadTilePayload(long tileId) throws IOException {
      PmTilesRangeReader.PmTilesHeader header = this.header();
      PmTilesRangeReader.Directory directory = this.getRootDirectory();

      for (int depth = 0; depth < MAX_DIRECTORY_DEPTH; depth++) {
         PmTilesRangeReader.Entry entry = findTile(directory.entries, tileId);
         if (entry == null) {
            return TilePayload.missing();
         }

         if (entry.runLength != 0L) {
            long dataOffset = PmTilesSafety.checkedAdd(header.tileDataOffset, entry.offset, "PMTiles tile data");
            int tileLength = PmTilesSafety.checkedLength(
               entry.length, PmTilesSafety.MAX_COMPRESSED_TILE_BYTES, "PMTiles tile"
            );
            byte[] tileBytes = this.readBytes(dataOffset, tileLength);
            return TilePayload.found(
               PmTilesSafety.decompress(
                  tileBytes,
                  header.tileCompression,
                  PmTilesSafety.MAX_DECOMPRESSED_TILE_BYTES,
                  "PMTiles tile"
               )
            );
         }

         long dirOffset = PmTilesSafety.checkedAdd(header.leafDirectoryOffset, entry.offset, "PMTiles leaf directory");
         long dirLength = entry.length;
         directory = this.getDirectory(dirOffset, dirLength);
      }

      return TilePayload.missing();
   }

   private synchronized PmTilesRangeReader.Directory getRootDirectory() throws IOException {
      if (this.rootDirectory == null) {
         PmTilesRangeReader.PmTilesHeader header = this.header();
         this.rootDirectory = this.getDirectory(header.rootOffset, header.rootLength);
      }

      return this.rootDirectory;
   }

   private PmTilesRangeReader.Directory getDirectory(long offset, long length) throws IOException {
      try {
         return (PmTilesRangeReader.Directory)this.directoryCache.get(new PmTilesRangeReader.DirectoryKey(offset, length));
      } catch (Exception var8) {
         if (var8.getCause() instanceof IOException io) {
            throw io;
         } else {
            throw new IOException("Failed to read PMTiles directory", var8);
         }
      }
   }

   private PmTilesRangeReader.PmTilesHeader readHeader() throws IOException {
      // 记录最后一次失败原因，全部候选都失败时抛给调用方，便于定位问题
      IOException lastError = null;
      // 按顺序尝试每个候选地址，通常是"镜像在前、官方在后"
      for (URI candidateUri : this.candidateUris) {
         try {
            // 从候选地址读取前 127 字节的 PMTiles 文件头
            byte[] headerBytes = this.readBytesFromUri(candidateUri, 0L, HEADER_SIZE);
            // 校验魔术字与版本号，并解析出各段数据的偏移量
            PmTilesRangeReader.PmTilesHeader parsedHeader = parseHeader(headerBytes);
            // 喵~防御：只有拿到合法文件头之后才固定生效地址。不同发布版本的文件内部偏移不同，
            // 若在读取过程中途切换数据源，会导致后续瓦片数据读出错乱的字节
            this.activeUri = candidateUri;
            // 立即返回解析成功的文件头，停止继续尝试
            return parsedHeader;
         } catch (IOException error) {
            // 记录本次失败原因，供全部失败时抛出
            lastError = error;
            // 打日志说明这个候选不可用，方便主人从日志区分是镜像坏了还是官方源也坏了
            LOGGER.warn("PMTiles source unavailable, trying next candidate: {}", candidateUri, error);
         }
      }
      // 喵~防御：所有候选都失败时抛出最后一次的错误，绝不让调用方拿到一个空文件头
      throw lastError != null ? lastError : new IOException("No PMTiles source available");
   }

   private static PmTilesRangeReader.PmTilesHeader parseHeader(byte[] headerBytes) throws IOException {
      // 校验文件前 7 个字节是否为 PMTiles 魔术字，否则说明拿到的不是 PMTiles 文件
      if (!"PMTiles".equals(new String(headerBytes, 0, 7, StandardCharsets.US_ASCII))) {
         throw new IOException("PMTiles header missing");
      } else {
         // 读取第 8 个字节作为 PMTiles 规范版本号
         int version = headerBytes[7] & 255;
         // 喵~防御：版本号不是已知的 3 时拒绝解析，避免按错误布局读出垃圾数据
         if (version != PMTILES_VERSION) {
            throw new IOException("Unsupported PMTiles version " + version);
         } else {
            // 从偏移 8 开始读取根目录起始位置
            long rootOffset = readUint64(headerBytes, 8);
            // 从偏移 16 开始读取根目录长度
            long rootLength = readUint64(headerBytes, 16);
            // 从偏移 40 开始读取叶子目录起始位置
            long leafOffset = readUint64(headerBytes, 40);
            // 从偏移 56 开始读取瓦片数据起始位置
            long tileOffset = readUint64(headerBytes, 56);
            // 偏移 97 表示目录内部使用的压缩算法
            int internalCompression = headerBytes[97] & 255;
            // 偏移 98 表示瓦片数据使用的压缩算法
            int tileCompression = headerBytes[98] & 255;
            // 偏移 99 表示瓦片类型（1 为 MVT 矢量瓦片）
            int tileType = headerBytes[99] & 255;
            // 偏移 100 表示该文件支持的最小缩放级别
            int minZoom = headerBytes[100] & 255;
            // 偏移 101 表示该文件支持的最大缩放级别
            int maxZoom = headerBytes[101] & 255;
            // 喵~防御：先校验各段偏移/长度是否落在合理范围，防止坏文件把后续读取引到越界位置
            validateHeader(rootOffset, rootLength, leafOffset, tileOffset, internalCompression, tileCompression, minZoom, maxZoom);
            // 把解析出的各段信息组装成不可变的文件头对象
            return new PmTilesRangeReader.PmTilesHeader(
               rootOffset,
               rootLength,
               leafOffset,
               tileOffset,
               internalCompression,
               tileCompression,
               tileType,
               minZoom,
               maxZoom
            );
         }
      }
   }

   private PmTilesRangeReader.Directory readDirectory(long offset, long length) throws IOException {
      if (length <= 0L) {
         return new PmTilesRangeReader.Directory(List.of());
      } else {
         PmTilesRangeReader.PmTilesHeader header = this.header();
         int compressedLength = PmTilesSafety.checkedLength(
            length, PmTilesSafety.MAX_COMPRESSED_DIRECTORY_BYTES, "PMTiles directory"
         );
         byte[] compressed = this.readBytes(offset, compressedLength);
         byte[] decompressed = PmTilesSafety.decompress(
            compressed,
            header.internalCompression,
            PmTilesSafety.MAX_DECOMPRESSED_DIRECTORY_BYTES,
            "PMTiles directory"
         );
         ByteArrayInputStream input = new ByteArrayInputStream(decompressed);
         long entryCount = PmTilesSafety.readVarint(input);
         long encodedEntryLimit = decompressed.length / 4L;
         if (entryCount > PmTilesSafety.MAX_DIRECTORY_ENTRIES || entryCount > encodedEntryLimit) {
            throw new IOException("PMTiles directory declares an unsafe entry count: " + entryCount);
         }
         int numEntries = (int)entryCount;
         List<PmTilesRangeReader.Entry> entries = new ArrayList<>(numEntries);
         long lastId = 0L;

         for (int i = 0; i < numEntries; i++) {
            long delta = PmTilesSafety.readVarint(input);
            long tileId = PmTilesSafety.checkedAdd(lastId, delta, "PMTiles tile id");
            entries.add(new PmTilesRangeReader.Entry(tileId, 0L, 0L, 0L));
            lastId = tileId;
         }

         for (int i = 0; i < numEntries; i++) {
            entries.get(i).runLength = PmTilesSafety.readVarint(input);
         }

         for (int i = 0; i < numEntries; i++) {
            entries.get(i).length = PmTilesSafety.readVarint(input);
         }

         for (int i = 0; i < numEntries; i++) {
            long tmp = PmTilesSafety.readVarint(input);
            if (i > 0 && tmp == 0L) {
               PmTilesRangeReader.Entry previous = entries.get(i - 1);
               entries.get(i).offset = PmTilesSafety.checkedAdd(previous.offset, previous.length, "PMTiles entry");
            } else {
               if (tmp == 0L) {
                  throw new IOException("PMTiles first directory offset must be positive");
               }
               entries.get(i).offset = tmp - 1L;
            }
         }

         return new PmTilesRangeReader.Directory(entries);
      }
   }

   private byte[] readBytes(long offset, int length) throws IOException {
      // 一律走当前生效地址读取，保证同一文件的所有偏移量都来自同一个数据源
      return this.readBytesFromUri(this.activeUri, offset, length);
   }

   private byte[] readBytesFromUri(URI sourceUri, long offset, int length) throws IOException {
      if (length <= 0) {
         return new byte[0];
      } else {
         // 喵~防御：用带溢出保护的加法算出区间结束位置，避免 offset + length 溢出成负数发出坏请求
         long endInclusive = PmTilesSafety.checkedAdd(offset, length - 1L, "PMTiles HTTP range");
         // 喵~防御：受管远距地形生成期间禁止联网，只允许读本地缓存
         if (ManagedTerrainNetworkPolicy.isCacheOnly()) {
            throw new IOException("Network access is disabled during managed Distant Horizons generation");
         }
         // 针对当前生效的数据源地址建立 HTTP 连接
         HttpURLConnection connection = (HttpURLConnection)sourceUri.toURL().openConnection();
         connection.setRequestProperty("Range", "bytes=" + offset + "-" + endInclusive);
         connection.setInstanceFollowRedirects(true);
         connection.setConnectTimeout(this.connectTimeoutMs);
         connection.setReadTimeout(this.readTimeoutMs);
         int code = connection.getResponseCode();
         long expectedBytes = connection.getContentLengthLong();
         DownloadProgressReporter.requestStarted(expectedBytes);

         byte[] var9;
         try (InputStream input = openStream(connection, code)) {
            if (code == 200) {
               if (offset != 0L) {
                  throw new IOException("PMTiles server ignored a nonzero HTTP range request");
               }
               return readFully(input, length);
            }

            if (code != 206) {
               throw new IOException("PMTiles HTTP error " + code);
            }

            var9 = readFully(input, length);
         } finally {
            DownloadProgressReporter.requestFinished();
            connection.disconnect();
         }

         return var9;
      }
   }

   private static InputStream openStream(HttpURLConnection connection, int code) throws IOException {
      if (code >= 400) {
         InputStream error = connection.getErrorStream();

         try {
            if (error == null) {
               throw new IOException("PMTiles HTTP error " + code);
            } else {
               byte[] message = error.readNBytes(512);
               throw new IOException("PMTiles HTTP error " + code + ": " + new String(message, StandardCharsets.UTF_8).trim());
            }
         } catch (Throwable var6) {
            if (error != null) {
               try {
                  error.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }
      } else {
         InputStream stream = connection.getInputStream();
         if (stream == null) {
            throw new IOException("PMTiles HTTP error " + code);
         } else {
            return stream;
         }
      }
   }

   private static byte[] readFully(InputStream input, int length) throws IOException {
      byte[] buffer = new byte[length];
      int offset = 0;

      while (offset < length) {
         int read = input.read(buffer, offset, length - offset);
         if (read == -1) {
            throw new EOFException("Unexpected EOF while reading bytes");
         }

         if (read != 0) {
            offset += read;
            DownloadProgressReporter.bytesRead(read);
         }
      }

      return buffer;
   }

   private static long readUint64(byte[] buffer, int pos) {
      return buffer[pos] & 255L
         | (buffer[pos + 1] & 255L) << 8
         | (buffer[pos + 2] & 255L) << 16
         | (buffer[pos + 3] & 255L) << 24
         | (buffer[pos + 4] & 255L) << 32
         | (buffer[pos + 5] & 255L) << 40
         | (buffer[pos + 6] & 255L) << 48
         | (buffer[pos + 7] & 255L) << 56;
   }

   private static long zxyToTileId(int z, int x, int y) {
      if (z < 0 || z > 31) {
         throw new IllegalArgumentException("Tile zoom exceeds 64-bit limit");
      } else {
         int max = (1 << z) - 1;
         if (x >= 0 && y >= 0 && x <= max && y <= max) {
            long acc = ((1L << z * 2) - 1L) / 3L;

            for (int level = z - 1; level >= 0; level--) {
               int scale = 1 << level;
               int rx = scale & x;
               int ry = scale & y;
               acc += (long)(3 * rx ^ ry) << level;
               if (ry == 0) {
                  if (rx != 0) {
                     x = scale - 1 - x;
                     y = scale - 1 - y;
                  }

                  int swapped = x;
                  x = y;
                  y = swapped;
               }
            }

            return acc;
         } else {
            throw new IllegalArgumentException("Tile x/y outside zoom bounds");
         }
      }
   }

   private static void validateHeader(
      long rootOffset,
      long rootLength,
      long leafOffset,
      long tileOffset,
      int internalCompression,
      int tileCompression,
      int minZoom,
      int maxZoom
   ) throws IOException {
      PmTilesSafety.checkedAdd(rootOffset, rootLength, "PMTiles root directory");
      PmTilesSafety.checkedLength(rootLength, PmTilesSafety.MAX_COMPRESSED_DIRECTORY_BYTES, "PMTiles root directory");
      if (leafOffset < 0L || tileOffset < 0L) {
         throw new IOException("PMTiles header contains an unsupported unsigned offset");
      }
      if ((internalCompression != PmTilesSafety.COMPRESSION_NONE && internalCompression != PmTilesSafety.COMPRESSION_GZIP)
         || (tileCompression != PmTilesSafety.COMPRESSION_NONE && tileCompression != PmTilesSafety.COMPRESSION_GZIP)) {
         throw new IOException("PMTiles header uses unsupported compression");
      }
      if (minZoom < 0 || maxZoom > 31 || minZoom > maxZoom) {
         throw new IOException("PMTiles header contains invalid zoom bounds");
      }
   }

   private static PmTilesRangeReader.Entry findTile(List<PmTilesRangeReader.Entry> entries, long tileId) {
      int low = 0;
      int high = entries.size() - 1;

      while (low <= high) {
         int mid = low + high >>> 1;
         int comparison = Long.compare(tileId, entries.get(mid).tileId);
         if (comparison > 0) {
            low = mid + 1;
         } else {
            if (comparison == 0) {
               return entries.get(mid);
            }

            high = mid - 1;
         }
      }

      if (high >= 0) {
         PmTilesRangeReader.Entry entry = entries.get(high);
         if (entry.runLength == 0L) {
            return entry;
         }

         if (tileId - entry.tileId < entry.runLength) {
            return entry;
         }
      }

      return null;
   }

   private static URI requireHttpUri(URI uri) {
      String scheme = uri.getScheme();
      if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
         throw new IllegalArgumentException("PMTiles URL must use HTTP or HTTPS");
      }
      return uri;
   }

   private static long directoryCacheBudget(int configuredEntries) {
      long requested = Math.max(1L, configuredEntries) * 512L * 1024L;
      return Math.max(8L * 1024L * 1024L, Math.min(MAX_DIRECTORY_CACHE_BYTES, requested));
   }

   private static int directoryWeight(int entries) {
      return (int)Math.min(Integer.MAX_VALUE, 128L + Math.max(0L, entries) * 48L);
   }

   private static final class Directory {
      private final List<PmTilesRangeReader.Entry> entries;

      private Directory(List<PmTilesRangeReader.Entry> entries) {
         this.entries = entries;
      }
   }

   private record DirectoryKey(long offset, long length) {
   }

   // 共享读取器的缓存键：候选地址列表（已规范化）+ 连接超时 + 读取超时 + 目录缓存条目数
   private record ReaderKey(List<URI> uris, int connectTimeoutMs, int readTimeoutMs, int directoryCacheEntries) {
   }

   private record TilePayload(byte[] bytes, boolean found) {
      private static TilePayload found(byte[] bytes) {
         return new TilePayload(Objects.requireNonNull(bytes, "bytes"), true);
      }

      private static TilePayload missing() {
         return new TilePayload(new byte[0], false);
      }
   }

   private static final class Entry {
      private final long tileId;
      private long offset;
      private long length;
      private long runLength;

      private Entry(long tileId, long offset, long length, long runLength) {
         this.tileId = tileId;
         this.offset = offset;
         this.length = length;
         this.runLength = runLength;
      }
   }

   public static final class PmTilesHeader {
      private final long rootOffset;
      private final long rootLength;
      private final long leafDirectoryOffset;
      private final long tileDataOffset;
      private final int internalCompression;
      private final int tileCompression;
      private final int tileType;
      private final int minZoom;
      private final int maxZoom;

      private PmTilesHeader(
         long rootOffset,
         long rootLength,
         long leafDirectoryOffset,
         long tileDataOffset,
         int internalCompression,
         int tileCompression,
         int tileType,
         int minZoom,
         int maxZoom
      ) {
         this.rootOffset = rootOffset;
         this.rootLength = rootLength;
         this.leafDirectoryOffset = leafDirectoryOffset;
         this.tileDataOffset = tileDataOffset;
         this.internalCompression = internalCompression;
         this.tileCompression = tileCompression;
         this.tileType = tileType;
         this.minZoom = minZoom;
         this.maxZoom = maxZoom;
      }

      public int minZoom() {
         return this.minZoom;
      }

      public int maxZoom() {
         return this.maxZoom;
      }

      public int tileType() {
         return this.tileType;
      }
   }
}
