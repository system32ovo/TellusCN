package com.yucareux.tellus.worldgen;

import java.util.ArrayList;
import java.util.List;

public final class RoadCrossingLayout {
   private static final int INTERSECTION_RUN_THRESHOLD = 9;

   private RoadCrossingLayout() {
   }

   public static Anchor findAnchor(
      int localX,
      int localZ,
      byte[] roadClass,
      byte[] roadMode,
      byte excludedMode,
      int side,
      int scanRadius
   ) {
      int minX = Math.max(0, localX - scanRadius);
      int maxX = Math.min(side - 1, localX + scanRadius);
      int minZ = Math.max(0, localZ - scanRadius);
      int maxZ = Math.min(side - 1, localZ + scanRadius);
      Anchor best = null;
      int bestScore = Integer.MIN_VALUE;
      int bestDistanceSq = Integer.MAX_VALUE;
      int bestCenterOffset = Integer.MAX_VALUE;
      int bestDominance = Integer.MIN_VALUE;

      for (int z = minZ; z <= maxZ; z++) {
         for (int x = minX; x <= maxX; x++) {
            int index = index(x, z, side);
            if (!isRoadCell(index, roadClass, roadMode, excludedMode)) {
               continue;
            }

            int horizontalRun = roadRunLength(
               x, z, 1, 0, roadClass, roadMode, excludedMode, side, side
            );
            int verticalRun = roadRunLength(
               x, z, 0, 1, roadClass, roadMode, excludedMode, side, side
            );
            boolean roadHorizontal = horizontalRun >= verticalRun;
            int axisRun = roadHorizontal ? horizontalRun : verticalRun;
            int perpRun = roadHorizontal ? verticalRun : horizontalRun;
            int dominance = Math.abs(horizontalRun - verticalRun);
            int perpStepX = roadHorizontal ? 0 : 1;
            int perpStepZ = roadHorizontal ? 1 : 0;
            int negative = roadRunLengthOneSide(
               x, z, -perpStepX, -perpStepZ, roadClass, roadMode, excludedMode, side, side
            );
            int positive = roadRunLengthOneSide(
               x, z, perpStepX, perpStepZ, roadClass, roadMode, excludedMode, side, side
            );
            int centerOffset = Math.abs(negative - positive);
            int centerX = x + perpStepX * ((positive - negative) / 2);
            int centerZ = z + perpStepZ * ((positive - negative) / 2);
            int dx = centerX - localX;
            int dz = centerZ - localZ;
            int distanceSq = dx * dx + dz * dz;
            int score = dominance * 16 + axisRun * 4 + perpRun - centerOffset * 8 - distanceSq * 32;
            if (horizontalRun >= INTERSECTION_RUN_THRESHOLD && verticalRun >= INTERSECTION_RUN_THRESHOLD) {
               score -= 128;
            }

            if (score > bestScore
               || score == bestScore
                  && (distanceSq < bestDistanceSq
                     || distanceSq == bestDistanceSq
                        && (centerOffset < bestCenterOffset || centerOffset == bestCenterOffset && dominance > bestDominance))) {
               bestScore = score;
               bestDistanceSq = distanceSq;
               bestCenterOffset = centerOffset;
               bestDominance = dominance;
               best = new Anchor(centerX, centerZ, roadHorizontal);
            }
         }
      }

      return best;
   }

   public static List<Cell> markedCells(
      Anchor anchor,
      byte[] roadClass,
      byte[] roadMode,
      byte excludedMode,
      int side,
      int stripeHalfSpan,
      int barHalfLength
   ) {
      int alongStepX = anchor.roadHorizontal() ? 1 : 0;
      int alongStepZ = anchor.roadHorizontal() ? 0 : 1;
      int stripeStepX = anchor.roadHorizontal() ? 0 : 1;
      int stripeStepZ = anchor.roadHorizontal() ? 1 : 0;
      List<Cell> cells = new ArrayList<>();

      for (int stripe = -stripeHalfSpan; stripe <= stripeHalfSpan; stripe++) {
         if (Math.floorMod(stripe + stripeHalfSpan, 2) != 0) {
            continue;
         }

         int stripeX = anchor.localX() + stripeStepX * stripe;
         int stripeZ = anchor.localZ() + stripeStepZ * stripe;
         if (!inBounds(stripeX, stripeZ, side)) {
            continue;
         }

         int stripeIndex = index(stripeX, stripeZ, side);
         if (!isRoadCell(stripeIndex, roadClass, roadMode, excludedMode)) {
            continue;
         }

         for (int along = -barHalfLength; along <= barHalfLength; along++) {
            int targetX = stripeX + alongStepX * along;
            int targetZ = stripeZ + alongStepZ * along;
            if (!inBounds(targetX, targetZ, side)) {
               continue;
            }

            int targetIndex = index(targetX, targetZ, side);
            if (isRoadCell(targetIndex, roadClass, roadMode, excludedMode)) {
               cells.add(new Cell(targetX, targetZ, targetIndex));
            }
         }
      }

      return cells;
   }

   private static int roadRunLength(
      int localX,
      int localZ,
      int stepX,
      int stepZ,
      byte[] roadClass,
      byte[] roadMode,
      byte excludedMode,
      int side,
      int maxOffset
   ) {
      int length = 1;
      length += roadRunLengthOneSide(localX, localZ, stepX, stepZ, roadClass, roadMode, excludedMode, side, maxOffset);
      length += roadRunLengthOneSide(localX, localZ, -stepX, -stepZ, roadClass, roadMode, excludedMode, side, maxOffset);
      return length;
   }

   private static int roadRunLengthOneSide(
      int localX,
      int localZ,
      int stepX,
      int stepZ,
      byte[] roadClass,
      byte[] roadMode,
      byte excludedMode,
      int side,
      int maxOffset
   ) {
      int startIndex = index(localX, localZ, side);
      byte startClass = roadClass[startIndex];
      byte startMode = roadMode[startIndex];
      int length = 0;
      for (int offset = 1; offset <= maxOffset; offset++) {
         int x = localX + stepX * offset;
         int z = localZ + stepZ * offset;
         if (!inBounds(x, z, side)) {
            break;
         }

         int index = index(x, z, side);
         if (roadClass[index] != startClass || roadMode[index] != startMode || !isRoadCell(index, roadClass, roadMode, excludedMode)) {
            break;
         }

         length++;
      }

      return length;
   }

   private static boolean isRoadCell(int index, byte[] roadClass, byte[] roadMode, byte excludedMode) {
      return roadClass[index] > 0 && roadMode[index] != excludedMode;
   }

   private static boolean inBounds(int localX, int localZ, int side) {
      return localX >= 0 && localX < side && localZ >= 0 && localZ < side;
   }

   private static int index(int localX, int localZ, int side) {
      return localZ * side + localX;
   }

   public record Anchor(int localX, int localZ, boolean roadHorizontal) {
   }

   public record Cell(int localX, int localZ, int index) {
   }
}
