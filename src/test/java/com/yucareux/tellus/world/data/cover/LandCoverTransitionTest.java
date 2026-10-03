package com.yucareux.tellus.world.data.cover;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LandCoverTransitionTest {
   @Test
   void fadesInOnlyBelowTheCategoricalSourceResolution() {
      assertEquals(0.0, LandCoverTransition.strength(10.0, 10.0));
      assertEquals(0.0, LandCoverTransition.strength(10.0, 20.0));
      assertEquals(1.0, LandCoverTransition.strength(10.0, 1.0));
      assertTrue(LandCoverTransition.strength(10.0, 5.0) > 0.5);
   }

   @Test
   void keepsSemanticHardClassesOutOfVisualDithering() {
      assertEquals(80, select(80, 10, 10, 10, 10, 0.5, 0.5, 12, 8));
      assertEquals(10, select(10, 10, 80, 10, 80, 0.5, 0.5, 12, 8));
      assertEquals(50, select(50, 10, 60, 30, 40, 0.5, 0.5, 12, 8));
      assertEquals(95, select(95, 10, 60, 30, 40, 0.5, 0.5, 12, 8));
   }

   @Test
   void producesDeterministicAbsoluteCoordinateTransitions() {
      int first = select(10, 10, 60, 10, 60, 0.55, 0.25, -12345, 67890);
      for (int attempt = 0; attempt < 100; attempt++) {
         assertEquals(first, select(10, 10, 60, 10, 60, 0.55, 0.25, -12345, 67890));
      }
   }

   @Test
   void breaksAFormerlyStraightRasterBoundaryIntoCoherentVariation() {
      Set<Integer> transitionEdges = new HashSet<>();
      for (int z = 0; z < 96; z++) {
         int firstRightClass = 10;
         for (int x = 0; x <= 10; x++) {
            int selected = select(10, 10, 60, 10, 60, x / 10.0, 0.35, x, z);
            if (selected == 60) {
               firstRightClass = x;
               break;
            }
         }
         transitionEdges.add(firstRightClass);
      }

      assertTrue(transitionEdges.size() >= 4, "the transition should not remain an axis-aligned raster edge");
   }

   @Test
   void leavesUniformRasterInteriorsUntouched() {
      for (int z = -16; z <= 16; z++) {
         for (int x = -16; x <= 16; x++) {
            assertEquals(30, select(30, 30, 30, 30, 30, 0.37, 0.61, x, z));
         }
      }
   }

   private static int select(
      int center,
      int class00,
      int class10,
      int class01,
      int class11,
      double fractionX,
      double fractionZ,
      double blockX,
      double blockZ
   ) {
      return LandCoverTransition.selectVisualClass(
         center,
         class00,
         class10,
         class01,
         class11,
         fractionX,
         fractionZ,
         1.0,
         blockX,
         blockZ,
         10.0
      );
   }
}
