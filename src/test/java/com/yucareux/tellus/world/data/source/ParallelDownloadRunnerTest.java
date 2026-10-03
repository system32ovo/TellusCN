package com.yucareux.tellus.world.data.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ParallelDownloadRunnerTest {
   @Test
   void deduplicatesRunsInParallelAndPropagatesProgressReporter() {
      AtomicInteger calls = new AtomicInteger();
      AtomicInteger active = new AtomicInteger();
      AtomicInteger maxActive = new AtomicInteger();
      AtomicInteger requestsStarted = new AtomicInteger();
      AtomicInteger bytesRead = new AtomicInteger();
      AtomicInteger requestsFinished = new AtomicInteger();
      AtomicInteger progressUpdates = new AtomicInteger();
      CountDownLatch twoWorkersStarted = new CountDownLatch(2);

      DownloadProgressReporter.Listener reporter = new DownloadProgressReporter.Listener() {
         @Override
         public void onRequestStarted(long expectedBytes) {
            requestsStarted.incrementAndGet();
         }

         @Override
         public void onBytesRead(int bytes) {
            bytesRead.addAndGet(bytes);
         }

         @Override
         public void onRequestFinished() {
            requestsFinished.incrementAndGet();
         }
      };

      int completed;
      DownloadProgressReporter.Scope reporterScope = DownloadProgressReporter.push(reporter);
      try (reporterScope) {
         completed = ParallelDownloadRunner.run(
            List.of(1, 2, 2, 3),
            10,
            item -> {
               calls.incrementAndGet();
               int current = active.incrementAndGet();
               maxActive.accumulateAndGet(current, Math::max);
               twoWorkersStarted.countDown();
               assertTrue(twoWorkersStarted.await(2, TimeUnit.SECONDS));
               DownloadProgressReporter.requestStarted(4L);
               DownloadProgressReporter.bytesRead(4);
               DownloadProgressReporter.requestFinished();
               active.decrementAndGet();
            },
            (item, count, total) -> progressUpdates.incrementAndGet()
         );
      }

      assertEquals(13, completed);
      assertEquals(3, calls.get());
      assertTrue(maxActive.get() >= 2);
      assertEquals(3, requestsStarted.get());
      assertEquals(12, bytesRead.get());
      assertEquals(3, requestsFinished.get());
      assertEquals(3, progressUpdates.get());
   }

   @Test
   void sharesInFlightAndRecentlyCompletedDownloadsWithinScope() throws Exception {
      ParallelDownloadRunner.DownloadScope scope = ParallelDownloadRunner.scope("shared-" + System.nanoTime(), 7L);
      AtomicInteger calls = new AtomicInteger();
      CountDownLatch downloadStarted = new CountDownLatch(1);
      CountDownLatch releaseDownload = new CountDownLatch(1);
      ExecutorService callers = Executors.newFixedThreadPool(2);

      try {
         Future<Integer> first = callers.submit(() -> ParallelDownloadRunner.run(scope, List.of("tile"), 0, item -> {
            calls.incrementAndGet();
            downloadStarted.countDown();
            assertTrue(releaseDownload.await(2, TimeUnit.SECONDS));
         }, (item, completed, total) -> {
         }));
         assertTrue(downloadStarted.await(2, TimeUnit.SECONDS));
         Future<Integer> overlapping = callers.submit(() -> ParallelDownloadRunner.run(scope, List.of("tile"), 0, item -> {
            calls.incrementAndGet();
         }, (item, completed, total) -> {
         }));

         releaseDownload.countDown();
         assertEquals(1, first.get(2, TimeUnit.SECONDS));
         assertEquals(1, overlapping.get(2, TimeUnit.SECONDS));
         assertEquals(1, calls.get());

         int repeated = ParallelDownloadRunner.run(scope, List.of("tile"), 0, item -> calls.incrementAndGet(), (item, completed, total) -> {
         });
         assertEquals(1, repeated);
         assertEquals(1, calls.get());
      } finally {
         releaseDownload.countDown();
         callers.shutdownNow();
      }
   }

   @Test
   void cacheGenerationChangeForcesFreshDownload() {
      String source = "generation-" + System.nanoTime();
      AtomicInteger calls = new AtomicInteger();
      ParallelDownloadRunner.run(
         ParallelDownloadRunner.scope(source, 1L), List.of("tile"), 0, item -> calls.incrementAndGet(), (item, completed, total) -> {
         }
      );
      ParallelDownloadRunner.run(
         ParallelDownloadRunner.scope(source, 2L), List.of("tile"), 0, item -> calls.incrementAndGet(), (item, completed, total) -> {
         }
      );
      assertEquals(2, calls.get());
   }

   @Test
   void decodedMemoryWarmupIsNotSuppressedByRecentRawDownload() {
      String source = "raw-memory-" + System.nanoTime();
      AtomicInteger calls = new AtomicInteger();
      ParallelDownloadRunner.run(
         ParallelDownloadRunner.scope(source, 1L), List.of("tile"), 0, item -> calls.incrementAndGet(), (item, completed, total) -> {
         }
      );
      ParallelDownloadRunner.run(
         ParallelDownloadRunner.scope(source + "-memory", 1L),
         List.of("tile"),
         0,
         item -> calls.incrementAndGet(),
         (item, completed, total) -> {
         }
      );
      assertEquals(2, calls.get());
   }

   @Test
   void failedDownloadIsNotRemembered() {
      ParallelDownloadRunner.DownloadScope scope = ParallelDownloadRunner.scope("failure-" + System.nanoTime(), 0L);
      AtomicInteger calls = new AtomicInteger();
      assertThrows(RuntimeException.class, () -> ParallelDownloadRunner.run(scope, List.of("tile"), 0, item -> {
         calls.incrementAndGet();
         throw new RuntimeException("expected failure");
      }, (item, completed, total) -> {
      }));

      int completed = ParallelDownloadRunner.run(scope, List.of("tile"), 0, item -> calls.incrementAndGet(), (item, count, total) -> {
      });
      assertEquals(1, completed);
      assertEquals(2, calls.get());
   }

   @Test
   void drainsSuccessfulSiblingsBeforeReportingOneFailedItem() {
      ParallelDownloadRunner.DownloadScope scope = ParallelDownloadRunner.scope("drain-failure-" + System.nanoTime(), 0L);
      AtomicInteger firstCalls = new AtomicInteger();
      AtomicInteger failedCalls = new AtomicInteger();
      AtomicInteger lastCalls = new AtomicInteger();

      assertThrows(RuntimeException.class, () -> ParallelDownloadRunner.run(scope, List.of(1, 2, 3), 0, item -> {
         switch (item) {
            case 1 -> firstCalls.incrementAndGet();
            case 2 -> {
               failedCalls.incrementAndGet();
               throw new RuntimeException("expected isolated failure");
            }
            case 3 -> lastCalls.incrementAndGet();
            default -> throw new AssertionError("Unexpected item " + item);
         }
      }, (item, completed, total) -> {
      }));

      assertEquals(1, firstCalls.get());
      assertEquals(1, failedCalls.get());
      assertEquals(1, lastCalls.get());

      int completed = ParallelDownloadRunner.run(scope, List.of(1, 2, 3), 0, item -> {
         if (item == 2) {
            failedCalls.incrementAndGet();
         } else {
            throw new AssertionError("Successful sibling was downloaded again: " + item);
         }
      }, (item, count, total) -> {
      });
      assertEquals(3, completed);
      assertEquals(2, failedCalls.get());
   }
}
