package com.yucareux.tellus.world.data.mask;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.TellusEndpointConfig;
import com.yucareux.tellus.worldgen.EarthProjection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import net.minecraft.util.Mth;

public final class TellusLandMaskSource {
   private static final double EQUATOR_CIRCUMFERENCE = 4.0075017E7;
   private static final int TILE_SIZE = 256;
   private static final double MIN_LAT = -85.05112878;
   private static final double MAX_LAT = 85.05112878;
   private static final double MIN_LON = -180.0;
   private static final double MAX_LON = 180.0;
   private static final String DEFAULT_BASE_URL = "https://github.com/Yucareux/Tellus-Land-Polygons/releases/download/v1.0.0/";
   private static final String PMTILES_NAME = "tellus_landmask.pmtiles";
   private static final int MAX_CACHE_TILES = intProperty("tellus.landmask.cacheTiles", 256);
   private static final AtomicBoolean LOGGED_UNAVAILABLE = new AtomicBoolean(false);
   private final PmTilesReader reader;
   private final LoadingCache<TellusLandMaskSource.TileKey, TellusLandMaskSource.LandMaskTile> cache;
   private final int minZoom;
   private final int maxZoom;
   private final boolean available;

   public TellusLandMaskSource() {
      String baseUrl = TellusEndpointConfig.getLandMaskBaseUrl(DEFAULT_BASE_URL);
      this.reader = new PmTilesReader(normalizeBaseUrl(baseUrl) + PMTILES_NAME);
      int resolvedMin = 0;
      int resolvedMax = 0;
      boolean ok = false;

      try {
         PmTilesReader.PmTilesHeader header = this.reader.header();
         resolvedMin = header.minZoom();
         resolvedMax = header.maxZoom();
         ok = true;
      } catch (IOException var6) {
         logUnavailable(var6);
      }

      this.available = ok;
      this.minZoom = ok ? resolvedMin : 0;
      this.maxZoom = ok ? resolvedMax : 0;
      this.cache = CacheBuilder.newBuilder()
         .maximumSize(MAX_CACHE_TILES)
         .build(new CacheLoader<TellusLandMaskSource.TileKey, TellusLandMaskSource.LandMaskTile>() {
            
            public TellusLandMaskSource.LandMaskTile load(TellusLandMaskSource.TileKey key) throws Exception {
               return TellusLandMaskSource.this.loadTile(key);
            }
         });
   }

   public TellusLandMaskSource.LandMaskSample sampleLandMask(double blockX, double blockZ, double worldScale) {
      return this.sampleLandMask(blockX, blockZ, worldScale, false);
   }

   public TellusLandMaskSource.LandMaskSample sampleLandMaskLocalOnly(double blockX, double blockZ, double worldScale) {
      return this.sampleLandMask(blockX, blockZ, worldScale, true);
   }

   public TellusLandMaskSource.LandMaskSampler newSampler() {
      return new TellusLandMaskSource.LandMaskSampler();
   }

   private TellusLandMaskSource.LandMaskSample sampleLandMask(double blockX, double blockZ, double worldScale, boolean localOnly) {
      if (this.available && !(worldScale <= 0.0)) {
         double blocksPerDegree = EarthProjection.blocksPerDegree(worldScale);
         double lon = blockX / blocksPerDegree;
         double lat = EarthProjection.blockZToLat(blockZ, worldScale);
         if (!(lat < MIN_LAT) && !(lat > MAX_LAT) && !(lon < MIN_LON) && !(lon > MAX_LON)) {
            int zoom = this.selectZoom(worldScale);
            TellusLandMaskSource.TileKey key = tileKeyForLonLat(lon, lat, zoom);
            if (key == null) {
               return TellusLandMaskSource.LandMaskSample.unknown();
            } else {
               TellusLandMaskSource.LandMaskTile tile = localOnly ? (TellusLandMaskSource.LandMaskTile)this.cache.getIfPresent(key) : this.getTile(key);
               if (tile == null) {
                  return TellusLandMaskSource.LandMaskSample.unknown();
               } else if (tile.isEmpty()) {
                  return TellusLandMaskSource.LandMaskSample.known(false);
               } else {
                  double latRad = Math.toRadians(lat);
                  double n = Math.pow(2.0, zoom);
                  double x = (lon + MAX_LON) / (MAX_LON - MIN_LON) * n;
                  double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
                  int tileX = Mth.floor(x);
                  int tileY = Mth.floor(y);
                  double localX = (x - tileX) * TILE_SIZE;
                  double localY = (y - tileY) * TILE_SIZE;
                  int px = Mth.clamp((int)localX, 0, tile.width() - 1);
                  int py = Mth.clamp((int)localY, 0, tile.height() - 1);
                  return TellusLandMaskSource.LandMaskSample.known(tile.isLand(px, py));
               }
            }
         } else {
            return TellusLandMaskSource.LandMaskSample.unknown();
         }
      } else {
         return TellusLandMaskSource.LandMaskSample.unknown();
      }
   }

