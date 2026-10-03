package com.yucareux.tellus.preload;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.world.data.cover.LandCoverTransition;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class TerrainPreloadPackage {
   public static final String FILE_NAME = "terrain.telluspack";
   public static final int FORMAT_VERSION = 4;
   static final int MAGIC = 0x544C504B;
   static final int SAMPLE_BYTES = Integer.BYTES + 3;
   static final int HARD_MAX_SAMPLE_COUNT = 20_000_000;
   static final int MAX_LOADED_SAMPLE_COUNT = resolveMaxLoadedSampleCount();
   private static final int CANCELLATION_CHECK_MASK = 4095;
   static final int FLAG_OPENWATERS_SELECTED = 1;
   static final int FLAG_MAPTERHORN_LAND_OVERRIDE = 1 << 1;
   static final int FLAG_OCEAN_ELEVATION_SELECTED = 1 << 2;
   private final String id;
   private final String settingsFingerprint;
   private final TerrainPreloadArea area;
   private final int gridStep;
   private final int gridWidth;
   private final int gridDepth;
   private final int[] terrainHeights;
   private final byte[] coverClasses;
   private final byte[] landMaskClasses;
   private final byte[] elevationFlags;

   TerrainPreloadPackage(
      String id,
      String settingsFingerprint,
      TerrainPreloadArea area,
      int gridStep,
      int gridWidth,
      int gridDepth,
      int[] terrainHeights,
      byte[] coverClasses,
      byte[] landMaskClasses,
      byte[] elevationFlags
   ) {
      this.id = Objects.requireNonNull(id, "id");
      this.settingsFingerprint = Objects.requireNonNull(settingsFingerprint, "settingsFingerprint");
      this.area = Objects.requireNonNull(area, "area");
      if (gridStep <= 0 || gridWidth <= 0 || gridDepth <= 0) {
         throw new IllegalArgumentException("Terrain preload grid dimensions must be positive");
      }
      int total = checkedSampleCountUnchecked(gridWidth, gridDepth);
      this.gridStep = gridStep;
      this.gridWidth = gridWidth;
      this.gridDepth = gridDepth;
      this.terrainHeights = Objects.requireNonNull(terrainHeights, "terrainHeights");
      this.coverClasses = Objects.requireNonNull(coverClasses, "coverClasses");
      this.landMaskClasses = Objects.requireNonNull(landMaskClasses, "landMaskClasses");
      this.elevationFlags = Objects.requireNonNull(elevationFlags, "elevationFlags");
      if (this.terrainHeights.length != total
         || this.coverClasses.length != total
         || this.landMaskClasses.length != total
         || this.elevationFlags.length != total) {
         throw new IllegalArgumentException("Terrain preload grid arrays do not match the declared dimensions");
      }
   }

   static long write(
      Path path,
      String id,
      String settingsFingerprint,
      TerrainPreloadArea area,
      int gridStep,
      int gridWidth,
      int gridDepth,
      int[] terrainHeights,
      byte[] coverClasses,
      byte[] landMaskClasses,
      byte[] elevationFlags
   ) throws IOException {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(settingsFingerprint, "settingsFingerprint");
      Objects.requireNonNull(area, "area");
      Objects.requireNonNull(terrainHeights, "terrainHeights");
      Objects.requireNonNull(coverClasses, "coverClasses");
      Objects.requireNonNull(landMaskClasses, "landMaskClasses");
      Objects.requireNonNull(elevationFlags, "elevationFlags");
      if (gridStep <= 0 || gridWidth <= 0 || gridDepth <= 0) {
         throw new IllegalArgumentException("Terrain preload grid dimensions must be positive");
      }

      int total = checkedSampleCountUnchecked(gridWidth, gridDepth);
      if (terrainHeights.length != total
         || coverClasses.length != total
         || landMaskClasses.length != total
         || elevationFlags.length != total) {
         throw new IllegalArgumentException("Terrain preload grid arrays do not match the declared dimensions");
      }

      Path parent = path.getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      throwIfWriteCancelled();

      try (
         DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))
         )
      ) {
         output.writeInt(MAGIC);
         output.writeInt(FORMAT_VERSION);
         output.writeUTF(id);
         output.writeUTF(settingsFingerprint);
         output.writeDouble(area.centerLatitude());
         output.writeDouble(area.centerLongitude());
         output.writeInt(area.chunksPerSide());
         output.writeDouble(area.worldScale());
         output.writeInt(area.minChunkX());
         output.writeInt(area.minChunkZ());
         output.writeInt(area.maxChunkX());
         output.writeInt(area.maxChunkZ());
         output.writeDouble(area.northLatitude());
         output.writeDouble(area.southLatitude());
         output.writeDouble(area.westLongitude());
         output.writeDouble(area.eastLongitude());
         output.writeInt(gridStep);
         output.writeInt(gridWidth);
         output.writeInt(gridDepth);
         for (int index = 0; index < total; index++) {
            if ((index & CANCELLATION_CHECK_MASK) == 0) {
               throwIfWriteCancelled();
            }
            output.writeInt(terrainHeights[index]);
            output.writeByte(coverClasses[index]);
            output.writeByte(landMaskClasses[index]);
            output.writeByte(elevationFlags[index]);
         }
         throwIfWriteCancelled();
      }

      return Files.size(path);
   }

   static byte encodeLandMask(TerrainPreloadPackage.Sample sample) {
      return sample.landMaskKnown() ? (byte)(sample.land() ? 1 : 0) : -1;
   }

   static byte encodeElevationFlags(TerrainPreloadPackage.Sample sample) {
      int flags = 0;
      if (sample.openWatersSelected()) {
         flags |= FLAG_OPENWATERS_SELECTED;
      }
      if (sample.mapterhornLandOverride()) {
         flags |= FLAG_MAPTERHORN_LAND_OVERRIDE;
      }
      if (sample.oceanElevationSelected()) {
         flags |= FLAG_OCEAN_ELEVATION_SELECTED;
      }
      return (byte)flags;
   }

   public static TerrainPreloadPackage read(Path path) throws IOException {
      Objects.requireNonNull(path, "path");
      try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
         int magic = input.readInt();
         if (magic != MAGIC) {
            throw new IOException("Invalid terrain preload package magic");
         }

         int version = input.readInt();
         if (version != FORMAT_VERSION) {
            throw new IOException("Unsupported terrain preload package version " + version);
         }

         String id = input.readUTF();
         String settingsFingerprint = input.readUTF();
         double centerLatitude = input.readDouble();
         double centerLongitude = input.readDouble();
         int chunksPerSide = input.readInt();
         double worldScale = input.readDouble();
         int minChunkX = input.readInt();
         int minChunkZ = input.readInt();
         int maxChunkX = input.readInt();
         int maxChunkZ = input.readInt();
         double northLatitude = input.readDouble();
         double southLatitude = input.readDouble();
         double westLongitude = input.readDouble();
         double eastLongitude = input.readDouble();
         int gridStep = input.readInt();
         int gridWidth = input.readInt();
         int gridDepth = input.readInt();
         if (gridStep <= 0) {
            throw new IOException("Terrain preload grid step must be positive");
         }
         int total = checkedSampleCount(gridWidth, gridDepth);
         TerrainPreloadArea area;
         try {
            area = new TerrainPreloadArea(
               centerLatitude,
               centerLongitude,
               chunksPerSide,
               worldScale,
               minChunkX,
               minChunkZ,
               maxChunkX,
               maxChunkZ,
               northLatitude,
               southLatitude,
               westLongitude,
               eastLongitude
            );
         } catch (IllegalArgumentException error) {
            throw new IOException("Invalid terrain preload area: " + error.getMessage(), error);
         }
         int[] terrainHeights = new int[total];
         byte[] coverClasses = new byte[total];
         byte[] landMaskClasses = new byte[total];
         byte[] elevationFlags = new byte[total];
         for (int index = 0; index < total; index++) {
            terrainHeights[index] = input.readInt();
            coverClasses[index] = input.readByte();
            landMaskClasses[index] = input.readByte();
            elevationFlags[index] = input.readByte();
         }

         if (input.read() != -1) {
            throw new IOException("Trailing data in terrain preload package");
         }

         return new TerrainPreloadPackage(
            id,
            settingsFingerprint,
            area,
            gridStep,
            gridWidth,
            gridDepth,
            terrainHeights,
            coverClasses,
            landMaskClasses,
            elevationFlags
         );
      }
   }

   public String id() {
      return this.id;
   }

   public String settingsFingerprint() {
      return this.settingsFingerprint;
   }

   public TerrainPreloadArea area() {
      return this.area;
   }

   public int gridStep() {
      return this.gridStep;
   }

   public int gridWidth() {
      return this.gridWidth;
   }

   public int gridDepth() {
      return this.gridDepth;
   }

   public double resolutionMeters() {
      return this.gridStep * this.area.worldScale();
   }

   public boolean supportsResolution(double requestedResolutionMeters) {
      return !Double.isFinite(requestedResolutionMeters)
         || requestedResolutionMeters <= 0.0
         || requestedResolutionMeters + Math.ulp(requestedResolutionMeters) >= this.resolutionMeters();
   }

   public TerrainPreloadPackage.Sample sample(int blockX, int blockZ) {
      if (!this.containsBlock(blockX, blockZ)) {
         return null;
      }

      double gridX = (blockX - this.area.minBlockX()) / (double)this.gridStep;
      double gridZ = (blockZ - this.area.minBlockZ()) / (double)this.gridStep;
      int x0 = clamp((int)Math.floor(gridX), 0, this.gridWidth - 1);
      int z0 = clamp((int)Math.floor(gridZ), 0, this.gridDepth - 1);
      int x1 = Math.min(this.gridWidth - 1, x0 + 1);
      int z1 = Math.min(this.gridDepth - 1, z0 + 1);
      double tx = clamp(gridX - x0, 0.0, 1.0);
      double tz = clamp(gridZ - z0, 0.0, 1.0);
      int index00 = this.index(x0, z0);
      int index10 = this.index(x1, z0);
      int index01 = this.index(x0, z1);
      int index11 = this.index(x1, z1);
      int nearestX = clamp((int)Math.round(gridX), 0, this.gridWidth - 1);
      int nearestZ = clamp((int)Math.round(gridZ), 0, this.gridDepth - 1);
      int nearest = this.index(nearestX, nearestZ);
      int height = categoriesAgree(index00, index10, index01, index11, this.landMaskClasses, this.elevationFlags)
         ? interpolateHeight(
            this.terrainHeights[index00],
            this.terrainHeights[index10],
            this.terrainHeights[index01],
            this.terrainHeights[index11],
            tx,
            tz
         )
         : this.terrainHeights[nearest];
      int coverClass = Byte.toUnsignedInt(this.coverClasses[nearest]);
      int visualCoverClass = LandCoverTransition.selectVisualClass(
         coverClass,
         Byte.toUnsignedInt(this.coverClasses[index00]),
         Byte.toUnsignedInt(this.coverClasses[index10]),
         Byte.toUnsignedInt(this.coverClasses[index01]),
         Byte.toUnsignedInt(this.coverClasses[index11]),
         tx,
         tz,
         1.0,
         blockX,
         blockZ,
         this.gridStep
      );
      byte landMask = this.landMaskClasses[nearest];
      int flags = Byte.toUnsignedInt(this.elevationFlags[nearest]);
      return new TerrainPreloadPackage.Sample(
         height,
         coverClass,
         visualCoverClass,
         landMask >= 0,
         landMask == 1,
         (flags & FLAG_OPENWATERS_SELECTED) != 0,
         (flags & FLAG_MAPTERHORN_LAND_OVERRIDE) != 0,
         (flags & FLAG_OCEAN_ELEVATION_SELECTED) != 0
      );
   }

   public boolean containsBlock(int blockX, int blockZ) {
      return blockX >= this.area.minBlockX()
         && blockX <= this.area.maxBlockX()
         && blockZ >= this.area.minBlockZ()
         && blockZ <= this.area.maxBlockZ();
   }

   boolean matches(String fingerprint, int blockX, int blockZ) {
      return this.settingsFingerprint.equals(fingerprint) && this.containsBlock(blockX, blockZ);
   }

   private int index(int x, int z) {
      return z * this.gridWidth + x;
   }

   private static int interpolateHeight(int h00, int h10, int h01, int h11, double tx, double tz) {
      if (h00 == Integer.MIN_VALUE || h10 == Integer.MIN_VALUE || h01 == Integer.MIN_VALUE || h11 == Integer.MIN_VALUE) {
         return firstValidHeight(h00, h10, h01, h11);
      }

      double north = h00 + (h10 - h00) * tx;
      double south = h01 + (h11 - h01) * tx;
      return (int)Math.round(north + (south - north) * tz);
   }

   private static boolean categoriesAgree(
      int index00, int index10, int index01, int index11, byte[] landMaskClasses, byte[] elevationFlags
   ) {
      byte landMask = landMaskClasses[index00];
      byte flags = elevationFlags[index00];
      return landMaskClasses[index10] == landMask
         && landMaskClasses[index01] == landMask
         && landMaskClasses[index11] == landMask
         && elevationFlags[index10] == flags
         && elevationFlags[index01] == flags
         && elevationFlags[index11] == flags;
   }

   private static int firstValidHeight(int... heights) {
      for (int height : heights) {
         if (height != Integer.MIN_VALUE) {
            return height;
         }
      }

      Tellus.LOGGER.warn("Terrain preload package contained only missing height samples");
      return 0;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   static int checkedSampleCount(int gridWidth, int gridDepth) throws IOException {
      if (gridWidth <= 0 || gridDepth <= 0) {
         throw new IOException("Terrain preload grid dimensions must be positive");
      }

      long total = (long)gridWidth * gridDepth;
      if (total > MAX_LOADED_SAMPLE_COUNT) {
         throw new IOException(
            "Terrain preload grid contains " + total + " samples; the safe load limit is " + MAX_LOADED_SAMPLE_COUNT
         );
      }
      return (int)total;
   }

   private static int checkedSampleCountUnchecked(int gridWidth, int gridDepth) {
      try {
         return checkedSampleCount(gridWidth, gridDepth);
      } catch (IOException error) {
         throw new IllegalArgumentException(error.getMessage(), error);
      }
   }

   private static int resolveMaxLoadedSampleCount() {
      long heapBound = Math.max(1L, Runtime.getRuntime().maxMemory() / (SAMPLE_BYTES * 8L));
      int safeHeapBound = (int)Math.min(HARD_MAX_SAMPLE_COUNT, heapBound);
      int configured = Integer.getInteger("tellus.preload.package.maxLoadedSamples", safeHeapBound);
      return Math.max(1, Math.min(safeHeapBound, configured));
   }

   private static void throwIfWriteCancelled() throws IOException {
      if (Thread.currentThread().isInterrupted()) {
         throw new IOException("Terrain preload package write was cancelled");
      }
   }

   public record Sample(
      int terrainHeight,
      int coverClass,
      int visualCoverClass,
      boolean landMaskKnown,
      boolean land,
      boolean openWatersSelected,
      boolean mapterhornLandOverride,
      boolean oceanElevationSelected
   ) {
      public Sample(
         int terrainHeight,
         int coverClass,
         boolean landMaskKnown,
         boolean land,
         boolean openWatersSelected,
         boolean mapterhornLandOverride,
         boolean oceanElevationSelected
      ) {
         this(
            terrainHeight,
            coverClass,
            coverClass,
            landMaskKnown,
            land,
            openWatersSelected,
            mapterhornLandOverride,
            oceanElevationSelected
         );
      }
   }
}
