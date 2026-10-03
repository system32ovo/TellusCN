package com.yucareux.tellus.world.data.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yucareux.tellus.config.TellusEndpointConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.util.Mth;

public final class OpenMeteoClient {
   private static final int CONNECT_TIMEOUT_MS = 5000;
   private static final int READ_TIMEOUT_MS = 12000;
   private static final int HISTORY_HOURS = 72;
   private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
   private static final float MELT_RATE_PER_HOUR = 0.2F;
   private static final float SNOW_ACCUM_SCALE = 10.0F;
   private static final float TEMP_MELT_THRESHOLD = 2.0F;
   private static final String USER_AGENT = "Tellus/1.0 (open-meteo.com)";
   private static final String DEFAULT_BASE_URL = "https://api.open-meteo.com/v1";
   // 延迟初始化，确保配置已加载
   // 候选基础地址：镜像优先，官方 Open-Meteo 兜底；镜像路由挂掉时会自动降级到官方源
   private static List<String> getBaseUrls() {
      return TellusEndpointConfig.getWeatherCandidates(DEFAULT_BASE_URL);
   }

   public OpenMeteoClient.WeatherPointData fetch(double latitude, double longitude) throws IOException {
      // 喵~防御：坐标非法属于调用方参数错误，必须在降级之前就拦下，否则会把参数错误误判成"地址挂了"白试一遍
      if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
         || latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
         throw new IllegalArgumentException("Weather coordinates are outside the valid latitude/longitude range");
      }
      // 依次尝试候选地址：优先镜像，失败时自动回落到官方 Open-Meteo
      return EndpointFallback.tryCandidates(
         getBaseUrls(), "Open-Meteo weather", baseUrl -> fetchFrom(baseUrl, latitude, longitude)
      );
   }

   /**
    * 用指定的基础地址请求一次天气数据。
    *
    * 输入：基础地址（例如镜像的 /weather 或官方 https://api.open-meteo.com/v1）与已校验的经纬度。
    * 输出：解析后的天气点数据。
    * 边界条件：非 200 响应、响应体超出上限、JSON 结构或数值非法时统一抛 IOException，由上层决定是否降级。
    */
   private static OpenMeteoClient.WeatherPointData fetchFrom(String baseUrl, double latitude, double longitude) throws IOException {
      // 按基础地址拼出完整的预报请求地址
      String url = buildUrl(baseUrl, latitude, longitude);
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
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_RESPONSE_BYTES) {
               throw new IOException("Open-Meteo response exceeds the safety limit");
            }
            byte[] response;
            try (var input = connection.getInputStream()) {
               response = InputStreamSafety.readAllBytes(input, MAX_RESPONSE_BYTES, "Open-Meteo response");
            }

            try {
               JsonElement rootElement = JsonParser.parseString(new String(response, StandardCharsets.UTF_8));
               if (rootElement.isJsonNull() || !rootElement.isJsonObject()) {
                  throw new IOException("Open-Meteo response missing JSON");
               }

               JsonObject root = rootElement.getAsJsonObject();
               int utcOffset = root.get("utc_offset_seconds").getAsInt();
               String timezoneId = root.has("timezone") ? root.get("timezone").getAsString() : "UTC";
               if (timezoneId == null || timezoneId.isBlank()) {
                  timezoneId = "UTC";
               }
               if (Math.abs((long)utcOffset) > 86400L || timezoneId.length() > 128) {
                  throw new IOException("Open-Meteo response contains invalid time-zone metadata");
               }

               JsonObject current = root.getAsJsonObject("current");
               int weatherCode = current.get("weather_code").getAsInt();
               float temperature = current.get("temperature_2m").getAsFloat();
               float precipitation = current.get("precipitation").getAsFloat();
               float snowfall = current.get("snowfall").getAsFloat();
               if (weatherCode < 0 || weatherCode > 999
                  || !Float.isFinite(temperature) || !Float.isFinite(precipitation) || !Float.isFinite(snowfall)
                  || precipitation < 0.0F || snowfall < 0.0F) {
                  throw new IOException("Open-Meteo response contains invalid weather values");
               }
               JsonObject hourly = root.getAsJsonObject("hourly");
               OpenMeteoClient.SnowHistory history = parseSnowHistory(hourly);
               float snowIndex = computeSnowIndex(history);
               return new OpenMeteoClient.WeatherPointData(
                  latitude, longitude, utcOffset, timezoneId, weatherCode, temperature, precipitation, snowfall, snowIndex
               );
            } catch (IOException error) {
               throw error;
            } catch (RuntimeException error) {
               throw new IOException("Open-Meteo returned malformed JSON", error);
            }
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
               if (!Float.isFinite(temp) || !Float.isFinite(snow)) {
                  continue;
               }
               snow = Math.max(0.0F, snow);
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

   /**
    * 按基础地址拼出预报请求地址。
    *
    * 输入：基础地址（镜像或官方）与经纬度。
    * 输出：完整的 forecast 请求 URL。
    * 边界条件：基础地址为空串时会拼出以 /forecast 开头的相对地址，随后由 URI.create 抛异常暴露配置问题。
    */
   private static String buildUrl(String baseUrl, double latitude, double longitude) {
      return String.format(
         Locale.ROOT,
         "%s/forecast?latitude=%.5f&longitude=%.5f&current=weather_code,temperature_2m,precipitation,snowfall&hourly=temperature_2m,snowfall&past_days=%d&forecast_days=1&timezone=auto",
         baseUrl,
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
