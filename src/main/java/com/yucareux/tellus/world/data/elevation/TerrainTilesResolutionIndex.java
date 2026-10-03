package com.yucareux.tellus.world.data.elevation;

import com.yucareux.tellus.Tellus;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.tukaani.xz.SingleXZInputStream;

final class TerrainTilesResolutionIndex {
   private static final byte[] MAGIC = "TELLUS/DEM_RES".getBytes(StandardCharsets.US_ASCII);
   private static final String DEFAULT_RESOURCE_PATH = "/tellus/elevation/terrain_tiles_resolution_index.bin.xz";
   private static final int VERSION = 0;
   private static final int LON_CELLS = 360;
   private static final int LAT_CELLS = 180;
   private static final int MAX_XZ_MEMORY_KIB = 64 * 1024;
   private static final int MAX_REFERENCE_COUNT = 2_000_000;
   private static final int MAX_POLYGON_COUNT = 250_000;
   private static final int MAX_RING_COUNT = 500_000;
   private static final int MAX_POINT_COUNT = 5_000_000;
   private final String resourcePath;
   private final ThreadLocal<TerrainTilesResolutionIndex.LookupState> lookupState = ThreadLocal.withInitial(TerrainTilesResolutionIndex.LookupState::new);
   private volatile TerrainTilesResolutionIndex.Index index;

   static TerrainTilesResolutionIndex create() {
      return create(DEFAULT_RESOURCE_PATH);
   }

   static TerrainTilesResolutionIndex create(String resourcePath) {
      return new TerrainTilesResolutionIndex(resourcePath);
   }

   private TerrainTilesResolutionIndex(String resourcePath) {
      this.resourcePath = resourcePath;
   }

   double lookupResolutionMeters(double lat, double lon) {
      TerrainTilesResolutionIndex.Index loaded = this.loadIndex();
      if (!loaded.available || Double.isNaN(lat) || Double.isNaN(lon)) {
         return Double.NaN;
      } else {
         double clampedLat = Math.max(-90.0, Math.min(89.999999, lat));
         double clampedLon = Math.max(-180.0, Math.min(179.999999, lon));
         TerrainTilesResolutionIndex.LookupState state = this.lookupState.get();
         double bestResolution = Double.POSITIVE_INFINITY;
         int bestPolygonIndex = -1;
         if (state.lastPolygonIndex >= 0 && this.containsPolygon(loaded, state.lastPolygonIndex, clampedLon, clampedLat)) {
            bestPolygonIndex = state.lastPolygonIndex;
            bestResolution = loaded.polygonResolutionMeters[state.lastPolygonIndex];
         }

         int cellX = Math.max(0, Math.min(LON_CELLS - 1, (int)Math.floor(clampedLon) + 180));
         int cellY = Math.max(0, Math.min(LAT_CELLS - 1, (int)Math.floor(clampedLat) + 90));
         int cellIndex = cellY * LON_CELLS + cellX;
         int start = loaded.cellStarts[cellIndex];
         int count = loaded.cellCounts[cellIndex];

         for (int i = 0; i < count; i++) {
            int polygonIndex = loaded.polygonRefs[start + i];
            if (polygonIndex != state.lastPolygonIndex
               && this.containsPolygon(loaded, polygonIndex, clampedLon, clampedLat)
               && loaded.polygonResolutionMeters[polygonIndex] < bestResolution) {
               bestPolygonIndex = polygonIndex;
               bestResolution = loaded.polygonResolutionMeters[polygonIndex];
            }
         }

         state.lastPolygonIndex = bestPolygonIndex;
         return Double.isFinite(bestResolution) ? bestResolution : Double.NaN;
      }
   }