   public void prefetchTiles(double blockX, double blockZ, double worldScale, int radius) {
      if (this.available && !(worldScale <= 0.0) && radius > 0) {
         int zoom = this.selectZoom(worldScale);
         TellusLandMaskSource.TileKey center = tileKeyForBlock(blockX, blockZ, worldScale, zoom);
         if (center != null) {
            int tilesPerAxis = 1 << zoom;
            int minX = Math.max(0, center.x() - radius);
            int maxX = Math.min(tilesPerAxis - 1, center.x() + radius);
            int minY = Math.max(0, center.y() - radius);
            int maxY = Math.min(tilesPerAxis - 1, center.y() + radius);

            for (int tileY = minY; tileY <= maxY; tileY++) {
               for (int tileX = minX; tileX <= maxX; tileX++) {
                  this.getTile(new TellusLandMaskSource.TileKey(zoom, tileX, tileY));
               }
            }
         }
      }
   }

   private TellusLandMaskSource.LandMaskTile getTile( TellusLandMaskSource.TileKey key) {
      try {
         return (TellusLandMaskSource.LandMaskTile)this.cache.get(key);
      } catch (Exception var3) {
         Tellus.LOGGER.debug("Failed to load land mask tile {}", key, var3);
         return null;
      }
   }

   private TellusLandMaskSource.LandMaskTile loadTile(TellusLandMaskSource.TileKey key) throws IOException {
      TellusLandMaskSource.TileKey resolvedKey = Objects.requireNonNull(key, "key");
      byte[] bytes = this.reader.getTileBytes(resolvedKey.zoom(), resolvedKey.x(), resolvedKey.y());
      if (bytes == null) {
         return TellusLandMaskSource.LandMaskTile.empty();
      } else {
         BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
         if (image == null) {
            throw new IOException("Invalid land mask tile image");
         } else {
            int width = image.getWidth();
            int height = image.getHeight();
            byte[] mask = new byte[width * height];

            for (int y = 0; y < height; y++) {
               int row = y * width;

               for (int x = 0; x < width; x++) {
                  int value = image.getRaster().getSample(x, y, 0);
                  mask[row + x] = (byte)(value > 0 ? 1 : 0);
               }
            }

            return new TellusLandMaskSource.LandMaskTile(width, height, mask, false);
         }
      }
   }

   private int selectZoom(double worldScale) {
      if (this.available && !(worldScale <= 0.0)) {
         double raw = Math.log(EQUATOR_CIRCUMFERENCE / (TILE_SIZE * worldScale)) / Math.log(2.0);
         int zoom = (int)Math.round(raw);
         return Mth.clamp(zoom, this.minZoom, this.maxZoom);
      } else {
         return this.minZoom;
      }
   }

   private static TellusLandMaskSource.TileKey tileKeyForBlock(double blockX, double blockZ, double worldScale, int zoom) {
      double blocksPerDegree = EarthProjection.blocksPerDegree(worldScale);
      double lon = blockX / blocksPerDegree;
      double lat = EarthProjection.blockZToLat(blockZ, worldScale);
      return tileKeyForLonLat(lon, lat, zoom);
   }

