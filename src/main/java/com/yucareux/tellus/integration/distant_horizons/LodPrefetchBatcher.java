package com.yucareux.tellus.integration.distant_horizons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Collects a short burst of adjacent DH requests into one source-prefetch area. */
final class LodPrefetchBatcher implements AutoCloseable {
   private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "tellus-dh-prefetch-batcher");
      thread.setDaemon(true);
      return thread;
   });

   private final Object lock = new Object();
   private final long collectionWindowMillis;
   private final int maxRequestsPerBatch;
   private final PrefetchStarter starter;
   private final Map<GroupKey, List<PendingRequest>> pendingByGroup = new HashMap<>();
   private ScheduledFuture<?> scheduledFlush;
   private boolean closed;

   LodPrefetchBatcher(long collectionWindowMillis, int maxRequestsPerBatch, PrefetchStarter starter) {
      this.collectionWindowMillis = Math.max(0L, collectionWindowMillis);
      this.maxRequestsPerBatch = Math.max(1, maxRequestsPerBatch);
      this.starter = Objects.requireNonNull(starter, "starter");
   }

   Submission submit(Request request) {
      Objects.requireNonNull(request, "request");
      if (this.collectionWindowMillis == 0L || this.maxRequestsPerBatch == 1) {
         return this.submitImmediately(request);
      }

      PendingRequest pending = new PendingRequest(request);
      synchronized (this.lock) {
         if (this.closed) {
            pending.submission.future.completeExceptionally(new CancellationException("LOD prefetch batcher is closed"));
            return pending.submission;
         }
         this.pendingByGroup.computeIfAbsent(GroupKey.forRequest(request), ignored -> new ArrayList<>()).add(pending);
         if (this.scheduledFlush == null) {
            this.scheduledFlush = SCHEDULER.schedule(this::flushNow, this.collectionWindowMillis, TimeUnit.MILLISECONDS);
         }
      }
      return pending.submission;
   }

   Submission submitImmediately(Request request) {
      Objects.requireNonNull(request, "request");
      synchronized (this.lock) {
         if (this.closed) {
            Submission submission = new Submission();
            submission.future.completeExceptionally(new CancellationException("LOD prefetch batcher is closed"));
            return submission;
         }
      }
      return directSubmission(request, this.starter);
   }

   static Submission completedSubmission() {
      Submission submission = new Submission();
      submission.future.complete(null);
      return submission;
   }

   void flushNow() {
      Map<GroupKey, List<PendingRequest>> pending;
      synchronized (this.lock) {
         if (this.pendingByGroup.isEmpty()) {
            this.scheduledFlush = null;
            return;
         }
         pending = new HashMap<>(this.pendingByGroup);
         this.pendingByGroup.clear();
         ScheduledFuture<?> flush = this.scheduledFlush;
         this.scheduledFlush = null;
         if (flush != null && !flush.isDone()) {
            flush.cancel(false);
         }
      }

      for (List<PendingRequest> compatibleRequests : pending.values()) {
         this.startClusters(compatibleRequests);
      }
   }

   private void startClusters(List<PendingRequest> compatibleRequests) {
      List<Cluster> clusters = new ArrayList<>();
      for (PendingRequest pending : compatibleRequests) {
         if (pending.submission.future.isCancelled()) {
            continue;
         }
         Cluster target = null;
         for (Cluster cluster : clusters) {
            if (cluster.requests.size() < this.maxRequestsPerBatch && touchesOrOverlaps(cluster.range, pending.request.range())) {
               target = cluster;
               break;
            }
         }
         if (target == null) {
            target = new Cluster(pending.request.range());
            clusters.add(target);
         }
         target.add(pending);

         boolean merged;
         do {
            merged = false;
            for (int i = clusters.size() - 1; i >= 0; i--) {
               Cluster other = clusters.get(i);
               if (other != target
                  && target.requests.size() + other.requests.size() <= this.maxRequestsPerBatch
                  && touchesOrOverlaps(target.range, other.range)) {
                  target.absorb(other);
                  clusters.remove(i);
                  merged = true;
                  break;
               }
            }
         } while (merged);
      }

      for (Cluster cluster : clusters) {
         Request batchedRequest = cluster.requests.get(0).request.withRange(cluster.range);
         CompletableFuture<Void> sharedFuture;
         try {
            sharedFuture = Objects.requireNonNull(this.starter.start(batchedRequest), "prefetch future");
         } catch (Throwable error) {
            sharedFuture = failedFuture(error);
         }
         CompletableFuture<Void> clusterFuture = sharedFuture;

         int batchSize = cluster.requests.size();
         AtomicInteger cancelledRequests = new AtomicInteger();
         for (PendingRequest pending : cluster.requests) {
            pending.submission.batchSize = batchSize;
            pending.submission.future.whenComplete((ignored, error) -> {
               if (pending.submission.future.isCancelled() && cancelledRequests.incrementAndGet() == batchSize) {
                  clusterFuture.cancel(true);
               }
            });
         }
         clusterFuture.whenComplete((ignored, error) -> {
            for (PendingRequest pending : cluster.requests) {
               if (error == null) {
                  pending.submission.future.complete(null);
               } else {
                  pending.submission.future.completeExceptionally(error);
               }
            }
         });
      }
   }

   @Override
   public void close() {
      List<PendingRequest> pending = new ArrayList<>();
      synchronized (this.lock) {
         if (this.closed) {
            return;
         }
         this.closed = true;
         if (this.scheduledFlush != null) {
            this.scheduledFlush.cancel(false);
            this.scheduledFlush = null;
         }
         for (List<PendingRequest> group : this.pendingByGroup.values()) {
            pending.addAll(group);
         }
         this.pendingByGroup.clear();
      }
      CancellationException cancellation = new CancellationException("LOD prefetch batcher closed");
      for (PendingRequest request : pending) {
         request.submission.future.completeExceptionally(cancellation);
      }
   }

   private static Submission directSubmission(Request request, PrefetchStarter starter) {
      Submission submission = new Submission();
      CompletableFuture<Void> future;
      try {
         future = Objects.requireNonNull(starter.start(request), "prefetch future");
      } catch (Throwable error) {
         future = failedFuture(error);
      }
      CompletableFuture<Void> sourceFuture = future;
      submission.future.whenComplete((ignored, error) -> {
         if (submission.future.isCancelled()) {
            sourceFuture.cancel(true);
         }
      });
      sourceFuture.whenComplete((ignored, error) -> {
         if (error == null) {
            submission.future.complete(null);
         } else {
            submission.future.completeExceptionally(error);
         }
      });
      return submission;
   }

   private static boolean touchesOrOverlaps(LodBlockRange a, LodBlockRange b) {
      boolean xTouches = (long)a.minX() <= (long)b.maxX() + 1L
         && (long)b.minX() <= (long)a.maxX() + 1L;
      boolean zTouches = (long)a.minZ() <= (long)b.maxZ() + 1L
         && (long)b.minZ() <= (long)a.maxZ() + 1L;
      boolean xOverlaps = a.minX() <= b.maxX() && b.minX() <= a.maxX();
      boolean zOverlaps = a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
      return xTouches && zTouches && (xOverlaps || zOverlaps);
   }

   private static LodBlockRange union(LodBlockRange a, LodBlockRange b) {
      return new LodBlockRange(
         Math.min(a.minX(), b.minX()),
         Math.min(a.minZ(), b.minZ()),
         Math.max(a.maxX(), b.maxX()),
         Math.max(a.maxZ(), b.maxZ())
      );
   }

   private static <T> CompletableFuture<T> failedFuture(Throwable error) {
      CompletableFuture<T> future = new CompletableFuture<>();
      future.completeExceptionally(error);
      return future;
   }

   record Request(
      LodBlockRange range,
      int detailLevel,
      boolean includeRoadsPrefetch,
      boolean includeBuildingsPrefetch,
      double previewResolutionMeters
   ) {
      Request {
         Objects.requireNonNull(range, "range");
      }

      Request withRange(LodBlockRange newRange) {
         return new Request(
            newRange,
            this.detailLevel,
            this.includeRoadsPrefetch,
            this.includeBuildingsPrefetch,
            this.previewResolutionMeters
         );
      }
   }

   static final class Submission {
      private final CompletableFuture<Void> future = new CompletableFuture<>();
      private volatile int batchSize = 1;

      CompletableFuture<Void> future() {
         return this.future;
      }

      int batchSize() {
         return this.batchSize;
      }
   }

   @FunctionalInterface
   interface PrefetchStarter {
      CompletableFuture<Void> start(Request request);
   }

   private record GroupKey(
      int detailLevel,
      boolean includeRoadsPrefetch,
      boolean includeBuildingsPrefetch,
      long previewResolutionBits
   ) {
      static GroupKey forRequest(Request request) {
         return new GroupKey(
            request.detailLevel(),
            request.includeRoadsPrefetch(),
            request.includeBuildingsPrefetch(),
            Double.doubleToLongBits(request.previewResolutionMeters())
         );
      }
   }

   private static final class PendingRequest {
      private final Request request;
      private final Submission submission = new Submission();

      private PendingRequest(Request request) {
         this.request = request;
      }
   }

   private static final class Cluster {
      private LodBlockRange range;
      private final List<PendingRequest> requests = new ArrayList<>();

      private Cluster(LodBlockRange range) {
         this.range = range;
      }

      private void add(PendingRequest request) {
         this.requests.add(request);
         this.range = union(this.range, request.request.range());
      }

      private void absorb(Cluster other) {
         this.requests.addAll(other.requests);
         this.range = union(this.range, other.range);
      }
   }
}