   private boolean containsPolygon(TerrainTilesResolutionIndex.Index index, int polygonIndex, double lon, double lat) {
      if (polygonIndex < 0 || polygonIndex >= index.polygonRingStart.length) {
         return false;
      } else if (lon < index.polygonMinLon[polygonIndex]
         || lon > index.polygonMaxLon[polygonIndex]
         || lat < index.polygonMinLat[polygonIndex]
         || lat > index.polygonMaxLat[polygonIndex]) {
         return false;
      } else {
         int ringStart = index.polygonRingStart[polygonIndex];
         int ringCount = index.polygonRingCount[polygonIndex];
         if (ringCount <= 0 || !this.pointInRing(index, ringStart, lon, lat)) {
            return false;
         } else {
            for (int i = 1; i < ringCount; i++) {
               if (this.pointInRing(index, ringStart + i, lon, lat)) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private boolean pointInRing(TerrainTilesResolutionIndex.Index index, int ringIndex, double lon, double lat) {
      int coordStart = index.ringCoordStart[ringIndex];
      int coordCount = index.ringCoordCount[ringIndex];
      if (coordCount < 3) {
         return false;
      } else {
         int base = coordStart * 2;
         int prev = base + (coordCount - 1) * 2;
         double prevLon = index.coords[prev];
         double prevLat = index.coords[prev + 1];
         boolean inside = false;

         for (int i = 0; i < coordCount; i++) {
            int offset = base + i * 2;
            double currentLon = index.coords[offset];
            double currentLat = index.coords[offset + 1];
            boolean intersects = currentLat > lat != prevLat > lat;
            if (intersects) {
               double edgeLon = (prevLon - currentLon) * (lat - currentLat) / (prevLat - currentLat) + currentLon;
               if (lon < edgeLon) {
                  inside = !inside;
               }
            }

            prevLon = currentLon;
            prevLat = currentLat;
         }

         return inside;
      }
   }

   private TerrainTilesResolutionIndex.Index loadIndex() {
      TerrainTilesResolutionIndex.Index loaded = this.index;
      if (loaded != null) {
         return loaded;
      } else {
         synchronized(this) {
            loaded = this.index;
            if (loaded != null) {
               return loaded;
            } else {
               this.index = loaded = this.readIndex();
               return loaded;
            }
         }
      }
   }

   private TerrainTilesResolutionIndex.Index readIndex() {
      try (InputStream raw = TerrainTilesResolutionIndex.class.getResourceAsStream(this.resourcePath)) {
         if (raw == null) {
            Tellus.LOGGER.warn("Terrain Tiles resolution index resource missing at {}.", this.resourcePath);
            return TerrainTilesResolutionIndex.Index.unavailable();
         } else {
            try (SingleXZInputStream xz = new SingleXZInputStream(raw, MAX_XZ_MEMORY_KIB); DataInputStream input = new DataInputStream(xz)) {
               byte[] magic = new byte[MAGIC.length];
               input.readFully(magic);
               if (!Arrays.equals(magic, MAGIC)) {
                  throw new IOException("Invalid Terrain Tiles resolution index signature");
               } else {
                  int version = input.readUnsignedByte();
                  if (version != VERSION) {
                     throw new IOException("Unsupported Terrain Tiles resolution index version " + version);
                  } else {
                     int lonCells = input.readInt();
                     int latCells = input.readInt();
                     int refCount = input.readInt();
                     int polygonCount = input.readInt();
                     int ringCount = input.readInt();
                     int pointCount = input.readInt();
                     if (lonCells != LON_CELLS || latCells != LAT_CELLS) {
                        throw new IOException("Unexpected Terrain Tiles resolution grid " + lonCells + "x" + latCells);
                     } else {
                        requireCount("reference", refCount, MAX_REFERENCE_COUNT);
                        requireCount("polygon", polygonCount, MAX_POLYGON_COUNT);
                        requireCount("ring", ringCount, MAX_RING_COUNT);
                        requireCount("point", pointCount, MAX_POINT_COUNT);
                        int[] cellStarts = new int[lonCells * latCells];
                        int[] cellCounts = new int[lonCells * latCells];

                        for (int i = 0; i < cellStarts.length; i++) {
                           cellStarts[i] = input.readInt();
                           cellCounts[i] = input.readInt();
                           requireSlice("cell reference", cellStarts[i], cellCounts[i], refCount);
                        }

                        float[] polygonMinLon = new float[polygonCount];
                        float[] polygonMinLat = new float[polygonCount];
                        float[] polygonMaxLon = new float[polygonCount];
                        float[] polygonMaxLat = new float[polygonCount];
                        int[] polygonRingStart = new int[polygonCount];
                        int[] polygonRingCount = new int[polygonCount];
                        float[] polygonResolutionMeters = new float[polygonCount];

                        for (int i = 0; i < polygonCount; i++) {
                           polygonMinLon[i] = input.readFloat();
                           polygonMinLat[i] = input.readFloat();
                           polygonMaxLon[i] = input.readFloat();
                           polygonMaxLat[i] = input.readFloat();
                           polygonRingStart[i] = input.readInt();
                           polygonRingCount[i] = input.readInt();
                           polygonResolutionMeters[i] = input.readFloat();
                           if (!Float.isFinite(polygonMinLon[i])
                              || !Float.isFinite(polygonMinLat[i])
                              || !Float.isFinite(polygonMaxLon[i])
                              || !Float.isFinite(polygonMaxLat[i])
                              || polygonMinLon[i] > polygonMaxLon[i]
                              || polygonMinLat[i] > polygonMaxLat[i]
                              || !Float.isFinite(polygonResolutionMeters[i])
                              || polygonResolutionMeters[i] <= 0.0F) {
                              throw new IOException("Invalid Terrain Tiles polygon metadata at index " + i);
                           }

                           requireSlice("polygon ring", polygonRingStart[i], polygonRingCount[i], ringCount);
                        }

                        int[] ringCoordStart = new int[ringCount];
                        int[] ringCoordCount = new int[ringCount];

                        for (int i = 0; i < ringCount; i++) {
                           ringCoordStart[i] = input.readInt();
                           ringCoordCount[i] = input.readInt();
                           requireSlice("ring coordinate", ringCoordStart[i], ringCoordCount[i], pointCount);
                        }

                        float[] coords = new float[Math.multiplyExact(pointCount, 2)];

                        for (int i = 0; i < coords.length; i++) {
                           coords[i] = input.readFloat();
                           if (!Float.isFinite(coords[i])) {
                              throw new IOException("Non-finite Terrain Tiles coordinate at index " + i);
                           }
                        }

                        int[] polygonRefs = new int[refCount];

                        for (int i = 0; i < refCount; i++) {
                           polygonRefs[i] = input.readInt();
                           if (polygonRefs[i] < 0 || polygonRefs[i] >= polygonCount) {
                              throw new IOException("Invalid Terrain Tiles polygon reference at index " + i);
                           }
                        }

                        if (input.read() != -1) {
                           throw new IOException("Trailing data in Terrain Tiles resolution index");
                        }

                        Tellus.LOGGER.info("Loaded Terrain Tiles resolution index ({} polygons, {} points).", polygonCount, pointCount);
                        return new TerrainTilesResolutionIndex.Index(
                           true,
                           cellStarts,
                           cellCounts,
                           polygonRefs,
                           polygonMinLon,
                           polygonMinLat,
                           polygonMaxLon,
                           polygonMaxLat,
                           polygonRingStart,
                           polygonRingCount,
                           polygonResolutionMeters,
                           ringCoordStart,
                           ringCoordCount,
                           coords
                        );
                     }
                  }
               }
            }
         }
      } catch (IOException | ArithmeticException error) {
         Tellus.LOGGER.warn("Failed to load Terrain Tiles resolution index.", error);
         return TerrainTilesResolutionIndex.Index.unavailable();
      }
   }

   private static void requireCount(String label, int count, int maximum) throws IOException {
      if (count < 0 || count > maximum) {
         throw new IOException("Invalid Terrain Tiles " + label + " count " + count);
      }
   }

   private static void requireSlice(String label, int start, int count, int total) throws IOException {
      if (start < 0 || count < 0 || start > total - count) {
         throw new IOException("Invalid Terrain Tiles " + label + " range " + start + "+" + count);
      }
   }

   private record Index(
      boolean available,
      int[] cellStarts,
      int[] cellCounts,
      int[] polygonRefs,
      float[] polygonMinLon,
      float[] polygonMinLat,
      float[] polygonMaxLon,
      float[] polygonMaxLat,
      int[] polygonRingStart,
      int[] polygonRingCount,
      float[] polygonResolutionMeters,
      int[] ringCoordStart,
      int[] ringCoordCount,
      float[] coords
   ) {
      private static TerrainTilesResolutionIndex.Index unavailable() {
         return new TerrainTilesResolutionIndex.Index(
            false,
            new int[0],
            new int[0],
            new int[0],
            new float[0],
            new float[0],
            new float[0],
            new float[0],
            new int[0],
            new int[0],
            new float[0],
            new int[0],
            new int[0],
            new float[0]
         );
      }
   }

   private static final class LookupState {
      private int lastPolygonIndex = -1;
   }
}
