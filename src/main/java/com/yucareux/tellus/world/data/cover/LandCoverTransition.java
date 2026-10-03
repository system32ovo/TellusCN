package com.yucareux.tellus.world.data.cover;

/**
 * Turns categorical raster interpolation weights into stable, organic-looking
 * land-cover boundaries.
 *
 * <p>Land-cover classes cannot be numerically interpolated like elevation. The
 * four neighboring classes are therefore blended spatially: their bilinear
 * weights select which class owns each block. A continuous, absolute-coordinate
 * noise field keeps the result clustered instead of producing checkerboard
 * dithering, and makes the result independent of chunk generation order.</p>
 */
public final class LandCoverTransition {
   private static final int NO_DATA_CLASS = 0;
   private static final int BUILT_UP_CLASS = 50;
   private static final int WATER_CLASS = 80;
   private static final int MANGROVES_CLASS = 95;
   private static final long COARSE_NOISE_SEED = 9154887495218319081L;
   private static final long DETAIL_NOISE_SEED = 5883890050026909207L;
   private static final long EDGE_DETAIL_SEED = 2611923443488327891L;
   private static final ThreadLocal<BlendScratch> BLEND_SCRATCH = ThreadLocal.withInitial(BlendScratch::new);

   private LandCoverTransition() {
   }

   /**
    * Smoothly enables transitions only when the generated result is finer than
    * the source raster. A 1 m output receives the full treatment for 10 m
    * WorldCover data, while output at the source resolution remains exact.
    */
   public static double strength(double sourceResolutionMeters, double effectiveResolutionMeters) {
      if (!(Double.isFinite(sourceResolutionMeters) && sourceResolutionMeters > 1.0)
         || !(Double.isFinite(effectiveResolutionMeters) && effectiveResolutionMeters > 0.0)
         || effectiveResolutionMeters >= sourceResolutionMeters) {
         return 0.0;
      }

      double linear = clamp(
         (sourceResolutionMeters - effectiveResolutionMeters) / (sourceResolutionMeters - 1.0),
         0.0,
         1.0
      );
      return linear * linear * (3.0 - 2.0 * linear);
   }

   /**
    * Selects a visual class from a center sample and the four samples around
    * the center of the categorical pixel grid.
    */
   public static int selectVisualClass(
      int centerClass,
      int class00,
      int class10,
      int class01,
      int class11,
      double fractionX,
      double fractionZ,
      double transitionStrength,
      double blockX,
      double blockZ,
      double sourceCellBlocks
   ) {
      if (!(transitionStrength > 0.0)
         || !isBlendableClass(centerClass)
         || isHardClass(centerClass)
         || !isBlendableClass(class00)
         || !isBlendableClass(class10)
         || !isBlendableClass(class01)
         || !isBlendableClass(class11)
         || isHardClass(class00)
         || isHardClass(class10)
         || isHardClass(class01)
         || isHardClass(class11)) {
         return centerClass;
      }
      if (centerClass == class00
         && centerClass == class10
         && centerClass == class01
         && centerClass == class11) {
         return centerClass;
      }

      double fx = clamp(fractionX, 0.0, 1.0);
      double fz = clamp(fractionZ, 0.0, 1.0);
      double strength = clamp(transitionStrength, 0.0, 1.0);
      double inverseX = 1.0 - fx;
      double inverseZ = 1.0 - fz;
      BlendScratch scratch = BLEND_SCRATCH.get();
      scratch.reset();
      scratch.add(centerClass, 1.0 - strength);
      scratch.add(class00, inverseX * inverseZ * strength);
      scratch.add(class10, fx * inverseZ * strength);
      scratch.add(class01, inverseX * fz * strength);
      scratch.add(class11, fx * fz * strength);
      return scratch.pick(centerClass, transitionThreshold(blockX, blockZ, sourceCellBlocks));
   }

   public static boolean isHardClass(int coverClass) {
      return coverClass == NO_DATA_CLASS
         || coverClass == BUILT_UP_CLASS
         || coverClass == WATER_CLASS
         || coverClass == MANGROVES_CLASS;
   }

