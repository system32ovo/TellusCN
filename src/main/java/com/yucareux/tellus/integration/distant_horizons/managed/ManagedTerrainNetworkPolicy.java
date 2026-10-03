package com.yucareux.tellus.integration.distant_horizons.managed;

public final class ManagedTerrainNetworkPolicy {
   private static final ThreadLocal<Integer> CACHE_ONLY_DEPTH = ThreadLocal.withInitial(() -> 0);

   private ManagedTerrainNetworkPolicy() {
   }

   public static Scope cacheOnly() {
      CACHE_ONLY_DEPTH.set(CACHE_ONLY_DEPTH.get() + 1);
      return new Scope();
   }

   public static boolean isCacheOnly() {
      return CACHE_ONLY_DEPTH.get() > 0;
   }

   public static final class Scope implements AutoCloseable {
      private boolean closed;

      private Scope() {
      }

      @Override
      public void close() {
         if (!this.closed) {
            this.closed = true;
            int next = Math.max(0, CACHE_ONLY_DEPTH.get() - 1);
            if (next == 0) {
               CACHE_ONLY_DEPTH.remove();
            } else {
               CACHE_ONLY_DEPTH.set(next);
            }
         }
      }
   }
}
