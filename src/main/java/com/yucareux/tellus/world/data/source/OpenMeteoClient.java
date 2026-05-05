package com.yucareux.tellus.world.data.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yucareux.tellus.config.TellusEndpointConfig;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.util.Mth;

public final class OpenMeteoClient {
   private static final int CONNECT_TIMEOUT_MS = 5000;
   private static final int READ_TIMEOUT_MS = 12000;
   private static final int HISTORY_HOURS = 72;
   private static final float MELT_RATE_PER_HOUR = 0.2F;
   private static final float SNOW_ACCUM_SCALE = 10.0F;
   private static final float TEMP_MELT_THRESHOLD = 2.0F;
   private static final String USER_AGENT = "Tellus/1.0 (open-meteo.com)";
   private static final String DEFAULT_BASE_URL = "https://api.open-meteo.com/v1";
   private static final String BASE_URL = TellusEndpointConfig.getWeatherEndpoint(DEFAULT_BASE_URL);

   public OpenMeteoClient.WeatherPointData fetch(double latitude, double longitude) throws IOException {
      String url = buildUrl(latitude, longitude);
      HttpURLConnection connection = (HttpURLConnection)URI.create(url).toURL().openConnection();
      try {
         connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
         connection.setReadTimeout(READ_TIMEOUT_MS);
         connection.setRequestProperty("User-Agent", USER_AGENT);
         connection.setRequestProperty("Accept", "application/json");
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            throw new IOException("Open-Meteo request failed with HTTP " + responseCode);
         } else {
            OpenMeteoClient.WeatherPointData var21;
            try (Reader reader = new InputStreamReader(new BufferedInputStream(connection.getInputStream()), StandardCharsets.UTF_8)) {
               JsonElement rootElement = JsonParser.parseReader(reader);
               if (rootElement.isJsonNull() || !rootElement.isJsonObject()) {
                  throw new IOException("Open-Meteo response missing JSON");
               }

               JsonObject root = rootElement.getAsJsonObject();
               int utcOffset = root.get("utc_offset_seconds").getAsInt();
               String timezoneId = root.has("timezone") ? root.get("timezone").getAsString() : "UTC";
               if (timezoneId == null || timezoneId.isBlank()) {
                  timezoneId = "UTC";
               }

               JsonObject current = root.getAsJsonObject("current");
               int weatherCode = current.get("weather_code").getAsInt();
               float temperature = current.get("temperature_2m").getAsFloat();
               float precipitation = current.get("precipitation").getAsFloat();
               float snowfall = current.get("snowfall").getAsFloat();
               JsonObject hourly = root.getAsJsonObject("hourly");
               OpenMeteoClient.SnowHistory history = parseSnowHistory(hourly);
               float snowIndex = computeSnowIndex(history);
               var21 = new OpenMeteoClient.WeatherPointData(
                  latitude, longitude, utcOffset, timezoneId, weatherCode, temperature, precipitation, snowfall, snowIndex
               );
            }

            return var21;
         }
      } finally {
         connection.disconnect();
      }
   }

   private static OpenMeteoClient.SnowHistory parseSnowHistory(JsonObject hourly) {
      if (hourly == null) {
         return new OpenMeteoClient.SnowHistory(0.0F, 0, 0.0F);
      } else {
         JsonArray temps = hourly.getAsJsonArray("temperature_2m");
         JsonArray snowfall = hourly.getAsJsonArray("snowfall");
         if (temps != null && snowfall != null) {
            int size = Math.min(temps.size(), snowfall.size());
            int start = Math.max(0, size - HISTORY_HOURS);
            float snowSum = 0.0F;
            int meltHours = 0;
            float tempSum = 0.0F;
            int tempCount = 0;

            for (int i = start; i < size; i++) {
               float temp = temps.get(i).getAsFloat();
               float snow = snowfall.get(i).getAsFloat();
               snowSum += snow;
               if (temp > TEMP_MELT_THRESHOLD) {
                  meltHours++;
               }

               tempSum += temp;
               tempCount++;
            }

            float avgTemp = tempCount == 0 ? 0.0F : tempSum / tempCount;
            return new OpenMeteoClient.SnowHistory(snowSum, meltHours, avgTemp);
         } else {
            return new OpenMeteoClient.SnowHistory(0.0F, 0, 0.0F);
         }
      }
   }

   private static float computeSnowIndex(OpenMeteoClient.SnowHistory history) {
      float snowAccum = Math.max(0.0F, history.snowfallSum() - history.meltHours() * MELT_RATE_PER_HOUR);
      float snowIndex = snowAccum / SNOW_ACCUM_SCALE;
      if (history.avgTemp() > TEMP_MELT_THRESHOLD) {
         float extraMelt = (history.avgTemp() - TEMP_MELT_THRESHOLD) * 0.05F;
         snowIndex -= extraMelt;
      }

      return Mth.clamp(snowIndex, 0.0F, 1.0F);
   }

   private static String buildUrl(double latitude, double longitude) {
      return String.format(
         Locale.ROOT,
         "%s/forecast?latitude=%.5f&longitude=%.5f&current=weather_code,temperature_2m,precipitation,snowfall&hourly=temperature_2m,snowfall&past_days=%d&forecast_days=1&timezone=auto",
         BASE_URL,
         latitude,
         longitude,
         historyDays()
      );
   }

   private static int historyDays() {
      return Math.max(1, (HISTORY_HOURS + 23) / 24);
   }

   private record SnowHistory(float snowfallSum, int meltHours, float avgTemp) {
   }

   public record WeatherPointData(
      double latitude,
      double longitude,
      int utcOffsetSeconds,
      String timeZoneId,
      int weatherCode,
      float temperatureC,
      float precipitationMm,
      float snowfallCm,
      float snowIndex
   ) {
      public WeatherPointData(
         double latitude,
         double longitude,
         int utcOffsetSeconds,
         String timeZoneId,
         int weatherCode,
         float temperatureC,
         float precipitationMm,
         float snowfallCm,
         float snowIndex
      ) {
         timeZoneId = Objects.requireNonNullElse(timeZoneId, "UTC");
         this.latitude = latitude;
         this.longitude = longitude;
         this.utcOffsetSeconds = utcOffsetSeconds;
         this.timeZoneId = timeZoneId;
         this.weatherCode = weatherCode;
         this.temperatureC = temperatureC;
         this.precipitationMm = precipitationMm;
         this.snowfallCm = snowfallCm;
         this.snowIndex = snowIndex;
      }
   }
}
