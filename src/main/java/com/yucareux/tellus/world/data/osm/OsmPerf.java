package com.yucareux.tellus.world.data.osm;

import com.yucareux.tellus.Tellus;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class OsmPerf {
   public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("tellus.debug.osmPerf", "false"));
   private static final long LOG_INTERVAL_NS = TimeUnit.SECONDS.toNanos(15L);
   private static final AtomicLong NEXT_LOG_AT_NS = new AtomicLong(System.nanoTime() + LOG_INTERVAL_NS);
   private static final LongAdder FULL_CHUNK_ROAD_FETCH_NS = new LongAdder();
   private static final LongAdder FULL_CHUNK_ROAD_RASTER_NS = new LongAdder();
   private static final LongAdder FULL_CHUNK_ROAD_CALLS = new LongAdder();
   private static final LongAdder FULL_CHUNK_ROAD_FEATURES = new LongAdder();
   private static final LongAdder FULL_CHUNK_BUILDING_FETCH_NS = new LongAdder();
   private static final LongAdder FULL_CHUNK_BUILDING_RASTER_NS = new LongAdder();
   private static final LongAdder FULL_CHUNK_BUILDING_CALLS = new LongAdder();
   private static final LongAdder FULL_CHUNK_BUILDING_FEATURES = new LongAdder();
   private static final LongAdder DH_ROAD_MASK_NS = new LongAdder();
   private static final LongAdder DH_ROAD_MASK_CALLS = new LongAdder();
   private static final LongAdder DH_ROAD_MASK_FEATURES = new LongAdder();
   private static final LongAdder DH_BUILDING_MASK_NS = new LongAdder();
   private static final LongAdder DH_BUILDING_MASK_CALLS = new LongAdder();
   private static final LongAdder DH_BUILDING_MASK_FEATURES = new LongAdder();
   private static final LongAdder WATER_QUERY_NS = new LongAdder();
   private static final LongAdder WATER_QUERY_CALLS = new LongAdder();
   private static final LongAdder WATER_QUERY_FEATURES = new LongAdder();
   private static final LongAdder WATER_CHUNK_RESOLVE_NS = new LongAdder();
   private static final LongAdder WATER_CHUNK_RESOLVE_CALLS = new LongAdder();
   private static final LongAdder WATER_CHUNK_RESOLVE_CACHE_HITS = new LongAdder();
   private static final LongAdder WATER_CHUNK_RESOLVE_DEFERRED_FALLBACKS = new LongAdder();
   private static final LongAdder WATER_CHUNK_RESOLVE_BLOCKING_CALLS = new LongAdder();
   private static final OsmPerf.TileSource[] TILE_SOURCES = OsmPerf.TileSource.values();
   private static final OsmPerf.TileLoadPath[] TILE_LOAD_PATHS = OsmPerf.TileLoadPath.values();
   private static final LongAdder[] TILE_LOAD_COUNTS = createTileLoadCounters();

   private OsmPerf() {
   }

   public static long now() {
      return ENABLED ? System.nanoTime() : 0L;
   }

   public static long elapsedSince(long startNs) {
      return ENABLED && startNs != 0L ? System.nanoTime() - startNs : 0L;
   }

   public static void recordFullChunkRoad(long fetchNs, long rasterNs, int features) {
      if (ENABLED) {
         FULL_CHUNK_ROAD_CALLS.increment();
         if (fetchNs > 0L) {
            FULL_CHUNK_ROAD_FETCH_NS.add(fetchNs);
         }

         if (rasterNs > 0L) {
            FULL_CHUNK_ROAD_RASTER_NS.add(rasterNs);
         }

         if (features > 0) {
            FULL_CHUNK_ROAD_FEATURES.add(features);
         }

         maybeLog();
      }
   }

   public static void recordFullChunkBuilding(long fetchNs, long rasterNs, int features) {
      if (ENABLED) {
         FULL_CHUNK_BUILDING_CALLS.increment();
         if (fetchNs > 0L) {
            FULL_CHUNK_BUILDING_FETCH_NS.add(fetchNs);
         }

         if (rasterNs > 0L) {
            FULL_CHUNK_BUILDING_RASTER_NS.add(rasterNs);
         }

         if (features > 0) {
            FULL_CHUNK_BUILDING_FEATURES.add(features);
         }

         maybeLog();
      }
   }

   public static void recordDhRoadMaskBuild(long totalNs, int features) {
      if (ENABLED) {
         DH_ROAD_MASK_CALLS.increment();
         if (totalNs > 0L) {
            DH_ROAD_MASK_NS.add(totalNs);
         }
         if (features > 0) {
            DH_ROAD_MASK_FEATURES.add(features);
         }
         maybeLog();
      }
   }

   public static void recordDhBuildingMaskBuild(long totalNs, int features) {
      if (ENABLED) {
         DH_BUILDING_MASK_CALLS.increment();
         if (totalNs > 0L) {
            DH_BUILDING_MASK_NS.add(totalNs);
         }
         if (features > 0) {
            DH_BUILDING_MASK_FEATURES.add(features);
         }
         maybeLog();
      }
   }

   public static void recordWaterQuery(long totalNs, int features) {
      if (ENABLED) {
         WATER_QUERY_CALLS.increment();
         if (totalNs > 0L) {
            WATER_QUERY_NS.add(totalNs);
         }

         if (features > 0) {
            WATER_QUERY_FEATURES.add(features);
         }

         maybeLog();
      }
   }

   public static void recordWaterChunkResolve(long totalNs, boolean cachedRegion, boolean deferredFallback, boolean blockingResolved) {
      if (ENABLED) {
         WATER_CHUNK_RESOLVE_CALLS.increment();
         if (totalNs > 0L) {
            WATER_CHUNK_RESOLVE_NS.add(totalNs);
         }

         if (cachedRegion) {
            WATER_CHUNK_RESOLVE_CACHE_HITS.increment();
         }

         if (deferredFallback) {
            WATER_CHUNK_RESOLVE_DEFERRED_FALLBACKS.increment();
         }

         if (blockingResolved) {
            WATER_CHUNK_RESOLVE_BLOCKING_CALLS.increment();
         }

         maybeLog();
      }
   }

   public static void recordTileLoad(OsmPerf.TileSource source, OsmPerf.TileLoadPath path) {
      if (ENABLED && source != null && path != null) {
         TILE_LOAD_COUNTS[counterIndex(source, path)].increment();
         maybeLog();
      }
   }

   private static LongAdder[] createTileLoadCounters() {
      LongAdder[] counters = new LongAdder[TILE_SOURCES.length * TILE_LOAD_PATHS.length];

      for (int i = 0; i < counters.length; i++) {
         counters[i] = new LongAdder();
      }

      return counters;
   }

   private static int counterIndex(OsmPerf.TileSource source, OsmPerf.TileLoadPath path) {
      return source.ordinal() * TILE_LOAD_PATHS.length + path.ordinal();
   }

   private static void maybeLog() {
      long now = System.nanoTime();
      long next = NEXT_LOG_AT_NS.get();
      if (now >= next && NEXT_LOG_AT_NS.compareAndSet(next, now + LOG_INTERVAL_NS)) {
         logAndReset();
      }
   }

   private static void logAndReset() {
      long roadFetchNs = FULL_CHUNK_ROAD_FETCH_NS.sumThenReset();
      long roadRasterNs = FULL_CHUNK_ROAD_RASTER_NS.sumThenReset();
      long roadCalls = FULL_CHUNK_ROAD_CALLS.sumThenReset();
      long roadFeatures = FULL_CHUNK_ROAD_FEATURES.sumThenReset();
      long buildingFetchNs = FULL_CHUNK_BUILDING_FETCH_NS.sumThenReset();
      long buildingRasterNs = FULL_CHUNK_BUILDING_RASTER_NS.sumThenReset();
      long buildingCalls = FULL_CHUNK_BUILDING_CALLS.sumThenReset();
      long buildingFeatures = FULL_CHUNK_BUILDING_FEATURES.sumThenReset();
      long dhRoadMaskNs = DH_ROAD_MASK_NS.sumThenReset();
      long dhRoadMaskCalls = DH_ROAD_MASK_CALLS.sumThenReset();
      long dhRoadMaskFeatures = DH_ROAD_MASK_FEATURES.sumThenReset();
      long dhBuildingMaskNs = DH_BUILDING_MASK_NS.sumThenReset();
      long dhBuildingMaskCalls = DH_BUILDING_MASK_CALLS.sumThenReset();
      long dhBuildingMaskFeatures = DH_BUILDING_MASK_FEATURES.sumThenReset();
      long waterQueryNs = WATER_QUERY_NS.sumThenReset();
      long waterQueryCalls = WATER_QUERY_CALLS.sumThenReset();
      long waterQueryFeatures = WATER_QUERY_FEATURES.sumThenReset();
      long waterChunkResolveNs = WATER_CHUNK_RESOLVE_NS.sumThenReset();
      long waterChunkResolveCalls = WATER_CHUNK_RESOLVE_CALLS.sumThenReset();
      long waterChunkResolveCacheHits = WATER_CHUNK_RESOLVE_CACHE_HITS.sumThenReset();
      long waterChunkResolveDeferredFallbacks = WATER_CHUNK_RESOLVE_DEFERRED_FALLBACKS.sumThenReset();
      long waterChunkResolveBlockingCalls = WATER_CHUNK_RESOLVE_BLOCKING_CALLS.sumThenReset();
      StringBuilder tileLoads = new StringBuilder();

      for (OsmPerf.TileSource source : TILE_SOURCES) {
         if (tileLoads.length() > 0) {
            tileLoads.append("; ");
         }

         tileLoads.append(source.logId()).append("[");

         for (int i = 0; i < TILE_LOAD_PATHS.length; i++) {
            if (i > 0) {
               tileLoads.append(", ");
            }

            OsmPerf.TileLoadPath path = TILE_LOAD_PATHS[i];
            long value = TILE_LOAD_COUNTS[counterIndex(source, path)].sumThenReset();
            tileLoads.append(path.logId()).append("=").append(value);
         }

         tileLoads.append("]");
      }

      Tellus.LOGGER
         .info(
            "OSM perf 15s: fullChunkRoad(fetch={}ms,raster={}ms,calls={},features={}) fullChunkBuilding(fetch={}ms,raster={}ms,calls={},features={}) dhRoadMask(total={}ms,calls={},features={}) dhBuildingMask(total={}ms,calls={},features={}) waterQuery(total={}ms,calls={},features={}) waterChunk(total={}ms,calls={},cached={},deferredFallback={},blocking={}) tileLoads={}",
            new Object[]{
               toMillis(roadFetchNs),
               toMillis(roadRasterNs),
               roadCalls,
               roadFeatures,
               toMillis(buildingFetchNs),
               toMillis(buildingRasterNs),
               buildingCalls,
               buildingFeatures,
               toMillis(dhRoadMaskNs),
               dhRoadMaskCalls,
               dhRoadMaskFeatures,
               toMillis(dhBuildingMaskNs),
               dhBuildingMaskCalls,
               dhBuildingMaskFeatures,
               toMillis(waterQueryNs),
               waterQueryCalls,
               waterQueryFeatures,
               toMillis(waterChunkResolveNs),
               waterChunkResolveCalls,
               waterChunkResolveCacheHits,
               waterChunkResolveDeferredFallbacks,
               waterChunkResolveBlockingCalls,
               tileLoads.toString()
            }
         );
   }

   private static String toMillis(long nanos) {
      return nanos <= 0L ? "0.00" : String.format(Locale.ROOT, "%.2f", nanos / 1000000.0);
   }

   public static enum TileLoadPath {
      MEMORY("memory"),
      PARSED_DISK("parsed"),
      RAW_DISK("raw"),
      NETWORK("network"),
      FAILURE("failure");

      private final String logId;

      private TileLoadPath(String logId) {
         this.logId = logId;
      }

      public String logId() {
         return this.logId;
      }
   }

   public static enum TileSource {
      OSM_ROADS("osmRoads"),
      OSM_WATER("osmWater"),
      OSM_BUILDINGS("osmBuildings"),
      OSM_SAND("osmSand"),
      OSM_STREET_LIGHTS("osmStreetLights");

      private final String logId;

      private TileSource(String logId) {
         this.logId = logId;
      }

      public String logId() {
         return this.logId;
      }
   }
}
