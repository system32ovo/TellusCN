package com.yucareux.tellus.world.data.elevation;

import java.util.Arrays;

/** Repairs the rare preview-only case where one or more elevation tiles remain unavailable after retrying. */
public final class ElevationGridRepair {
   private ElevationGridRepair() {
   }

   public static boolean repairMissing(double[] elevations, boolean[] missing, int width, int height) {
      if (elevations == null || missing == null || width <= 0 || height <= 0) {
         throw new IllegalArgumentException("Invalid elevation grid");
      }
      int size = width * height;
      if (elevations.length != size || missing.length != size) {
         throw new IllegalArgumentException("Elevation grid dimensions do not match its storage");
      }

      boolean anyMissing = false;
      boolean anyValid = false;
      for (int index = 0; index < size; index++) {
         if (missing[index] || !Double.isFinite(elevations[index])) {
            missing[index] = true;
            anyMissing = true;
         } else {
            anyValid = true;
         }
      }
      if (!anyMissing) {
         return true;
      }
      if (!anyValid) {
         return false;
      }

      boolean[] unresolved = Arrays.copyOf(missing, size);
      for (int z = 0; z < height; z++) {
         for (int x = 0; x < width; x++) {
            int index = x + z * width;
            if (!unresolved[index]) {
               continue;
            }

            double weightedElevation = 0.0;
            double totalWeight = 0.0;
            for (int left = x - 1; left >= 0; left--) {
               int candidate = left + z * width;
               if (!missing[candidate]) {
                  double weight = 1.0 / (x - left);
                  weightedElevation += elevations[candidate] * weight;
                  totalWeight += weight;
                  break;
               }
            }
            for (int right = x + 1; right < width; right++) {
               int candidate = right + z * width;
               if (!missing[candidate]) {
                  double weight = 1.0 / (right - x);
                  weightedElevation += elevations[candidate] * weight;
                  totalWeight += weight;
                  break;
               }
            }
            for (int up = z - 1; up >= 0; up--) {
               int candidate = x + up * width;
               if (!missing[candidate]) {
                  double weight = 1.0 / (z - up);
                  weightedElevation += elevations[candidate] * weight;
                  totalWeight += weight;
                  break;
               }
            }
            for (int down = z + 1; down < height; down++) {
               int candidate = x + down * width;
               if (!missing[candidate]) {
                  double weight = 1.0 / (down - z);
                  weightedElevation += elevations[candidate] * weight;
                  totalWeight += weight;
                  break;
               }
            }
            if (totalWeight > 0.0) {
               elevations[index] = weightedElevation / totalWeight;
               unresolved[index] = false;
            }
         }
      }

      // Handles unusual diagonal-only coverage without introducing a zero-height patch.
      double[] next = Arrays.copyOf(elevations, size);
      while (true) {
         int repaired = 0;
         for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
               int index = x + z * width;
               if (!unresolved[index]) {
                  continue;
               }
               double sum = 0.0;
               int count = 0;
               for (int dz = -1; dz <= 1; dz++) {
                  int neighborZ = z + dz;
                  if (neighborZ < 0 || neighborZ >= height) {
                     continue;
                  }
                  for (int dx = -1; dx <= 1; dx++) {
                     int neighborX = x + dx;
                     if ((dx == 0 && dz == 0) || neighborX < 0 || neighborX >= width) {
                        continue;
                     }
                     int neighbor = neighborX + neighborZ * width;
                     if (!unresolved[neighbor]) {
                        sum += elevations[neighbor];
                        count++;
                     }
                  }
               }
               if (count > 0) {
                  next[index] = sum / count;
                  repaired++;
               }
            }
         }
         if (repaired == 0) {
            break;
         }
         for (int index = 0; index < size; index++) {
            if (unresolved[index] && Double.isFinite(next[index])) {
               elevations[index] = next[index];
               unresolved[index] = false;
            }
         }
      }

      for (int index = 0; index < size; index++) {
         if (unresolved[index]) {
            return false;
         }
         missing[index] = false;
      }
      return true;
   }
}
