package com.yucareux.tellus.world.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemperatureGridTest {
   @Test
   void interpolatesTemperatureAtTheCenter() {
      TemperatureGrid grid = new TemperatureGrid(
         0,
         0,
         100,
         new float[]{0.0F, 1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F},
         System.currentTimeMillis()
      );

      assertEquals(4.0F, grid.sample(0, 0));
   }

   @Test
   void rejectsPositionsOutsideTheSampledArea() {
      TemperatureGrid grid = new TemperatureGrid(
         0,
         0,
         100,
         new float[]{0.0F, 1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F},
         System.currentTimeMillis()
      );

      assertTrue(Float.isNaN(grid.sample(101, 0)));
   }

   @Test
   void rejectsExpiredSamples() {
      TemperatureGrid grid = new TemperatureGrid(
         0,
         0,
         100,
         new float[]{0.0F, 1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F},
         System.currentTimeMillis() - 1200001L
      );

      assertTrue(Float.isNaN(grid.sample(0, 0)));
   }
}
