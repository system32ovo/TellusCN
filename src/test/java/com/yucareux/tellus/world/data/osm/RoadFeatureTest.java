package com.yucareux.tellus.world.data.osm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadFeatureTest {
   @Test
   void ownsCoordinateArraysAfterConstruction() {
      double[] longitudes = new double[]{-99.10, -99.08};
      double[] latitudes = new double[]{19.40, 19.42};
      RoadFeature feature = new RoadFeature(1L, RoadClass.NORMAL, RoadMode.NORMAL, 0, "residential", longitudes, latitudes);

      longitudes[0] = -100.0;
      latitudes[0] = 20.0;

      assertEquals(-99.10, feature.lonAt(0), 0.0001);
      assertEquals(19.40, feature.latAt(0), 0.0001);
      assertTrue(feature.intersects(19.39, -99.11, 19.43, -99.07));
      assertFalse(feature.intersects(19.90, -100.10, 20.10, -99.90));
   }

   @Test
   void returnsCoordinateArrayCopies() {
      RoadFeature feature = new RoadFeature(
         1L,
         RoadClass.NORMAL,
         RoadMode.NORMAL,
         0,
         "residential",
         new double[]{-99.10, -99.08},
         new double[]{19.40, 19.42}
      );
      double[] exportedLongitudes = feature.longitudes();
      double[] exportedLatitudes = feature.latitudes();

      exportedLongitudes[0] = -100.0;
      exportedLatitudes[0] = 20.0;

      assertEquals(-99.10, feature.lonAt(0), 0.0001);
      assertEquals(19.40, feature.latAt(0), 0.0001);
   }
}
