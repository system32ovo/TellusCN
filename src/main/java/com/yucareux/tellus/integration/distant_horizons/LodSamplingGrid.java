package com.yucareux.tellus.integration.distant_horizons;

/** Pure sizing helpers for detail-aware coarse LOD sampling. */
final class LodSamplingGrid {
   /** Tellus and Distant Horizons both cap their supported render radius at 4096 chunks. */
   static final int MAX_RENDER_DISTANCE_CHUNKS = 4096;
   /**
    * The coarsest data detail DH can normally request at the 4096-chunk boundary.
    *
    * <p>DH's lowest horizontal-quality preset uses a 64-block distance unit and a
    * base-2 falloff. The view diameter is 8192 chunks, so detail 11 is the
    * conservative boundary. Higher quality presets request detail 5-9 there.
    * Keeping every output column through detail 11 prevents Tellus from applying
    * a second distance reduction on top of DH's own 2^detail column sizing.</p>
    */
   static final int FULL_QUALITY_MAX_DETAIL = 11;

   private LodSamplingGrid() {
   }

   static int terrainStrideForDetail(int detailLevel, int maxStride, int outputWidth) {
      return strideForDetail(detailLevel, FULL_QUALITY_MAX_DETAIL + 1, maxStride, outputWidth);
   }

   static int strideForDetail(int detailLevel, int minDetailLevel, int maxStride, int outputWidth) {
      if (outputWidth <= 1 || maxStride <= 1 || detailLevel < minDetailLevel) {
         return 1;
      }

      int detailSteps = Math.min(30, detailLevel - minDetailLevel + 1);
      int requestedStride = 1 << detailSteps;
      int stride = Integer.highestOneBit(Math.min(requestedStride, Math.min(maxStride, outputWidth)));
      while (stride > 1 && outputWidth % stride != 0) {
         stride >>= 1;
      }
      return Math.max(1, stride);
   }

   static int sampleWidth(int outputWidth, int stride) {
      if (outputWidth <= 0) {
         throw new IllegalArgumentException("Output width must be positive.");
      }
      if (stride <= 0 || outputWidth % stride != 0) {
         throw new IllegalArgumentException("Sampling stride must evenly divide the output width.");
      }
      return outputWidth / stride;
   }
}
