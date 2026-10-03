package com.yucareux.tellus.world.data.mask;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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

final class PmTilesReader {
   private static final int HEADER_SIZE = 127;
   private static final int MAX_DIRECTORY_DEPTH = 4;
   private static final int MAX_DIRECTORY_CACHE = intProperty("tellus.landmask.dirCache", 256);
   private static final long MAX_DIRECTORY_CACHE_BYTES = 128L * 1024L * 1024L;
   private static final int READ_TIMEOUT_MS = 15000;
   private static final int CONNECT_TIMEOUT_MS = 10000;
   private final String url;
   private final LoadingCache<PmTilesReader.DirectoryKey, PmTilesReader.Directory> directoryCache;
   
   private PmTilesReader.PmTilesHeader header;
   
   private PmTilesReader.Directory rootDirectory;
   private URI uri;

   PmTilesReader(String url) {
      this.url = Objects.requireNonNull(url, "url");
      requireHttpUri(URI.create(this.url));
      this.directoryCache = CacheBuilder.newBuilder()
         .maximumWeight(directoryCacheBudget(MAX_DIRECTORY_CACHE))
         .weigher((PmTilesReader.DirectoryKey key, PmTilesReader.Directory directory) -> directoryWeight(directory.entries.size()))
         .build(new CacheLoader<PmTilesReader.DirectoryKey, PmTilesReader.Directory>() {
            public PmTilesReader.Directory load(PmTilesReader.DirectoryKey key) throws Exception {
               return PmTilesReader.this.readDirectory(key.offset, key.length);
            }
         });
   }

   synchronized PmTilesReader.PmTilesHeader header() throws IOException {
      if (this.header == null) {
         this.header = this.readHeader();
      }

      return this.header;
   }

   
   byte[] getTileBytes(int z, int x, int y) throws IOException {
      long tileId = zxyToTileId(z, x, y);
      PmTilesReader.PmTilesHeader header = this.header();
      PmTilesReader.Directory directory = this.getRootDirectory();

      for (int depth = 0; depth < MAX_DIRECTORY_DEPTH; depth++) {
         PmTilesReader.Entry entry = findTile(directory.entries, tileId);
         if (entry == null) {
            return null;
         }

         if (entry.runLength != 0L) {
            long dataOffset = PmTilesSafety.checkedAdd(header.tileDataOffset, entry.offset, "PMTiles tile data");
            int tileLength = PmTilesSafety.checkedLength(
               entry.length, PmTilesSafety.MAX_COMPRESSED_TILE_BYTES, "PMTiles tile"
            );
            return this.readBytes(dataOffset, tileLength);
         }

         long dirOffset = PmTilesSafety.checkedAdd(header.leafDirectoryOffset, entry.offset, "PMTiles leaf directory");
         long dirLength = entry.length;
         directory = this.getDirectory(dirOffset, dirLength);
      }

      return null;
   }

   private synchronized PmTilesReader.Directory getRootDirectory() throws IOException {
      if (this.rootDirectory == null) {
         PmTilesReader.PmTilesHeader header = this.header();
         this.rootDirectory = this.getDirectory(header.rootOffset, header.rootLength);
      }

      return this.rootDirectory;
   }

   private PmTilesReader.Directory getDirectory(long offset, long length) throws IOException {
      try {
         return (PmTilesReader.Directory)this.directoryCache.get(new PmTilesReader.DirectoryKey(offset, length));
      } catch (Exception var8) {
         if (var8.getCause() instanceof IOException io) {
            throw io;
         } else {
            throw new IOException("Failed to read PMTiles directory", var8);
         }
      }
   }

