package com.yucareux.tellus.worldgen;

import com.yucareux.tellus.world.data.mask.TellusLandMaskSource;

final class OceanClassification {
   private static final int ESA_NO_DATA = 0;
   private static final int ESA_WATER = 80;

   private OceanClassification() {
   }

   static boolean isOcean(
      boolean oceanHint,
      TellusLandMaskSource.LandMaskSample landMaskSample,
      int surface,
      int coverClass,
      int seaLevel
   ) {
      return isOcean(oceanHint, false, landMaskSample, surface, coverClass, seaLevel);
   }

   static boolean isOcean(
      boolean oceanHint,
      boolean strictOverture,
      TellusLandMaskSource.LandMaskSample landMaskSample,
      int surface,
      int coverClass,
      int seaLevel
   ) {
      return isOcean(oceanHint, strictOverture, landMaskSample, surface, coverClass, seaLevel, false);
   }

   static boolean isOcean(
      boolean oceanHint,
      boolean strictOverture,
      TellusLandMaskSource.LandMaskSample landMaskSample,
      int surface,
      int coverClass,
      int seaLevel,
      boolean mapterhornLandOverride
   ) {
      if (strictOverture) {
         return oceanHint;
      } else if (oceanHint) {
         return true;
      } else if (landMaskSample != null && landMaskSample.known()) {
         return !landMaskSample.land() && (surface <= seaLevel || coverClass == ESA_NO_DATA || coverClass == ESA_WATER);
      } else {
         return coverClass == ESA_NO_DATA && surface <= seaLevel;
      }
   }
}
