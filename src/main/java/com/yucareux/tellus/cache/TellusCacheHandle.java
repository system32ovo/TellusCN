package com.yucareux.tellus.cache;

public interface TellusCacheHandle {
   TellusCacheDomain cacheDomain();

   default boolean matchesCacheDomain(TellusCacheDomain domain) {
      return this.cacheDomain() == domain;
   }

   void clearCache();
}