   private PmTilesReader.PmTilesHeader readHeader() throws IOException {
      byte[] headerBytes = this.readBytes(0L, HEADER_SIZE);
      if (!"PMTiles".equals(new String(headerBytes, 0, 7, StandardCharsets.US_ASCII))) {
         throw new IOException("PMTiles header missing");
      } else {
         int version = headerBytes[7] & 255;
         if (version != 3) {
            throw new IOException("Unsupported PMTiles version " + version);
         } else {
            long rootOffset = readUint64(headerBytes, 8);
            long rootLength = readUint64(headerBytes, 16);
            long metadataOffset = readUint64(headerBytes, 24);
            long metadataLength = readUint64(headerBytes, 32);
            long leafOffset = readUint64(headerBytes, 40);
            long leafLength = readUint64(headerBytes, 48);
            long tileOffset = readUint64(headerBytes, 56);
            long tileLength = readUint64(headerBytes, 64);
            int internalCompression = headerBytes[97] & 255;
            int tileCompression = headerBytes[98] & 255;
            int tileType = headerBytes[99] & 255;
            int minZoom = headerBytes[100] & 255;
            int maxZoom = headerBytes[101] & 255;
            validateHeader(
               rootOffset,
               rootLength,
               metadataOffset,
               metadataLength,
               leafOffset,
               leafLength,
               tileOffset,
               tileLength,
               internalCompression,
               tileCompression,
               tileType,
               minZoom,
               maxZoom
            );

            return new PmTilesReader.PmTilesHeader(
               rootOffset, rootLength, metadataOffset, metadataLength, leafOffset, leafLength, tileOffset, tileLength, minZoom, maxZoom
            );
         }
      }
   }

   private PmTilesReader.Directory readDirectory(long offset, long length) throws IOException {
      if (length <= 0L) {
         return new PmTilesReader.Directory(List.of());
      } else {
         int compressedLength = PmTilesSafety.checkedLength(
            length, PmTilesSafety.MAX_COMPRESSED_DIRECTORY_BYTES, "PMTiles directory"
         );
         byte[] compressed = this.readBytes(offset, compressedLength);
         byte[] decompressed = PmTilesSafety.decompress(
            compressed,
            PmTilesSafety.COMPRESSION_GZIP,
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
         List<PmTilesReader.Entry> entries = new ArrayList<>(numEntries);
         long lastId = 0L;

         for (int i = 0; i < numEntries; i++) {
            long delta = PmTilesSafety.readVarint(input);
            long tileId = PmTilesSafety.checkedAdd(lastId, delta, "PMTiles tile id");
            entries.add(new PmTilesReader.Entry(tileId, 0L, 0L, 0L));
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
               PmTilesReader.Entry prev = entries.get(i - 1);
               entries.get(i).offset = PmTilesSafety.checkedAdd(prev.offset, prev.length, "PMTiles entry");
            } else {
               if (tmp == 0L) {
                  throw new IOException("PMTiles first directory offset must be positive");
               }
               entries.get(i).offset = tmp - 1L;
            }
         }

         return new PmTilesReader.Directory(entries);
      }
   }

