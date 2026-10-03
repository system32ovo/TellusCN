package com.yucareux.tellus.worldgen;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

public final class ScanlinePolygonRasterizer {
   private ScanlinePolygonRasterizer() {
   }

   public static void fill(double[][] ringXs, double[][] ringZs, int minWorldX, int minWorldZ, int maxWorldX, int maxWorldZ, CellConsumer consumer) {
      if (ringXs.length != ringZs.length || ringXs.length == 0) {
         return;
      } else {
         double minZ = Double.POSITIVE_INFINITY;
         double maxZ = Double.NEGATIVE_INFINITY;

         for (int ring = 0; ring < ringXs.length; ring++) {
            double[] zs = ringZs[ring];

            for (double z : zs) {
               minZ = Math.min(minZ, z);
               maxZ = Math.max(maxZ, z);
            }
         }

         int startZ = Math.max(minWorldZ, (int)Math.ceil(minZ - 1.0E-6));
         int endZ = Math.min(maxWorldZ, (int)Math.floor(maxZ + 1.0E-6));
         DoubleArrayList intersections = new DoubleArrayList(16);

         for (int worldZ = startZ; worldZ <= endZ; worldZ++) {
            intersections.clear();
            double scanZ = worldZ;

            for (int ring = 0; ring < ringXs.length; ring++) {
               double[] xs = ringXs[ring];
               double[] zs = ringZs[ring];
               if (xs.length == zs.length && xs.length >= 2) {
                  for (int point = 1; point < xs.length; point++) {
                     double startX = xs[point - 1];
                     double startRingZ = zs[point - 1];
                     double endX = xs[point];
                     double endRingZ = zs[point];
                     if ((startRingZ <= scanZ && endRingZ > scanZ) || (endRingZ <= scanZ && startRingZ > scanZ)) {
                        double t = (scanZ - startRingZ) / (endRingZ - startRingZ);
                        intersections.add(startX + t * (endX - startX));
                     }
                  }
               }
            }

            if (!intersections.isEmpty()) {
               intersections.sort(Double::compare);

               for (int i = 0; i + 1 < intersections.size(); i += 2) {
                  double startX = intersections.getDouble(i);
                  double endX = intersections.getDouble(i + 1);
                  if (endX < startX) {
                     double swap = startX;
                     startX = endX;
                     endX = swap;
                  }

                  int fillStart = Math.max(minWorldX, (int)Math.ceil(startX - 1.0E-6));
                  int fillEnd = Math.min(maxWorldX, (int)Math.floor(endX + 1.0E-6));

                  for (int worldX = fillStart; worldX <= fillEnd; worldX++) {
                     consumer.accept(worldX, worldZ);
                  }
               }
            }
         }
      }
   }

   /**
    * Tests one integer grid cell with the same scanline and boundary rules as
    * {@link #fill}. This is intentionally not a generic point-in-polygon test:
    * coast-field sparse sampling must agree bit-for-bit with the cells that a
    * dense rasterization would fill.
    */
   static boolean contains(double[][] ringXs, double[][] ringZs, int worldX, int worldZ) {
      if (ringXs.length != ringZs.length || ringXs.length == 0) {
         return false;
      }

      DoubleArrayList intersections = new DoubleArrayList(16);
      double scanZ = worldZ;
      for (int ring = 0; ring < ringXs.length; ring++) {
         double[] xs = ringXs[ring];
         double[] zs = ringZs[ring];
         if (xs.length != zs.length || xs.length < 2) {
            continue;
         }

         for (int point = 1; point < xs.length; point++) {
            double startX = xs[point - 1];
            double startZ = zs[point - 1];
            double endX = xs[point];
            double endZ = zs[point];
            if ((startZ <= scanZ && endZ > scanZ) || (endZ <= scanZ && startZ > scanZ)) {
               double t = (scanZ - startZ) / (endZ - startZ);
               intersections.add(startX + t * (endX - startX));
            }
         }
      }

      if (intersections.isEmpty()) {
         return false;
      }

      intersections.sort(Double::compare);
      for (int i = 0; i + 1 < intersections.size(); i += 2) {
         double startX = intersections.getDouble(i);
         double endX = intersections.getDouble(i + 1);
         if (endX < startX) {
            double swap = startX;
            startX = endX;
            endX = swap;
         }

         int fillStart = (int)Math.ceil(startX - 1.0E-6);
         int fillEnd = (int)Math.floor(endX + 1.0E-6);
         if (worldX >= fillStart && worldX <= fillEnd) {
            return true;
         }
      }

      return false;
   }

   @FunctionalInterface
   public interface CellConsumer {
      void accept(int worldX, int worldZ);
   }
}
