package com.yucareux.tellus.world.data.elevation;

import com.yucareux.tellus.world.data.elevation.TellusElevationSource.DemUsage;
import java.util.Objects;

record NormalizedElevationTileSample(
   double elevationMeters,
   DemUsage primaryProvider,
   int providerMask,
   double resolutionMeters,
   boolean mapterhornAvailable,
   double mapterhornElevationMeters
) {
   NormalizedElevationTileSample {
      Objects.requireNonNull(primaryProvider, "primaryProvider");
   }
}
