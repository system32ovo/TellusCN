package com.yucareux.tellus.world.realtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherTemperaturePolicyTest {
   @Test
   void snowsAtTheConfiguredThreshold() {
      assertTrue(WeatherTemperaturePolicy.shouldSnow(2.0F));
   }

   @Test
   void rainsAboveTheConfiguredThreshold() {
      assertFalse(WeatherTemperaturePolicy.shouldSnow(2.1F));
   }

   @Test
   void missingTemperatureDoesNotForceSnow() {
      assertFalse(WeatherTemperaturePolicy.shouldSnow(Float.NaN));
   }

   @Test
   void freshWaterCanFreezeAtThePhysicalFreezingPoint() {
      assertTrue(WeatherTemperaturePolicy.canWaterFreeze(false, 0.0F));
   }

   @Test
   void freshWaterCannotFreezeAboveThePhysicalFreezingPoint() {
      assertFalse(WeatherTemperaturePolicy.canWaterFreeze(false, 0.1F));
   }

   @Test
   void missingTemperatureCannotFreezeFreshWater() {
      assertFalse(WeatherTemperaturePolicy.canWaterFreeze(false, Float.NaN));
   }

   @Test
   void oceanWaterCannotFreezeEvenBelowTheFreshWaterFreezingPoint() {
      assertFalse(WeatherTemperaturePolicy.canWaterFreeze(true, -20.0F));
   }
}
