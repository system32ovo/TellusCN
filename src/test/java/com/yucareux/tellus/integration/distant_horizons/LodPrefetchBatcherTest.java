package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class LodPrefetchBatcherTest {
   @Test
   void unionsOnlyAdjacentCompatibleRequests() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      CompletableFuture<Void> sourceReady = new CompletableFuture<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> {
         started.add(request);
         return sourceReady;
      })) {
         LodPrefetchBatcher.Submission first = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         LodPrefetchBatcher.Submission east = batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 2));
         LodPrefetchBatcher.Submission south = batcher.submit(request(new LodBlockRange(0, 64, 63, 127), 2));

         batcher.flushNow();

         assertEquals(1, started.size());
         assertEquals(new LodBlockRange(0, 0, 127, 127), started.get(0).range());
         assertEquals(3, first.batchSize());
         assertEquals(3, east.batchSize());
         assertEquals(3, south.batchSize());
         assertFalse(first.future().isDone());

         sourceReady.complete(null);
         first.future().join();
         east.future().join();
         south.future().join();
      }
   }

   @Test
   void mergesExistingClustersWhenALaterRequestBridgesThem() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> {
         started.add(request);
         return CompletableFuture.completedFuture(null);
      })) {
         LodPrefetchBatcher.Submission west = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         LodPrefetchBatcher.Submission east = batcher.submit(request(new LodBlockRange(128, 0, 191, 63), 2));
         LodPrefetchBatcher.Submission bridge = batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 2));

         batcher.flushNow();

         assertEquals(1, started.size());
         assertEquals(new LodBlockRange(0, 0, 191, 63), started.get(0).range());
         assertEquals(3, west.batchSize());
         assertEquals(3, east.batchSize());
         assertEquals(3, bridge.batchSize());
      }
   }

   @Test
   void keepsNonAdjacentAndIncompatibleRequestsSeparate() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> {
         started.add(request);
         return CompletableFuture.completedFuture(null);
      })) {
         batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         batcher.submit(request(new LodBlockRange(256, 0, 319, 63), 2));
         batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 3));

         batcher.flushNow();

         assertEquals(3, started.size());
      }
   }

   @Test
   void doesNotBatchRequestsThatOnlyTouchAtOneCorner() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> {
         started.add(request);
         return CompletableFuture.completedFuture(null);
      })) {
         batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         batcher.submit(request(new LodBlockRange(64, 64, 127, 127), 2));

         batcher.flushNow();

         assertEquals(2, started.size());
      }
   }

   @Test
   void immediateSubmissionBypassesCollectionWindow() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> {
         started.add(request);
         return CompletableFuture.completedFuture(null);
      })) {
         LodPrefetchBatcher.Submission submission = batcher.submitImmediately(
            request(new LodBlockRange(0, 0, 63, 63), 2)
         );

         submission.future().join();
         assertEquals(1, started.size());
         assertEquals(1, submission.batchSize());
      }
   }

   @Test
   void honorsMaximumBatchSize() {
      List<LodPrefetchBatcher.Request> started = new ArrayList<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 2, request -> {
         started.add(request);
         return CompletableFuture.completedFuture(null);
      })) {
         LodPrefetchBatcher.Submission first = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         LodPrefetchBatcher.Submission second = batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 2));
         LodPrefetchBatcher.Submission third = batcher.submit(request(new LodBlockRange(128, 0, 191, 63), 2));

         batcher.flushNow();

         assertEquals(2, started.size());
         assertEquals(2, first.batchSize());
         assertEquals(2, second.batchSize());
         assertEquals(1, third.batchSize());
      }
   }

   @Test
   void propagatesSharedFailureToEveryRequest() {
      CompletableFuture<Void> failed = new CompletableFuture<>();
      failed.completeExceptionally(new IllegalStateException("expected"));
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> failed)) {
         LodPrefetchBatcher.Submission first = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         LodPrefetchBatcher.Submission second = batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 2));

         batcher.flushNow();

         assertThrows(CompletionException.class, () -> first.future().join());
         assertThrows(CompletionException.class, () -> second.future().join());
      }
   }

   @Test
   void cancelsSharedPrefetchOnlyAfterEveryBatchedRequestIsCancelled() {
      CompletableFuture<Void> sourceReady = new CompletableFuture<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> sourceReady)) {
         LodPrefetchBatcher.Submission first = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));
         LodPrefetchBatcher.Submission second = batcher.submit(request(new LodBlockRange(64, 0, 127, 63), 2));

         batcher.flushNow();

         first.future().cancel(true);
         assertFalse(sourceReady.isCancelled());

         second.future().cancel(true);
         assertTrue(sourceReady.isCancelled());
      }
   }

   @Test
   void cancellingImmediateSubmissionCancelsItsPrefetch() {
      CompletableFuture<Void> sourceReady = new CompletableFuture<>();
      try (LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> sourceReady)) {
         LodPrefetchBatcher.Submission submission = batcher.submitImmediately(
            request(new LodBlockRange(0, 0, 63, 63), 2)
         );

         submission.future().cancel(true);

         assertTrue(sourceReady.isCancelled());
      }
   }

   @Test
   void closeCancelsRequestsStillInCollectionWindow() {
      LodPrefetchBatcher batcher = new LodPrefetchBatcher(60_000L, 4, request -> CompletableFuture.completedFuture(null));
      LodPrefetchBatcher.Submission submission = batcher.submit(request(new LodBlockRange(0, 0, 63, 63), 2));

      batcher.close();

      assertTrue(submission.future().isCompletedExceptionally());
   }

   private static LodPrefetchBatcher.Request request(LodBlockRange range, int detail) {
      return new LodPrefetchBatcher.Request(range, detail, true, true, Math.scalb(1.0, detail));
   }
}
