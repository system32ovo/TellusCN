package com.yucareux.tellus.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TellusCacheManagerTest {
   @TempDir
   Path gameDirectory;

   @AfterEach
   void clearGameDirectoryOverride() {
      System.clearProperty("tellus.gameDir");
   }

   @Test
   void calculatesUsageAndDeletesSelectedCache() throws Exception {
      System.setProperty("tellus.gameDir", this.gameDirectory.toString());
      Path cacheRoot = this.gameDirectory.resolve("tellus/cache/map");
      Files.createDirectories(cacheRoot.resolve("nested"));
      Files.write(cacheRoot.resolve("tile-a.bin"), new byte[]{1, 2, 3});
      Files.write(cacheRoot.resolve("nested/tile-b.bin"), new byte[]{4, 5, 6, 7, 8});

      TellusCacheManager.requestRefresh();
      TellusCacheManager.Snapshot measured = awaitReady();
      assertEquals(8L, measured.bytesFor(TellusCacheManager.Metric.OSM));
      assertEquals(2L, measured.filesFor(TellusCacheManager.Metric.OSM));

      TellusCacheManager.delete(TellusCacheManager.Metric.OSM);
      TellusCacheManager.Snapshot deleted = awaitReady();
      assertEquals(0L, deleted.bytesFor(TellusCacheManager.Metric.OSM));
      assertEquals(0L, deleted.filesFor(TellusCacheManager.Metric.OSM));
      assertFalse(Files.exists(cacheRoot));
   }

   @Test
   void cancellingDeletionLeavesUntouchedFilesInPlace() throws Exception {
      System.setProperty("tellus.gameDir", this.gameDirectory.toString());
      Path cacheRoot = this.gameDirectory.resolve("tellus/cache/map");
      Files.createDirectories(cacheRoot);
      for (int index = 0; index < 2_000; index++) {
         Files.write(cacheRoot.resolve("tile-" + index + ".bin"), new byte[]{1});
      }

      TellusCacheManager.requestRefresh();
      assertEquals(2_000L, awaitReady().filesFor(TellusCacheManager.Metric.OSM));

      TellusCacheManager.delete(TellusCacheManager.Metric.OSM);
      TellusCacheManager.cancelDeletion();
      TellusCacheManager.Snapshot cancelled = awaitReady();
      long remainingFiles = regularFileCount(cacheRoot);
      assertTrue(remainingFiles > 0L, "Cancellation should leave files that deletion had not reached");
      assertEquals(remainingFiles, cancelled.filesFor(TellusCacheManager.Metric.OSM));
   }

   private static TellusCacheManager.Snapshot awaitReady() throws InterruptedException {
      Instant deadline = Instant.now().plus(Duration.ofSeconds(15L));
      while (Instant.now().isBefore(deadline)) {
         TellusCacheManager.Snapshot snapshot = TellusCacheManager.snapshot();
         if (snapshot.ready()) {
            return snapshot;
         }
         Thread.sleep(10L);
      }

      TellusCacheManager.Snapshot snapshot = TellusCacheManager.snapshot();
      throw new AssertionError("Cache operation did not finish; last status was " + snapshot.status());
   }

   private static long regularFileCount(Path root) throws IOException {
      if (!Files.exists(root)) {
         return 0L;
      }
      try (Stream<Path> files = Files.walk(root)) {
         return files.filter(Files::isRegularFile).count();
      }
   }
}
