package com.yucareux.tellus.cache;

import com.yucareux.tellus.Tellus;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class TellusCacheRegistry {
   private static final CopyOnWriteArrayList<WeakReference<TellusCacheHandle>> HANDLES = new CopyOnWriteArrayList<>();
   private static final AtomicLong[] GENERATIONS = createGenerations();

   private TellusCacheRegistry() {
   }

   public static void register(TellusCacheHandle handle) {
      if (handle == null) {
         return;
      }

      boolean alreadyRegistered = false;
      List<WeakReference<TellusCacheHandle>> staleReferences = null;
      for (WeakReference<TellusCacheHandle> reference : HANDLES) {
         TellusCacheHandle existing = reference.get();
         if (existing == null) {
            if (staleReferences == null) {
               staleReferences = new ArrayList<>();
            }

            staleReferences.add(reference);
         } else if (existing == handle) {
            alreadyRegistered = true;
         }
      }

      removeStaleReferences(staleReferences);
      if (!alreadyRegistered) {
         HANDLES.add(new WeakReference<>(handle));
      }
   }

   public static void clear(TellusCacheDomain domain) {
      if (domain == null) {
         return;
      }

      clearMatching(domain);
   }

   public static void clearAll() {
      clearMatching(null);
   }

   public static long generation(TellusCacheDomain domain) {
      return GENERATIONS[domain.ordinal()].get();
   }

   public static boolean isCurrent(TellusCacheDomain domain, long generation) {
      return generation(domain) == generation;
   }

   private static void clearMatching(TellusCacheDomain domain) {
      advanceGeneration(domain);
      Set<TellusCacheHandle> cleared = Collections.newSetFromMap(new IdentityHashMap<>());
      List<WeakReference<TellusCacheHandle>> staleReferences = null;

      for (WeakReference<TellusCacheHandle> reference : HANDLES) {
         TellusCacheHandle handle = reference.get();
         if (handle == null) {
            if (staleReferences == null) {
               staleReferences = new ArrayList<>();
            }

            staleReferences.add(reference);
         } else if ((domain == null || handle.matchesCacheDomain(domain)) && cleared.add(handle)) {
            try {
               handle.clearCache();
            } catch (RuntimeException error) {
               Tellus.LOGGER.warn("Failed to clear {} runtime cache", domain == null ? "all" : domain, error);
            }
         }
      }

      removeStaleReferences(staleReferences);
   }

   private static void removeStaleReferences(List<WeakReference<TellusCacheHandle>> staleReferences) {
      if (staleReferences != null && !staleReferences.isEmpty()) {
         HANDLES.removeAll(staleReferences);
      }
   }

   private static AtomicLong[] createGenerations() {
      TellusCacheDomain[] domains = TellusCacheDomain.values();
      AtomicLong[] generations = new AtomicLong[domains.length];
      for (TellusCacheDomain domain : domains) {
         generations[domain.ordinal()] = new AtomicLong();
      }

      return generations;
   }

   private static void advanceGeneration(TellusCacheDomain domain) {
      if (domain == null) {
         for (AtomicLong generation : GENERATIONS) {
            generation.incrementAndGet();
         }
      } else {
         GENERATIONS[domain.ordinal()].incrementAndGet();
      }
   }
}
