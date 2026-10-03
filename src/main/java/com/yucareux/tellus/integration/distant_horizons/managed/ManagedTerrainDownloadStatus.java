package com.yucareux.tellus.integration.distant_horizons.managed;

public record ManagedTerrainDownloadStatus(
   Stage stage,
   int completedCells,
   int totalCells,
   int activeCells,
   int failedCells,
   int degradedCells,
   long bytesRead,
   long bytesExpected,
   int renderRadiusChunks,
   int safetyRingChunks,
   String detail
) {
   public enum Stage {
      DISABLED,
      COMPATIBILITY_FALLBACK,
      PLANNING,
      DOWNLOADING,
      COMPLETE,
      DEGRADED,
      FAILED
   }

   public double progress() {
      if (this.totalCells <= 0) {
         return 0.0;
      }
      double activeProgress = 0.0;
      if (this.activeCells > 0 && this.bytesExpected > 0L) {
         double byteProgress = Math.max(0.0, Math.min(1.0, this.bytesRead / (double)this.bytesExpected));
         activeProgress = this.activeCells * byteProgress;
      }
      return Math.max(0.0, Math.min(1.0, (this.completedCells + activeProgress) / this.totalCells));
   }

   public boolean shouldRemainVisible() {
      return this.stage == Stage.DOWNLOADING
         || this.stage == Stage.PLANNING
         || this.stage == Stage.DEGRADED
         || this.stage == Stage.FAILED
         || this.stage == Stage.COMPATIBILITY_FALLBACK;
   }
}
