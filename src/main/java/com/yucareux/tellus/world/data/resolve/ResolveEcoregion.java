package com.yucareux.tellus.world.data.resolve;

import java.util.Objects;

/** Immutable RESOLVE metadata reused by every lookup of the same ecoregion. */
public record ResolveEcoregion(
   int ecoId,
   String name,
   ResolveBiome biome,
   String biomeName,
   ResolveRealm realm,
   String realmName,
   String license
) {
   public static final ResolveEcoregion UNKNOWN = new ResolveEcoregion(
      -1,
      "Unknown",
      ResolveBiome.UNKNOWN,
      "Unknown",
      ResolveRealm.UNKNOWN,
      "Unknown",
      ""
   );

   public ResolveEcoregion {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(biome, "biome");
      Objects.requireNonNull(biomeName, "biomeName");
      Objects.requireNonNull(realm, "realm");
      Objects.requireNonNull(realmName, "realmName");
      Objects.requireNonNull(license, "license");
   }

   public boolean available() {
      return this.ecoId >= 0 && this.biome != ResolveBiome.UNKNOWN;
   }
}
