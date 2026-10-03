package com.yucareux.tellus.integration.distant_horizons.managed;

public final class ManagedTerrainCompatibility {
   private static volatile boolean distantHorizonsPresent;
   private static volatile boolean generationGateAvailable;

   private ManagedTerrainCompatibility() {
   }

   public static void setDistantHorizonsCompatibility(boolean present, boolean gateAvailable) {
      distantHorizonsPresent = present;
      generationGateAvailable = present && gateAvailable;
   }

   public static boolean isDistantHorizonsPresent() {
      return distantHorizonsPresent;
   }

   public static boolean isGenerationGateAvailable() {
      return generationGateAvailable;
   }
}
