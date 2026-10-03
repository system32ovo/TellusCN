package com.yucareux.tellus.world.data.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs preload-only network requests with a conservative global concurrency cap. */
public final class ParallelDownloadRunner {
   private static final int THREADS = intProperty("tellus.preload.downloadThreads", 8, 1, 32);
   private static final int MAX_IN_FLIGHT = intProperty("tellus.preload.downloadInFlight", THREADS * 2, THREADS, 64);
   private static final int RECENT_DOWNLOAD_MAX = intProperty("tellus.preload.downloadDedupEntries", 16384, 0, 262144);
   private static final long RECENT_DOWNLOAD_RETENTION_NANOS = TimeUnit.SECONDS.toNanos(
      intProperty("tellus.preload.downloadDedupSeconds", 120, 0, 3600)
   );
   private static final ConcurrentHashMap<DownloadKey, CompletableFuture<Void>> IN_FLIGHT = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<DownloadKey, Long> RECENTLY_COMPLETED = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<CompletedDownload> COMPLETION_ORDER = new ConcurrentLinkedQueue<>();
   private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(THREADS, new ThreadFactory() {
      private final AtomicInteger index = new AtomicInteger();

      @Override
      public Thread newThread(Runnable runnable) {
         Thread thread = new Thread(runnable, "tellus-preload-download-" + this.index.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      }
   });

   private ParallelDownloadRunner() {
   }

   public static <T> int run(
      Collection<T> requestedItems,
      int completedUnits,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      ParallelDownloadRunner.ProgressListener<T> progress
   ) {
      return run(null, requestedItems, completedUnits, downloader, progress);
   }

   /**
    * Runs downloads while sharing identical source items across overlapping preload requests.
    * The scope must include every cache generation that can invalidate the requested items.
    */
   public static <T> int run(
      DownloadScope scope,
      Collection<T> requestedItems,
      int completedUnits,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      ParallelDownloadRunner.ProgressListener<T> progress
   ) {
      Objects.requireNonNull(requestedItems, "requestedItems");
      Objects.requireNonNull(downloader, "downloader");
      Objects.requireNonNull(progress, "progress");
      LinkedHashSet<T> items = new LinkedHashSet<>(requestedItems);
      if (items.isEmpty()) {
         return completedUnits;
      }

      DownloadProgressReporter.Listener reporter = DownloadProgressReporter.currentListener();
      ExecutorCompletionService<T> completion = new ExecutorCompletionService<>(EXECUTOR);
      List<Future<T>> futures = new ArrayList<>(Math.min(items.size(), MAX_IN_FLIGHT));
      List<T> pendingItems = new ArrayList<>(items.size());
      int finished = 0;
      long now = System.nanoTime();
      if (scope != null) {
         cleanupRecent(now);
         for (T item : items) {
            if (isRecentlyCompleted(new DownloadKey(scope, item), now)) {
               finished++;
               progress.completed(item, completedUnits + finished, items.size());
            } else {
               pendingItems.add(item);
            }
         }
      } else {
         pendingItems.addAll(items);
      }

      Iterator<T> pending = pendingItems.iterator();
      try {
         while (pending.hasNext() && futures.size() < MAX_IN_FLIGHT) {
            futures.add(submit(completion, scope, pending.next(), downloader, reporter));
         }

         int settled = finished;
         Throwable firstFailure = null;
         while (settled < items.size()) {
            if (Thread.currentThread().isInterrupted()) {
               throw new InterruptedException("Terrain preload interrupted");
            }

            Future<T> completedFuture = completion.take();
            futures.remove(completedFuture);
            settled++;
            try {
               T item = completedFuture.get();
               finished++;
               progress.completed(item, completedUnits + finished, items.size());
            } catch (ExecutionException error) {
               Throwable cause = error.getCause();
               if (cause instanceof Error fatal) {
                  cancel(futures);
                  throw fatal;
               }
               if (firstFailure == null) {
                  firstFailure = cause;
               }
            }
            if (pending.hasNext()) {
               futures.add(submit(completion, scope, pending.next(), downloader, reporter));
            }
         }

         if (firstFailure instanceof RuntimeException runtime) {
            throw runtime;
         }
         if (firstFailure != null) {
            throw new RuntimeException("Terrain download failed", firstFailure);
         }
         return completedUnits + finished;
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         cancel(futures);
         throw new CancellationException("Terrain preload interrupted");
      } catch (RuntimeException | Error error) {
         cancel(futures);
         throw error;
      }
   }

   private static <T> Future<T> submit(
      ExecutorCompletionService<T> completion,
      DownloadScope scope,
      T item,
      ParallelDownloadRunner.CheckedConsumer<T> downloader,
      DownloadProgressReporter.Listener reporter
   ) {
      return completion.submit(() -> {
         ParallelDownloadRunner.CheckedConsumer<T> reportedDownloader = requestedItem -> {
            if (reporter == null) {
               downloader.accept(requestedItem);
            } else {
               DownloadProgressReporter.Scope progressScope = DownloadProgressReporter.push(reporter);
               try (progressScope) {
                  downloader.accept(requestedItem);
               }
            }
         };
         if (scope == null) {
            reportedDownloader.accept(item);
         } else {
            downloadOnce(new DownloadKey(scope, item), item, reportedDownloader);
         }
         return item;
      });
   }

   private static <T> void downloadOnce(
      DownloadKey key, T item, ParallelDownloadRunner.CheckedConsumer<T> downloader
   ) throws Exception {
      long now = System.nanoTime();
      if (isRecentlyCompleted(key, now)) {
         return;
      }

      CompletableFuture<Void> owned = new CompletableFuture<>();
      CompletableFuture<Void> existing = IN_FLIGHT.putIfAbsent(key, owned);
      if (existing != null) {
         awaitSharedDownload(existing);
         return;
      }

      try {
         // Close the small race between the first recent-cache check and acquiring ownership.
         now = System.nanoTime();
         if (!isRecentlyCompleted(key, now)) {
            downloader.accept(item);
            rememberCompleted(key, System.nanoTime());
         }
         owned.complete(null);
      } catch (Exception | Error error) {
         owned.completeExceptionally(error);
         throw error;
      } finally {
         IN_FLIGHT.remove(key, owned);
      }
   }

   private static void awaitSharedDownload(CompletableFuture<Void> future) throws Exception {
      try {
         future.get();
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         throw error;
      } catch (ExecutionException error) {
         Throwable cause = error.getCause();
         if (cause instanceof Exception exception) {
            throw exception;
         }
         if (cause instanceof Error fatal) {
            throw fatal;
         }
         throw new RuntimeException("Shared terrain download failed", cause);
      }
   }

   private static boolean isRecentlyCompleted(DownloadKey key, long now) {
      if (RECENT_DOWNLOAD_MAX <= 0 || RECENT_DOWNLOAD_RETENTION_NANOS <= 0L) {
         return false;
      }
      Long completedAt = RECENTLY_COMPLETED.get(key);
      if (completedAt == null) {
         return false;
      }
      if (now - completedAt <= RECENT_DOWNLOAD_RETENTION_NANOS) {
         return true;
      }
      RECENTLY_COMPLETED.remove(key, completedAt);
      return false;
   }

   private static void rememberCompleted(DownloadKey key, long completedAt) {
      if (RECENT_DOWNLOAD_MAX <= 0 || RECENT_DOWNLOAD_RETENTION_NANOS <= 0L) {
         return;
      }
      RECENTLY_COMPLETED.put(key, completedAt);
      COMPLETION_ORDER.add(new CompletedDownload(key, completedAt));
      cleanupRecent(completedAt);
   }

   private static void cleanupRecent(long now) {
      while (true) {
         CompletedDownload oldest = COMPLETION_ORDER.peek();
         if (oldest == null) {
            return;
         }
         boolean expired = now - oldest.completedAt() > RECENT_DOWNLOAD_RETENTION_NANOS;
         if (!expired && RECENTLY_COMPLETED.size() <= RECENT_DOWNLOAD_MAX) {
            return;
         }
         COMPLETION_ORDER.poll();
         RECENTLY_COMPLETED.remove(oldest.key(), oldest.completedAt());
      }
   }

   private static void cancel(List<? extends Future<?>> futures) {
      for (Future<?> future : futures) {
         future.cancel(true);
      }
   }

   private static int intProperty(String key, int defaultValue, int min, int max) {
      String value = System.getProperty(key);
      if (value == null) {
         return Math.max(min, Math.min(max, defaultValue));
      }
      try {
         return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
      } catch (NumberFormatException ignored) {
         return Math.max(min, Math.min(max, defaultValue));
      }
   }

   public static DownloadScope scope(String source, long... cacheGenerations) {
      Objects.requireNonNull(source, "source");
      List<Long> generations = Arrays.stream(cacheGenerations).boxed().toList();
      return new DownloadScope(source, generations);
   }

   public record DownloadScope(String source, List<Long> cacheGenerations) {
      public DownloadScope {
         Objects.requireNonNull(source, "source");
         cacheGenerations = List.copyOf(cacheGenerations);
      }
   }

   private record DownloadKey(DownloadScope scope, Object item) {
      private DownloadKey {
         Objects.requireNonNull(scope, "scope");
         Objects.requireNonNull(item, "item");
      }
   }

   private record CompletedDownload(DownloadKey key, long completedAt) {
   }

   @FunctionalInterface
   public interface CheckedConsumer<T> {
      void accept(T item) throws Exception;
   }

   @FunctionalInterface
   public interface ProgressListener<T> {
      void completed(T item, int completedUnits, int phaseTotal);
   }
}
