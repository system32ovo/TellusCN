package com.yucareux.tellus.world.data.osm;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellusOsmWaterCoverageTest {
   @Test
   void validEmptyCoverageIsComplete() {
      TellusOsmWaterSource.WaterQueryResult result = new TellusOsmWaterSource.WaterQueryResult(
         List.of(), TellusOsmWaterSource.CoverageStatus.COMPLETE, 8
      );

      assertTrue(result.features().isEmpty());
      assertTrue(result.complete());
      assertFalse(result.hadCacheMiss());
   }

   @Test
   void pendingAndFailedCoverageAreNeverComplete() {
      TellusOsmWaterSource.WaterQueryResult pending = new TellusOsmWaterSource.WaterQueryResult(
         List.of(), TellusOsmWaterSource.CoverageStatus.PENDING, 8
      );
      TellusOsmWaterSource.WaterQueryResult failed = new TellusOsmWaterSource.WaterQueryResult(
         List.of(), TellusOsmWaterSource.CoverageStatus.FAILED, 8
      );

      assertFalse(pending.complete());
      assertTrue(pending.hadCacheMiss());
      assertFalse(failed.complete());
      assertFalse(failed.hadCacheMiss());
   }

   @Test
   void sourceRetryBackoffUsesRequestedSchedule() {
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(1) == 1L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(2) == 2L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(3) == 4L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(4) == 8L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(5) == 16L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(6) == 60L);
      assertTrue(TellusOsmWaterSource.retryDelaySeconds(20) == 60L);
   }
}