   private static TellusLandMaskSource.TileKey tileKeyForLonLat(double lon, double lat, int zoom) {
      if (!(lat < MIN_LAT) && !(lat > MAX_LAT) && !(lon < MIN_LON) && !(lon > MAX_LON)) {
         double latRad = Math.toRadians(lat);
         double n = Math.pow(2.0, zoom);
         double x = (lon + MAX_LON) / (MAX_LON - MIN_LON) * n;
         double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
         if (!(x < 0.0) && !(y < 0.0) && !(x >= n) && !(y >= n)) {
            int tileX = Mth.floor(x);
            int tileY = Mth.floor(y);
            return new TellusLandMaskSource.TileKey(zoom, tileX, tileY);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String normalizeBaseUrl(String baseUrl) {
      Objects.requireNonNull(baseUrl, "baseUrl");
      return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
   }

   private static void logUnavailable(IOException error) {
      if (LOGGED_UNAVAILABLE.compareAndSet(false, true)) {
         Tellus.LOGGER.info("Land mask PMTiles unavailable ({}); using ESA-only land fallback.", describeError(error));
      }

      Tellus.LOGGER.debug("Land mask PMTiles unavailable; using ESA-only land fallback", error);
   }

   private static String describeError(IOException error) {
      String message = error.getMessage();
      return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
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

   public final class LandMaskSampler {
      private double cachedWorldScale = Double.NaN;
      private int cachedZoom;
      private double cachedBlocksPerDegree;
      private int cachedTileX = Integer.MIN_VALUE;
      private int cachedTileY = Integer.MIN_VALUE;
      private TellusLandMaskSource.LandMaskTile cachedTile;
      private boolean cachedTileSet;

      private LandMaskSampler() {
      }

      public TellusLandMaskSource.LandMaskSample sample(double blockX, double blockZ, double worldScale) {
         if (!TellusLandMaskSource.this.available || worldScale <= 0.0) {
            return TellusLandMaskSource.LandMaskSample.unknown();
         }

         if (worldScale != this.cachedWorldScale) {
            this.cachedWorldScale = worldScale;
            this.cachedZoom = TellusLandMaskSource.this.selectZoom(worldScale);
            this.cachedBlocksPerDegree = EarthProjection.blocksPerDegree(worldScale);
            this.cachedTileSet = false;
         }

         double lon = blockX / this.cachedBlocksPerDegree;
         double lat = EarthProjection.blockZToLat(blockZ, worldScale);
         if (lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) {
            return TellusLandMaskSource.LandMaskSample.unknown();
         }

         double latRad = Math.toRadians(lat);
         double n = Math.pow(2.0, this.cachedZoom);
         double x = (lon + MAX_LON) / (MAX_LON - MIN_LON) * n;
         double y = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
         if (x < 0.0 || y < 0.0 || x >= n || y >= n) {
            return TellusLandMaskSource.LandMaskSample.unknown();
         }

         int tileX = Mth.floor(x);
         int tileY = Mth.floor(y);
         TellusLandMaskSource.LandMaskTile tile;
         if (this.cachedTileSet && tileX == this.cachedTileX && tileY == this.cachedTileY) {
            tile = this.cachedTile;
         } else {
            tile = TellusLandMaskSource.this.getTile(new TellusLandMaskSource.TileKey(this.cachedZoom, tileX, tileY));
            this.cachedTile = tile;
            this.cachedTileX = tileX;
            this.cachedTileY = tileY;
            this.cachedTileSet = true;
         }

         if (tile == null) {
            return TellusLandMaskSource.LandMaskSample.unknown();
         } else if (tile.isEmpty()) {
            return TellusLandMaskSource.LandMaskSample.known(false);
         }

         double localX = (x - tileX) * TILE_SIZE;
         double localY = (y - tileY) * TILE_SIZE;
         int px = Mth.clamp((int)localX, 0, tile.width() - 1);
         int py = Mth.clamp((int)localY, 0, tile.height() - 1);
         return TellusLandMaskSource.LandMaskSample.known(tile.isLand(px, py));
      }
   }

   public record LandMaskSample(boolean known, boolean land) {
      public static TellusLandMaskSource.LandMaskSample known(boolean land) {
         return new TellusLandMaskSource.LandMaskSample(true, land);
      }

      public static TellusLandMaskSource.LandMaskSample unknown() {
         return new TellusLandMaskSource.LandMaskSample(false, false);
      }
   }

   private static final class LandMaskTile {
      private static final TellusLandMaskSource.LandMaskTile EMPTY = new TellusLandMaskSource.LandMaskTile(0, 0, new byte[0], true);
      private final int width;
      private final int height;
      private final byte[] mask;
      private final boolean empty;

      private LandMaskTile(int width, int height, byte[] mask, boolean empty) {
         this.width = width;
         this.height = height;
         this.mask = mask;
         this.empty = empty;
      }

      public static TellusLandMaskSource.LandMaskTile empty() {
         return EMPTY;
      }

      public boolean isEmpty() {
         return this.empty;
      }

      public int width() {
         return this.width;
      }

      public int height() {
         return this.height;
      }

      public boolean isLand(int x, int y) {
         if (!this.empty && this.mask.length != 0) {
            int index = y * this.width + x;
            return index >= 0 && index < this.mask.length ? this.mask[index] != 0 : false;
         } else {
            return false;
         }
      }
   }

   private record TileKey(int zoom, int x, int y) {
   }
}
