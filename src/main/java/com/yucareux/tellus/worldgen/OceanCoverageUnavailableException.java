package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.osm.TellusOsmWaterSource;

/** Signals that a coastline-dependent result must not be cached yet. */
public final class OceanCoverageUnavailableException extends RuntimeException {
   private final TellusOsmWaterSource.CoverageStatus coverageStatus;

   public OceanCoverageUnavailableException(
      TellusOsmWaterSource.CoverageStatus coverageStatus, int blockX, int blockZ
   ) {
      super("Overture ocean coverage " + coverageStatus + " near " + blockX + ":" + blockZ);
      this.coverageStatus = coverageStatus;
   }

   public TellusOsmWaterSource.CoverageStatus coverageStatus() {
      return this.coverageStatus;
   }
}
