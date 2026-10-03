package com.yucareux.tellus.integration.distant_horizons.managed;

public final class ManagedTerrainClientState {
   private static final long COMPLETE_VISIBILITY_MILLIS = 4_000L;
   private static volatile ManagedTerrainDownloadStatus status;
   private static volatile long updatedAtMillis;

   private ManagedTerrainClientState() {
   }

   public static void update(ManagedTerrainDownloadStatus newStatus) {
      ManagedTerrainDownloadStatus previous = status;
      status = newStatus;
      boolean repeatedComplete = previous != null
         && previous.stage() == ManagedTerrainDownloadStatus.Stage.COMPLETE
         && previous.equals(newStatus);
      if (!repeatedComplete) {
         updatedAtMillis = System.currentTimeMillis();
      }
   }

   public static ManagedTerrainDownloadStatus visibleStatus() {
      ManagedTerrainDownloadStatus current = status;
      if (current == null) {
         return null;
      }
      if (current.stage() == ManagedTerrainDownloadStatus.Stage.DISABLED) {
         return null;
      }
      if (current.stage() == ManagedTerrainDownloadStatus.Stage.COMPLETE
         && System.currentTimeMillis() - updatedAtMillis > COMPLETE_VISIBILITY_MILLIS) {
         return null;
      }
      return current;
   }

   public static void reset() {
      status = null;
      updatedAtMillis = 0L;
   }
}
