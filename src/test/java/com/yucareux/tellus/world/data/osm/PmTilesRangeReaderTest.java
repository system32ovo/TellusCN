package com.yucareux.tellus.world.data.osm;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yucareux.tellus.integration.distant_horizons.managed.ManagedTerrainNetworkPolicy;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class PmTilesRangeReaderTest {
   @Test
   void sharesArchiveMetadataForMatchingSourceConfiguration() {
      PmTilesRangeReader first = PmTilesRangeReader.shared("https://example.test/base.pmtiles", 30_000, 60_000, 256);
      PmTilesRangeReader second = PmTilesRangeReader.shared("https://example.test/base.pmtiles", 30_000, 60_000, 256);
      PmTilesRangeReader normalized = PmTilesRangeReader.shared("https://example.test/a/../base.pmtiles", 30_000, 60_000, 256);

      assertSame(first, second);
      assertSame(first, normalized);
   }

   @Test
   void keepsDifferentTimeoutPoliciesIsolated() {
      PmTilesRangeReader longTimeout = PmTilesRangeReader.shared("https://example.test/base.pmtiles", 30_000, 60_000, 256);
      PmTilesRangeReader shortTimeout = PmTilesRangeReader.shared("https://example.test/base.pmtiles", 7_000, 20_000, 256);

      assertNotSame(longTimeout, shortTimeout);
   }

   @Test
   void cacheOnlyGenerationRejectsNetworkRanges() {
      PmTilesRangeReader reader = new PmTilesRangeReader("https://example.test/never-open.pmtiles", 1, 1, 1);
      ManagedTerrainNetworkPolicy.Scope networkScope = ManagedTerrainNetworkPolicy.cacheOnly();
      try (networkScope) {
         assertThrows(IOException.class, reader::header);
      }
   }

   @Test
   void rejectsNonHttpArchiveUrls() {
      assertThrows(
         IllegalArgumentException.class,
         () -> new PmTilesRangeReader("file:///tmp/archive.pmtiles", 1000, 1000, 1)
      );
   }
}
