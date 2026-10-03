package com.yucareux.tellus.integration.distant_horizons;

/** Pure vertical-layout helpers for keeping one LOD tree crown on one ground plane. */
final class LodCanopyVerticalLayout {
   static final int UNANCHORED_SURFACE_Y = Integer.MIN_VALUE;

   private LodCanopyVerticalLayout() {
   }

   static int anchorLayerTop(
      int anchorSurfaceY,
      int minY,
      int absoluteTop,
      int fallbackLayerTop
   ) {
      if (anchorSurfaceY == UNANCHORED_SURFACE_Y) {
         return clamp(fallbackLayerTop, 0, absoluteTop);
      }

      long layerTop = (long)anchorSurfaceY - minY + 1L;
      return (int)Math.max(0L, Math.min(absoluteTop, layerTop));
   }

   static Span visibleSpan(
      int emittedLayerTop,
      int anchorLayerTop,
      int relativeBottom,
      int height,
      int absoluteTop
   ) {
      if (height <= 0 || emittedLayerTop >= absoluteTop) {
         return Span.EMPTY;
      }

      long requestedBottom = (long)anchorLayerTop + relativeBottom;
      long requestedTop = requestedBottom + height;
      int bottom = (int)Math.max(emittedLayerTop, Math.max(0L, Math.min(absoluteTop, requestedBottom)));
      int top = (int)Math.max(0L, Math.min(absoluteTop, requestedTop));
      return top > bottom ? new Span(bottom, top) : Span.EMPTY;
   }

   private static int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   record Span(int bottom, int top) {
      private static final Span EMPTY = new Span(0, 0);

      boolean visible() {
         return this.top > this.bottom;
      }
   }
}
