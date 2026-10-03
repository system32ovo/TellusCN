package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.osm.TellusOsmWaterSource;

/** Compact coastline-field value shared by full terrain and DH. */
public record OceanCoastSample(
   boolean ocean,
   int coastDistance,
   boolean correctionRequired,
   TellusOsmWaterSource.CoverageStatus coverageStatus
) {
   public boolean complete() {
      return this.coverageStatus == TellusOsmWaterSource.CoverageStatus.COMPLETE;
   }
}
