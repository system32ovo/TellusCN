package com.yucareux.tellus.world.data.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.TellusEndpointConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NominatimGeocoder implements Geocoder {
   private static final String DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org";
   private static final String BASE_URL = TellusEndpointConfig.getGeocodingEndpoint(DEFAULT_BASE_URL);
   private static final String SEARCH_URL = BASE_URL + "/search?format=json&limit=%d&q=%s";
   private static final int GET_LIMIT = 1;
   private static final int SUGGEST_LIMIT = 5;
   private static final int CONNECT_TIMEOUT_MS = 5000;
   private static final int READ_TIMEOUT_MS = 12000;
   private static final int TIMEOUT_RETRIES = 1;
   private static final int RETRY_BACKOFF_MS = 300;

   @Override
   public double[] get(String place) {
      try {
         JsonElement result = this.query(place, GET_LIMIT);
         if (result.isJsonArray()) {
            JsonArray array = result.getAsJsonArray();
            if (!array.isEmpty()) {
               JsonObject first = array.get(0).getAsJsonObject();
               double lat = first.get("lat").getAsDouble();
               double lon = first.get("lon").getAsDouble();
               return new double[]{lat, lon};
            }
         }
      } catch (SocketTimeoutException var9) {
         Tellus.LOGGER.warn("Geocoder timed out for: {}", place);
         Tellus.LOGGER.debug("Geocoder timeout details", var9);
      } catch (IOException var10) {
         Tellus.LOGGER.error("Failed to geocode place: {}", place, var10);
      }

      return null;
   }

   @Override
   public Geocoder.Suggestion[] suggest(String place) {
      try {
         JsonElement result = this.query(place, SUGGEST_LIMIT);
         if (result.isJsonArray()) {
            JsonArray array = result.getAsJsonArray();
            List<Geocoder.Suggestion> suggestions = new ArrayList<>(SUGGEST_LIMIT);

            for (JsonElement element : array) {
               if (element.isJsonObject()) {
                  JsonObject object = element.getAsJsonObject();
                  if (object.has("display_name") && object.has("lat") && object.has("lon")) {
                     String name = object.get("display_name").getAsString();
                     double lat = object.get("lat").getAsDouble();
                     double lon = object.get("lon").getAsDouble();
                     suggestions.add(new Geocoder.Suggestion(name, lat, lon));
                  }
               }
            }

            return suggestions.toArray(new Geocoder.Suggestion[0]);
         }
      } catch (SocketTimeoutException var13) {
         Tellus.LOGGER.warn("Geocoder timed out for: {}", place);
         Tellus.LOGGER.debug("Geocoder timeout details", var13);
      } catch (IOException var14) {
         Tellus.LOGGER.error("Failed to suggest places for: {}", place, var14);
      }

      return new Geocoder.Suggestion[0];
   }

   private JsonElement query(String place, int limit) throws IOException {
      String encodedPlace = URLEncoder.encode(place, StandardCharsets.UTF_8);
      URI uri = URI.create(String.format(SEARCH_URL, limit, encodedPlace));
      IOException lastError = null;
      int attempt = 0;

      while (attempt <= TIMEOUT_RETRIES) {
         HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
         try {
            connection.setRequestProperty("User-Agent", "Tellus/2.0.0 (Minecraft Mod)");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            JsonElement var10;
            try (
               InputStream input = connection.getInputStream();
               InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            ) {
               var10 = JsonParser.parseReader(reader);
            }

            return var10;
         } catch (SocketTimeoutException var17) {
            lastError = var17;
            if (attempt >= TIMEOUT_RETRIES) {
               throw var17;
            }

            try {
               Thread.sleep(RETRY_BACKOFF_MS);
            } catch (InterruptedException var12) {
               Thread.currentThread().interrupt();
               throw new IOException("Geocoder retry interrupted", var12);
            }

            attempt++;
         } catch (IOException var18) {
            throw var18;
         } finally {
            connection.disconnect();
         }
      }

      throw lastError != null ? lastError : new IOException("Geocoder query failed");
   }
}