   static double transitionThreshold(double blockX, double blockZ, double sourceCellBlocks) {
      double cellBlocks = Double.isFinite(sourceCellBlocks) && sourceCellBlocks > 0.0
         ? sourceCellBlocks
         : 1.0;
      double patchBlocks = clamp(cellBlocks * 0.45, 2.0, 12.0);
      double coarse = valueNoise(blockX / patchBlocks, blockZ / patchBlocks, COARSE_NOISE_SEED);
      double detail = valueNoise(
         blockX / (patchBlocks * 0.47) + 31.75,
         blockZ / (patchBlocks * 0.47) - 19.25,
         DETAIL_NOISE_SEED
      );

      // Interpolated value noise clusters around 0.5. Restoring contrast keeps
      // the full bilinear transition width while retaining coherent patches.
      double field = clamp(0.72 * coarse + 0.28 * detail, 0.0, 1.0);
      field = clamp(0.5 + (field - 0.5) * 1.45, 0.0, 1.0);
      field = field * field * (3.0 - 2.0 * field);
      double edgeDetail = hashToUnit(floorToLong(blockX), floorToLong(blockZ), EDGE_DETAIL_SEED) - 0.5;
      return clamp(field + edgeDetail * 0.12, 0.0, Math.nextDown(1.0));
   }

   private static boolean isBlendableClass(int coverClass) {
      return coverClass >= 0 && coverClass <= 255;
   }

   private static double valueNoise(double x, double z, long seed) {
      long x0 = floorToLong(x);
      long z0 = floorToLong(z);
      double fractionX = x - x0;
      double fractionZ = z - z0;
      double value00 = hashToUnit(x0, z0, seed);
      double value10 = hashToUnit(x0 + 1L, z0, seed);
      double value01 = hashToUnit(x0, z0 + 1L, seed);
      double value11 = hashToUnit(x0 + 1L, z0 + 1L, seed);
      double smoothX = smootherStep(fractionX);
      double smoothZ = smootherStep(fractionZ);
      double north = value00 + (value10 - value00) * smoothX;
      double south = value01 + (value11 - value01) * smoothX;
      return north + (south - north) * smoothZ;
   }

   private static double smootherStep(double value) {
      double clamped = clamp(value, 0.0, 1.0);
      return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
   }

   private static double hashToUnit(long x, long z, long seed) {
      long hash = seed ^ x * -7046029254386353131L;
      hash ^= z * -4417276706812531889L;
      hash = mix64(hash);
      return (hash >>> 11) * 0x1.0p-53;
   }

   private static long mix64(long value) {
      long mixed = (value ^ value >>> 33) * -49064778989728563L;
      mixed = (mixed ^ mixed >>> 33) * -4265267296055464877L;
      return mixed ^ mixed >>> 33;
   }

   private static long floorToLong(double value) {
      if (value <= Long.MIN_VALUE) {
         return Long.MIN_VALUE;
      }
      if (value >= Long.MAX_VALUE) {
         return Long.MAX_VALUE;
      }
      return (long)Math.floor(value);
   }

   private static double clamp(double value, double minimum, double maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private static final class BlendScratch {
      private final double[] weights = new double[256];
      private final int[] used = new int[5];
      private int usedCount;

      private void reset() {
         for (int index = 0; index < this.usedCount; index++) {
            this.weights[this.used[index]] = 0.0;
         }
         this.usedCount = 0;
      }

      private void add(int coverClass, double weight) {
         if (!(weight > 0.0) || !isBlendableClass(coverClass)) {
            return;
         }
         if (!(this.weights[coverClass] > 0.0)) {
            this.used[this.usedCount++] = coverClass;
         }
         this.weights[coverClass] += weight;
      }

      private int pick(int fallbackClass, double threshold) {
         if (this.usedCount == 0) {
            return fallbackClass;
         }
         double total = 0.0;
         for (int index = 0; index < this.usedCount; index++) {
            total += this.weights[this.used[index]];
         }
         if (!(total > 0.0)) {
            return fallbackClass;
         }

         double target = clamp(threshold, 0.0, Math.nextDown(1.0)) * total;
         double cumulative = 0.0;
         int selected = fallbackClass;
         for (int index = 0; index < this.usedCount; index++) {
            selected = this.used[index];
            cumulative += this.weights[selected];
            if (target < cumulative) {
               break;
            }
         }
         return selected;
      }
   }
}
