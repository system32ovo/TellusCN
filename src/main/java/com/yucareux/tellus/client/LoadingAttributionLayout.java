package com.yucareux.tellus.client;

import java.util.List;
import java.util.Optional;

/**
 * Places wrapped loading-screen attributions in the free vertical regions around
 * Minecraft's loading widget.
 */
public final class LoadingAttributionLayout {
   private static final float EPSILON = 1.0E-4F;

   private LoadingAttributionLayout() {
   }

   public static Optional<LoadingAttributionLayout.Layout> arrange(
      List<Integer> groupLineCounts,
      float lineHeight,
      float lineSpacing,
      float topStartY,
      float topEndY,
      float bottomStartY,
      float bottomEndY,
      boolean keepGroupsTogether
   ) {
      if (lineHeight <= 0.0F || lineSpacing < 0.0F) {
         throw new IllegalArgumentException("Line height must be positive and line spacing must not be negative");
      }

      int totalLines = 0;
      for (int lineCount : groupLineCounts) {
         if (lineCount < 0) {
            throw new IllegalArgumentException("Group line counts must not be negative");
         }
         totalLines += lineCount;
      }
      if (totalLines == 0) {
         return Optional.of(new LoadingAttributionLayout.Layout(List.of()));
      }

      int topCapacity = lineCapacity(topEndY - topStartY, lineHeight, lineSpacing);
      int bottomCapacity = lineCapacity(bottomEndY - bottomStartY, lineHeight, lineSpacing);
      float totalHeight = blockHeight(totalLines, lineHeight, lineSpacing);
      if (bottomCapacity >= totalLines) {
         return Optional.of(
            new LoadingAttributionLayout.Layout(
               List.of(new LoadingAttributionLayout.Block(0, totalLines, bottomStartY))
            )
         );
      }
      if (topCapacity >= totalLines) {
         return Optional.of(
            new LoadingAttributionLayout.Layout(
               List.of(new LoadingAttributionLayout.Block(0, totalLines, topEndY - totalHeight))
            )
         );
      }

      int splitLine = keepGroupsTogether
         ? groupedSplit(groupLineCounts, totalLines, topCapacity, bottomCapacity)
         : lineSplit(totalLines, topCapacity, bottomCapacity);
      if (splitLine <= 0 || splitLine >= totalLines) {
         return Optional.empty();
      }

      float topHeight = blockHeight(splitLine, lineHeight, lineSpacing);
      return Optional.of(
         new LoadingAttributionLayout.Layout(
            List.of(
               new LoadingAttributionLayout.Block(0, splitLine, topEndY - topHeight),
               new LoadingAttributionLayout.Block(splitLine, totalLines - splitLine, bottomStartY)
            )
         )
      );
   }

   private static int groupedSplit(
      List<Integer> groupLineCounts, int totalLines, int topCapacity, int bottomCapacity
   ) {
      int cumulativeLines = 0;
      int bestSplit = -1;
      float target = proportionalTarget(totalLines, topCapacity, bottomCapacity);
      float bestDistance = Float.POSITIVE_INFINITY;

      for (int groupIndex = 0; groupIndex < groupLineCounts.size() - 1; groupIndex++) {
         cumulativeLines += groupLineCounts.get(groupIndex);
         if (cumulativeLines <= 0
            || cumulativeLines > topCapacity
            || totalLines - cumulativeLines > bottomCapacity) {
            continue;
         }

         float distance = Math.abs(cumulativeLines - target);
         if (distance + EPSILON < bestDistance) {
            bestDistance = distance;
            bestSplit = cumulativeLines;
         }
      }
      return bestSplit;
   }

   private static int lineSplit(int totalLines, int topCapacity, int bottomCapacity) {
      int minimumTopLines = Math.max(1, totalLines - bottomCapacity);
      int maximumTopLines = Math.min(totalLines - 1, topCapacity);
      if (minimumTopLines > maximumTopLines) {
         return -1;
      }

      int preferred = Math.round(proportionalTarget(totalLines, topCapacity, bottomCapacity));
      return Math.max(minimumTopLines, Math.min(maximumTopLines, preferred));
   }

   private static float proportionalTarget(int totalLines, int topCapacity, int bottomCapacity) {
      int totalCapacity = topCapacity + bottomCapacity;
      return totalCapacity == 0 ? 0.0F : totalLines * (topCapacity / (float)totalCapacity);
   }

   private static int lineCapacity(float availableHeight, float lineHeight, float lineSpacing) {
      if (availableHeight + EPSILON < lineHeight) {
         return 0;
      }
      return 1 + (int)Math.floor((availableHeight - lineHeight + EPSILON) / (lineHeight + lineSpacing));
   }

   private static float blockHeight(int lineCount, float lineHeight, float lineSpacing) {
      return lineCount <= 0 ? 0.0F : lineCount * lineHeight + (lineCount - 1) * lineSpacing;
   }

   public record Block(int firstLine, int lineCount, float y) {
      public Block {
         if (firstLine < 0 || lineCount <= 0) {
            throw new IllegalArgumentException("A block must contain at least one line");
         }
      }
   }

   public record Layout(List<LoadingAttributionLayout.Block> blocks) {
      public Layout {
         blocks = List.copyOf(blocks);
      }
   }
}
