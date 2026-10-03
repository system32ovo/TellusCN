package com.yucareux.tellus.integration.distant_horizons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DistantHorizonsStartupGateTest {
   @Test
   void remainsClosedUntilReleasedAndCanBeResetForTheNextServer() {
      DistantHorizonsStartupGate gate = new DistantHorizonsStartupGate();

      assertFalse(gate.isReady());
      assertTrue(gate.release());
      assertTrue(gate.isReady());
      assertFalse(gate.release());

      gate.reset();

      assertFalse(gate.isReady());
   }

   @Test
   void legacyWorkerWaitsForRelease() throws Exception {
      DistantHorizonsStartupGate gate = new DistantHorizonsStartupGate();
      CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(() -> await(gate));

      awaitWaiter(gate);
      assertTrue(gate.release());
      assertTrue(waiter.get(1, TimeUnit.SECONDS));
   }

   @Test
   void resetCancelsWaitersFromPreviousServer() throws Exception {
      DistantHorizonsStartupGate gate = new DistantHorizonsStartupGate();
      CompletableFuture<Boolean> waiter = CompletableFuture.supplyAsync(() -> await(gate));

      awaitWaiter(gate);
      gate.reset();
      assertFalse(waiter.get(1, TimeUnit.SECONDS));
      assertFalse(gate.isReady());
   }

   @Test
   void readinessFutureCompletesWithoutOccupyingAWorkerThread() {
      DistantHorizonsStartupGate gate = new DistantHorizonsStartupGate();
      CompletableFuture<Void> firstServer = gate.whenReady();

      assertFalse(firstServer.isDone());
      assertTrue(gate.release());
      firstServer.join();

      gate.reset();
      CompletableFuture<Void> secondServer = gate.whenReady();
      assertFalse(secondServer.isDone());
      assertTrue(gate.release());
      secondServer.join();
   }

   @Test
   void resetRejectsAStaleReadinessFuture() {
      DistantHorizonsStartupGate gate = new DistantHorizonsStartupGate();
      CompletableFuture<Void> stale = gate.whenReady();

      gate.reset();

      assertThrows(CancellationException.class, stale::join);
      assertFalse(gate.whenReady().isDone());
   }

   private static boolean await(DistantHorizonsStartupGate gate) {
      try {
         return gate.awaitReady();
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private static void awaitWaiter(DistantHorizonsStartupGate gate) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
      while (gate.waitingCount() == 0 && System.nanoTime() < deadline) {
         Thread.onSpinWait();
      }
      assertTrue(gate.waitingCount() > 0);
   }
}
