package com.yucareux.tellus.preload;

public record TerrainPreloadProgress(
   String jobId,
   TerrainPreloadStage stage,
   String status,
   String detail,
   String sourceDetail,
   int completedUnits,
   int totalUnits,
   long bytesRead,
   long bytesExpected,
   long startedAtMillis,
   int activeWorkers,
   boolean paused,
   boolean cancellable,
   String error
) {
   public static TerrainPreloadProgress idle() {
      return new TerrainPreloadProgress("", TerrainPreloadStage.IDLE, "Idle", "", "", 0, 0, 0L, -1L, System.currentTimeMillis(), 0, false, false, null);
   }

   public double unitProgress() {
      return this.totalUnits <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, this.completedUnits / (double)this.totalUnits));
   }

   public long elapsedMillis() {
      return Math.max(0L, System.currentTimeMillis() - this.startedAtMillis);
   }

   public long estimatedRemainingMillis() {
      if (this.completedUnits > 0 && this.totalUnits > this.completedUnits) {
         double unitsPerMillis = this.completedUnits / (double)Math.max(1L, this.elapsedMillis());
         return unitsPerMillis <= 0.0 ? -1L : Math.round((this.totalUnits - this.completedUnits) / unitsPerMillis);
      }

      if (this.totalUnits <= 0 && this.bytesExpected > this.bytesRead && this.bytesRead > 0L) {
         double bytesPerMillis = this.bytesRead / (double)Math.max(1L, this.elapsedMillis());
         return bytesPerMillis <= 0.0 ? -1L : Math.round((this.bytesExpected - this.bytesRead) / bytesPerMillis);
      }
      return -1L;
   }

   public double unitsPerSecond() {
      return this.completedUnits <= 0 ? 0.0 : this.completedUnits * 1000.0 / Math.max(1L, this.elapsedMillis());
   }

   public double bytesPerSecond() {
      return this.bytesRead <= 0L ? 0.0 : this.bytesRead * 1000.0 / Math.max(1L, this.elapsedMillis());
   }
}