   private byte[] readBytes(long offset, int length) throws IOException {
      if (length <= 0) {
         return new byte[0];
      } else {
         long endInclusive = PmTilesSafety.checkedAdd(offset, length - 1L, "PMTiles HTTP range");
         if (ManagedTerrainNetworkPolicy.isCacheOnly()) {
            throw new IOException("Network access is disabled during managed Distant Horizons generation");
         }
         HttpURLConnection connection = (HttpURLConnection)this.uri().toURL().openConnection();
         connection.setRequestProperty("Range", "bytes=" + offset + "-" + endInclusive);
         connection.setInstanceFollowRedirects(true);
         connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
         connection.setReadTimeout(READ_TIMEOUT_MS);
         int code = connection.getResponseCode();
         if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect();
            throw new IOException("PMTiles HTTP error " + code);
         }

         byte[] var7;
         DownloadProgressReporter.requestStarted(length);
         try (InputStream input = connection.getInputStream()) {
            if (code == HttpURLConnection.HTTP_OK) {
               if (offset != 0L) {
                  throw new IOException("PMTiles server ignored a nonzero HTTP range request");
               }
               return readFullyWithProgress(input, length);
            }

            var7 = readFullyWithProgress(input, length);
         } finally {
            DownloadProgressReporter.requestFinished();
            connection.disconnect();
         }

         return var7;
      }
   }

   private URI uri() {
      if (this.uri == null) {
         this.uri = URI.create(this.url);
      }

      return this.uri;
   }

   private static byte[] readFullyWithProgress(InputStream input, int length) throws IOException {
      byte[] buffer = new byte[length];
      int offset = 0;

      while (offset < length) {
         int read = input.read(buffer, offset, length - offset);
         if (read == -1) {
            throw new EOFException("Unexpected EOF while reading");
         }

         offset += read;
         DownloadProgressReporter.bytesRead(read);
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
      long metadataOffset,
      long metadataLength,
      long leafOffset,
      long leafLength,
      long tileOffset,
      long tileLength,
      int internalCompression,
      int tileCompression,
      int tileType,
      int minZoom,
      int maxZoom
   ) throws IOException {
      PmTilesSafety.checkedAdd(rootOffset, rootLength, "PMTiles root directory");
      PmTilesSafety.checkedLength(rootLength, PmTilesSafety.MAX_COMPRESSED_DIRECTORY_BYTES, "PMTiles root directory");
      PmTilesSafety.checkedAdd(metadataOffset, metadataLength, "PMTiles metadata");
      PmTilesSafety.checkedAdd(leafOffset, leafLength, "PMTiles leaf directories");
      PmTilesSafety.checkedAdd(tileOffset, tileLength, "PMTiles tile data");
      if (internalCompression != PmTilesSafety.COMPRESSION_GZIP
         || tileCompression != PmTilesSafety.COMPRESSION_NONE
         || tileType != 2) {
         throw new IOException("Land-mask PMTiles header uses unsupported compression or tile type");
      }
      if (minZoom < 0 || maxZoom > 31 || minZoom > maxZoom) {
         throw new IOException("PMTiles header contains invalid zoom bounds");
      }
   }

   private static PmTilesReader.Entry findTile(List<PmTilesReader.Entry> entries, long tileId) {
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
         PmTilesReader.Entry entry = entries.get(high);
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

   private static int intProperty(String key, int defaultValue) {
      String value = System.getProperty(key);
      if (value == null) {
         return defaultValue;
      } else {
         try {
            return Math.max(1, Integer.parseInt(value));
         } catch (NumberFormatException var4) {
            return defaultValue;
         }
      }
   }

   private static final class Directory {
      private final List<PmTilesReader.Entry> entries;

      private Directory(List<PmTilesReader.Entry> entries) {
         this.entries = entries;
      }
   }

   private record DirectoryKey(long offset, long length) {
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

   static final class PmTilesHeader {
      private final long rootOffset;
      private final long rootLength;
      private final long metadataOffset;
      private final long metadataLength;
      private final long leafDirectoryOffset;
      private final long leafDirectoryLength;
      private final long tileDataOffset;
      private final long tileDataLength;
      private final int minZoom;
      private final int maxZoom;

      private PmTilesHeader(
         long rootOffset,
         long rootLength,
         long metadataOffset,
         long metadataLength,
         long leafDirectoryOffset,
         long leafDirectoryLength,
         long tileDataOffset,
         long tileDataLength,
         int minZoom,
         int maxZoom
      ) {
         this.rootOffset = rootOffset;
         this.rootLength = rootLength;
         this.metadataOffset = metadataOffset;
         this.metadataLength = metadataLength;
         this.leafDirectoryOffset = leafDirectoryOffset;
         this.leafDirectoryLength = leafDirectoryLength;
         this.tileDataOffset = tileDataOffset;
         this.tileDataLength = tileDataLength;
         this.minZoom = minZoom;
         this.maxZoom = maxZoom;
      }

      public long rootOffset() {
         return this.rootOffset;
      }

      public long rootLength() {
         return this.rootLength;
      }

      public long metadataOffset() {
         return this.metadataOffset;
      }

      public long metadataLength() {
         return this.metadataLength;
      }

      public long leafDirectoryOffset() {
         return this.leafDirectoryOffset;
      }

      public long leafDirectoryLength() {
         return this.leafDirectoryLength;
      }

      public long tileDataOffset() {
         return this.tileDataOffset;
      }

      public long tileDataLength() {
         return this.tileDataLength;
      }

      public int minZoom() {
         return this.minZoom;
      }

      public int maxZoom() {
         return this.maxZoom;
      }
   }
}
