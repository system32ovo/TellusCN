package com.yucareux.tellus.world.data.source;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NominatimGeocoderTest {
   @Test
   void formatsCityStateAndCountry() {
      NominatimGeocoder.Location location = NominatimGeocoder.parseLocation(
         JsonParser.parseString(
               """
               {
                 "address": {
                   "city": "Guadalajara",
                   "state": "Jalisco",
                   "country": "México"
                 }
               }
               """
            )
            .getAsJsonObject()
      );

      assertNotNull(location);
      assertEquals("Guadalajara, Jalisco, México", location.displayName());
   }

   @Test
   void fallsBackFromCityToTown() {
      NominatimGeocoder.Location location = NominatimGeocoder.parseLocation(
         JsonParser.parseString(
               """
               {
                 "address": {
                   "town": "Banff",
                   "state": "Alberta",
                   "country": "Canada"
                 }
               }
               """
            )
            .getAsJsonObject()
      );

      assertNotNull(location);
      assertEquals("Banff, Alberta, Canada", location.displayName());
   }
}
