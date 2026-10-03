package com.yucareux.tellus.integration.distant_horizons;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class DistantHorizonsStartupGate {
   private final Object monitor = new Object();
   private final AtomicBoolean ready = new AtomicBoolean(false);
   private CompletableFuture<Void> readyFuture = new CompletableFuture<>();
   private long generation;
   private int waitingCount;

   boolean isReady() {
      return this.ready.get();
   }

   boolean release() {
      CompletableFuture<Void> completedFuture = null;
      boolean changed;
      synchronized (this.monitor) {
         changed = this.ready.compareAndSet(false, true);
         if (changed) {
            completedFuture = this.readyFuture;
            this.monitor.notifyAll();
         }
      }
      // CompletableFuture may run dependent work inline. Never run that work
      // while holding the startup-gate monitor.
      if (completedFuture != null) {
         completedFuture.complete(null);
      }
      return changed;
   }

   void reset() {
      CompletableFuture<Void> previous;
      synchronized (this.monitor) {
         previous = this.readyFuture;
         this.generation++;
         this.ready.set(false);
         this.readyFuture = new CompletableFuture<>();
         this.monitor.notifyAll();
      }
      previous.completeExceptionally(new CancellationException("Distant Horizons startup gate reset"));
   }

   CompletableFuture<Void> whenReady() {
      synchronized (this.monitor) {
         return this.readyFuture;
      }
   }

   /**
    * Waits for the current server's initial player position. A reset cancels
    * waiters from the previous server so DH worker threads cannot leak across
    * integrated-server restarts.
    *
    * @return {@code true} when released, or {@code false} when reset first
    */
   boolean awaitReady() throws InterruptedException {
      synchronized (this.monitor) {
         long observedGeneration = this.generation;
         this.waitingCount++;
         try {
            while (!this.ready.get() && observedGeneration == this.generation) {
               this.monitor.wait();
            }
            return this.ready.get() && observedGeneration == this.generation;
         } finally {
            this.waitingCount--;
         }
      }
   }

   int waitingCount() {
      synchronized (this.monitor) {
         return this.waitingCount;
      }
   }
}
