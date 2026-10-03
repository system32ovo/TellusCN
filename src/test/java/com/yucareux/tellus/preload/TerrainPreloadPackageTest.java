package com.yucareux.tellus.preload;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainPreloadPackageTest {
   @TempDir
   Path tempDirectory;

   @Test
   void roundTripsElevationSourceAndLandOverrideFlags() throws Exception {
      Path path = this.tempDirectory.resolve(TerrainPreloadPackage.FILE_NAME);
      int oceanFlags = TerrainPreloadPackage.FLAG_OPENWATERS_SELECTED
         | TerrainPreloadPackage.FLAG_OCEAN_ELEVATION_SELECTED;
      int landFlags = TerrainPreloadPackage.FLAG_MAPTERHORN_LAND_OVERRIDE;

      try (
         DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))
         )
      ) {
         output.writeInt(TerrainPreloadPackage.MAGIC);
         output.writeInt(TerrainPreloadPackage.FORMAT_VERSION);
         output.writeUTF("test-package");
         output.writeUTF("settings");
         output.writeDouble(0.0);
         output.writeDouble(0.0);
         output.writeInt(1);
         output.writeDouble(1.0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeDouble(1.0);
         output.writeDouble(-1.0);
         output.writeDouble(-1.0);
         output.writeDouble(1.0);
         output.writeInt(1);
         output.writeInt(2);
         output.writeInt(1);
         output.writeInt(-50);
         output.writeByte(80);
         output.writeByte(0);
         output.writeByte(oceanFlags);
         output.writeInt(72);
         output.writeByte(10);
         output.writeByte(0);
         output.writeByte(landFlags);
      }

      TerrainPreloadPackage pack = TerrainPreloadPackage.read(path);
      TerrainPreloadPackage.Sample ocean = pack.sample(0, 0);
      TerrainPreloadPackage.Sample land = pack.sample(1, 0);

      assertEquals(-50, ocean.terrainHeight());
      assertEquals(80, ocean.coverClass());
      assertTrue(ocean.landMaskKnown());
      assertTrue(ocean.openWatersSelected());
      assertTrue(ocean.oceanElevationSelected());
      assertEquals(72, land.terrainHeight());
      assertTrue(land.mapterhornLandOverride());
   }

   @Test
   void writerRoundTripsPreparedGrid() throws Exception {
      Path path = this.tempDirectory.resolve("prepared").resolve(TerrainPreloadPackage.FILE_NAME);
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 2, 1.0, 0, 0, 1, 0);
      TerrainPreloadPackage.Sample land = new TerrainPreloadPackage.Sample(120, 10, true, true, false, true, false);
      TerrainPreloadPackage.Sample ocean = new TerrainPreloadPackage.Sample(-42, 80, true, false, true, false, true);
      int[] heights = new int[]{land.terrainHeight(), ocean.terrainHeight(), 80, 90};
      byte[] covers = new byte[]{(byte)land.coverClass(), (byte)ocean.coverClass(), 30, 40};
      byte[] masks = new byte[]{
         TerrainPreloadPackage.encodeLandMask(land),
         TerrainPreloadPackage.encodeLandMask(ocean),
         -1,
         1
      };
      byte[] flags = new byte[]{
         TerrainPreloadPackage.encodeElevationFlags(land),
         TerrainPreloadPackage.encodeElevationFlags(ocean),
         0,
         0
      };

      long bytes = TerrainPreloadPackage.write(
         path, "prepared", "fingerprint", area, 16, 2, 2, heights, covers, masks, flags
      );

      assertTrue(bytes > 0L);
      TerrainPreloadPackage pack = TerrainPreloadPackage.read(path);
      TerrainPreloadPackage.Sample first = pack.sample(0, 0);
      TerrainPreloadPackage.Sample second = pack.sample(16, 0);
      assertEquals(120, first.terrainHeight());
      assertTrue(first.land());
      assertTrue(first.mapterhornLandOverride());
      assertEquals(-42, second.terrainHeight());
      assertFalse(second.land());
      assertTrue(second.openWatersSelected());
      assertTrue(second.oceanElevationSelected());
   }

   @Test
   void rejectsUnsafeGridDimensionsBeforeReadingSamples() throws Exception {
      Path negative = this.tempDirectory.resolve("negative.telluspack");
      this.writeHeader(negative, 1, -1, 2);
      IOException negativeError = assertThrows(IOException.class, () -> TerrainPreloadPackage.read(negative));
      assertTrue(negativeError.getMessage().contains("dimensions must be positive"));

      Path excessive = this.tempDirectory.resolve("excessive.telluspack");
      this.writeHeader(excessive, 1, TerrainPreloadPackage.MAX_LOADED_SAMPLE_COUNT, 2);
      IOException excessiveError = assertThrows(IOException.class, () -> TerrainPreloadPackage.read(excessive));
      assertTrue(excessiveError.getMessage().contains("safe load limit"));
   }

   @Test
   void rejectsMalformedAreaMetadataAsAnIoFailure() throws Exception {
      Path path = this.tempDirectory.resolve("invalid-area.telluspack");
      try (
         DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))
         )
      ) {
         output.writeInt(TerrainPreloadPackage.MAGIC);
         output.writeInt(TerrainPreloadPackage.FORMAT_VERSION);
         output.writeUTF("test-package");
         output.writeUTF("settings");
         output.writeDouble(0.0);
         output.writeDouble(0.0);
         output.writeInt(1);
         output.writeDouble(1.0);
         output.writeInt(Integer.MAX_VALUE);
         output.writeInt(0);
         output.writeInt(Integer.MAX_VALUE);
         output.writeInt(0);
         output.writeDouble(1.0);
         output.writeDouble(-1.0);
         output.writeDouble(-1.0);
         output.writeDouble(1.0);
         output.writeInt(1);
         output.writeInt(1);
         output.writeInt(1);
      }

      IOException error = assertThrows(IOException.class, () -> TerrainPreloadPackage.read(path));
      assertTrue(error.getMessage().contains("Invalid terrain preload area"));
   }

   @Test
   void interruptedWriterPreservesInterruptStatus() {
      Path path = this.tempDirectory.resolve("cancelled.telluspack");
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 1, 1.0, 0, 0, 0, 0);
      Thread.currentThread().interrupt();
      try {
         IOException error = assertThrows(
            IOException.class,
            () -> TerrainPreloadPackage.write(
               path,
               "cancelled",
               "fingerprint",
               area,
               1,
               1,
               1,
               new int[]{64},
               new byte[]{10},
               new byte[]{1},
               new byte[]{0}
            )
         );
         assertTrue(error.getMessage().contains("cancelled"));
         assertTrue(Thread.currentThread().isInterrupted());
      } finally {
         Thread.interrupted();
      }
   }

   @Test
   void exposesPackageResolutionCompatibility() throws Exception {
      Path path = this.tempDirectory.resolve("resolution.telluspack");
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 1, 2.0, 0, 0, 0, 0);
      TerrainPreloadPackage.write(
         path,
         "resolution",
         "fingerprint",
         area,
         4,
         1,
         1,
         new int[]{64},
         new byte[]{10},
         new byte[]{1},
         new byte[]{0}
      );

      TerrainPreloadPackage pack = TerrainPreloadPackage.read(path);
      assertFalse(pack.supportsResolution(7.99));
      assertTrue(pack.supportsResolution(8.0));
      assertTrue(pack.supportsResolution(16.0));
   }

   @Test
   void usesCoherentNearestHeightAcrossCategoricalBoundaries() {
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 1, 1.0, 0, 0, 0, 0);
      int[] heights = new int[]{0, 100, 200, 400};
      byte[] covers = new byte[]{10, 10, 10, 80};
      byte[] landBoundary = new byte[]{1, 1, 1, 0};
      byte[] sourceBoundary = new byte[]{
         0,
         0,
         0,
         TerrainPreloadPackage.FLAG_OPENWATERS_SELECTED
      };
      TerrainPreloadPackage landMaskPack = new TerrainPreloadPackage(
         "land-boundary", "fingerprint", area, 16, 2, 2, heights, covers, landBoundary, new byte[4]
      );
      TerrainPreloadPackage sourcePack = new TerrainPreloadPackage(
         "source-boundary", "fingerprint", area, 16, 2, 2, heights, covers, new byte[]{1, 1, 1, 1}, sourceBoundary
      );

      TerrainPreloadPackage.Sample landMaskSample = landMaskPack.sample(8, 8);
      TerrainPreloadPackage.Sample sourceSample = sourcePack.sample(8, 8);
      assertEquals(400, landMaskSample.terrainHeight());
      assertFalse(landMaskSample.land());
      assertEquals(400, sourceSample.terrainHeight());
      assertTrue(sourceSample.openWatersSelected());
   }

   @Test
   void visuallyBlendsSoftCoverClassesWithoutChangingRawPackageData() {
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 1, 1.0, 0, 0, 0, 0);
      TerrainPreloadPackage pack = new TerrainPreloadPackage(
         "visual-transition",
         "fingerprint",
         area,
         16,
         2,
         2,
         new int[]{64, 64, 64, 64},
         new byte[]{10, 60, 10, 60},
         new byte[]{1, 1, 1, 1},
         new byte[4]
      );

      Set<Integer> visualClasses = new HashSet<>();
      for (int z = 0; z < 16; z++) {
         TerrainPreloadPackage.Sample sample = pack.sample(8, z);
         assertEquals(60, sample.coverClass());
         visualClasses.add(sample.visualCoverClass());
      }

      assertEquals(Set.of(10, 60), visualClasses);
   }

   @Test
   void packageVisualTransitionDoesNotMoveWaterBoundaries() {
      TerrainPreloadArea area = TerrainPreloadArea.fromChunkBounds(0.0, 0.0, 1, 1.0, 0, 0, 0, 0);
      TerrainPreloadPackage pack = new TerrainPreloadPackage(
         "water-transition",
         "fingerprint",
         area,
         16,
         2,
         2,
         new int[]{64, 64, 64, 64},
         new byte[]{10, 80, 10, 80},
         new byte[]{1, 0, 1, 0},
         new byte[4]
      );

      for (int z = 0; z < 16; z++) {
         TerrainPreloadPackage.Sample sample = pack.sample(8, z);
         assertEquals(80, sample.coverClass());
         assertEquals(80, sample.visualCoverClass());
      }
   }

   private void writeHeader(Path path, int gridStep, int gridWidth, int gridDepth) throws Exception {
      try (
         DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))
         )
      ) {
         output.writeInt(TerrainPreloadPackage.MAGIC);
         output.writeInt(TerrainPreloadPackage.FORMAT_VERSION);
         output.writeUTF("test-package");
         output.writeUTF("settings");
         output.writeDouble(0.0);
         output.writeDouble(0.0);
         output.writeInt(1);
         output.writeDouble(1.0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeInt(0);
         output.writeDouble(1.0);
         output.writeDouble(-1.0);
         output.writeDouble(-1.0);
         output.writeDouble(1.0);
         output.writeInt(gridStep);
         output.writeInt(gridWidth);
         output.writeInt(gridDepth);
      }
   }
}
