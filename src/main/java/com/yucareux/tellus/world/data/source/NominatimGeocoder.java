package com.yucareux.tellus.world.data.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.config.TellusEndpointConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class NominatimGeocoder implements Geocoder {
   // 默认 Nominatim 官方地址，同时作为镜像失效时的兜底地址
   private static final String DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org";
   // 延迟初始化，确保游戏内镜像配置已经加载
   // 候选基础地址：镜像优先，官方 Nominatim 兜底；镜像路由挂掉时会自动降级到官方源
   private static List<String> getBaseUrls() {
      return TellusEndpointConfig.getGeocodingCandidates(DEFAULT_BASE_URL);
   }
   // 搜索地址模板：域名部分由调用方按候选地址填入，查询参数沿用上游的完整字段
   private static String searchUrlPattern(String baseUrl) {
      return baseUrl + "/search?format=json&addressdetails=1&namedetails=1&dedupe=1&limit=%d&accept-language=%s&q=%s";
   }
   // 反向地理编码地址模板：域名部分由调用方按候选地址填入，其余参数沿用上游
   private static String reverseUrlPattern(String baseUrl) {
      return baseUrl + "/reverse?format=jsonv2&addressdetails=1&zoom=10&accept-language=%s&lat=%.6f&lon=%.6f";
   }
   private static final String DEFAULT_LANGUAGE = "en";
   private static final int GET_LIMIT = 8;
   private static final int SUGGEST_LIMIT = 5;
   private static final int SUGGEST_FETCH_LIMIT = 10;
   private static final int CONNECT_TIMEOUT_MS = 5000;
   private static final int READ_TIMEOUT_MS = 12000;
   private static final int TIMEOUT_RETRIES = 1;
   private static final int RETRY_BACKOFF_MS = 300;
   private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
   private static final Pattern DIACRITIC_PATTERN = Pattern.compile("\\p{M}+");
   private static final Pattern NON_SEARCH_CHARACTER_PATTERN = Pattern.compile("[^\\p{L}\\p{Nd}]+");
   private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
   private final Supplier<String> languageSupplier;

   public NominatimGeocoder() {
      this(() -> Locale.getDefault().toLanguageTag());
   }

   public NominatimGeocoder(String languagePreference) {
      this(() -> languagePreference);
   }

   public NominatimGeocoder(Supplier<String> languageSupplier) {
      this.languageSupplier = Objects.requireNonNull(languageSupplier, "languageSupplier");
   }

   @Override
   public double[] get(String place) {
      try {
         List<NominatimGeocoder.SearchCandidate> candidates = this.search(place, GET_LIMIT);
         if (!candidates.isEmpty()) {
            NominatimGeocoder.SearchCandidate first = candidates.get(0);
            return new double[]{first.latitude(), first.longitude()};
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
         List<NominatimGeocoder.SearchCandidate> candidates = this.search(place, SUGGEST_FETCH_LIMIT);
         List<Geocoder.Suggestion> suggestions = new ArrayList<>(SUGGEST_LIMIT);

         for (int i = 0; i < candidates.size() && suggestions.size() < SUGGEST_LIMIT; i++) {
            NominatimGeocoder.SearchCandidate candidate = candidates.get(i);
            suggestions.add(new Geocoder.Suggestion(candidate.displayName(), candidate.latitude(), candidate.longitude()));
         }

         return suggestions.toArray(new Geocoder.Suggestion[0]);
      } catch (SocketTimeoutException var13) {
         Tellus.LOGGER.warn("Geocoder timed out for: {}", place);
         Tellus.LOGGER.debug("Geocoder timeout details", var13);
      } catch (IOException var14) {
         Tellus.LOGGER.error("Failed to suggest places for: {}", place, var14);
      }

      return new Geocoder.Suggestion[0];
   }

   public NominatimGeocoder.Location reverse(double latitude, double longitude) throws IOException {
      if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
         || latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
         throw new IllegalArgumentException("Geocoder coordinates are outside the valid latitude/longitude range");
      }
      String languagePreference = this.resolveLanguagePreference();
      String encodedLanguagePreference = URLEncoder.encode(languagePreference, StandardCharsets.UTF_8);
      // 依次尝试候选地址：优先镜像，失败时自动回落到官方 Nominatim
      JsonElement result = EndpointFallback.tryCandidates(
         getBaseUrls(),
         "Nominatim reverse geocoding",
         baseUrl -> this.query(
            URI.create(String.format(Locale.ROOT, reverseUrlPattern(baseUrl), encodedLanguagePreference, latitude, longitude)),
            languagePreference
         )
      );
      if (!result.isJsonObject()) {
         return null;
      }

      return parseLocation(result.getAsJsonObject());
   }

   private List<NominatimGeocoder.SearchCandidate> search(String place, int limit) throws IOException {
      if (place == null || place.isBlank()) {
         return List.of();
      }
      String languagePreference = this.resolveLanguagePreference();
      JsonElement result = this.query(place, limit, languagePreference);
      if (!result.isJsonArray()) {
         return List.of();
	      } else {
	         JsonArray array = result.getAsJsonArray();
	         List<String> languageCodes = languageCodes(languagePreference);
	         String normalizedPlace = normalizeSearchText(place);
	         List<NominatimGeocoder.SearchCandidate> candidates = new ArrayList<>(array.size());

	         for (int i = 0; i < array.size(); i++) {
	            JsonElement element = array.get(i);
	            if (element.isJsonObject()) {
	               JsonObject object = element.getAsJsonObject();
	               if (object.has("lat") && object.has("lon")) {
	                  String name = this.displayName(object, languageCodes);
	                  if (!name.isBlank()) {
	                     Double lat = parseDouble(object, "lat");
	                     Double lon = parseDouble(object, "lon");
	                     if (lat != null && lon != null && isValidCoordinate(lat, lon)) {
	                        double score = this.scoreCandidate(normalizedPlace, object, languageCodes, i);
	                        candidates.add(new NominatimGeocoder.SearchCandidate(name, lat, lon, score, i));
	                     }
	                  }
	               }
	            }
	         }

         candidates.sort(Comparator.comparingDouble(NominatimGeocoder.SearchCandidate::score).reversed().thenComparingInt(SearchCandidate::originalIndex));
         return candidates;
      }
   }

   private JsonElement query(String place, int limit, String languagePreference) throws IOException {
      // 把地点名转义成 URL 安全的查询串
      String encodedPlace = URLEncoder.encode(place, StandardCharsets.UTF_8);
      // 把语言偏好也转义，避免逗号等字符破坏查询串
      String encodedLanguagePreference = URLEncoder.encode(languagePreference, StandardCharsets.UTF_8);
      // 依次尝试候选地址：优先镜像，失败时自动回落到官方 Nominatim
      return EndpointFallback.tryCandidates(
         getBaseUrls(),
         "Nominatim search",
         baseUrl -> this.query(
            URI.create(String.format(Locale.ROOT, searchUrlPattern(baseUrl), limit, encodedLanguagePreference, encodedPlace)),
            languagePreference
         )
      );
   }

   private JsonElement query(URI uri, String languagePreference) throws IOException {
      IOException lastError = null;
      int attempt = 0;

      while (attempt <= TIMEOUT_RETRIES) {
         HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
         try {
            connection.setRequestProperty("User-Agent", "Tellus/2.0.0 (Minecraft Mod)");
            connection.setRequestProperty("Accept-Language", languagePreference);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
               throw new IOException("Nominatim request failed with HTTP " + responseCode);
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_RESPONSE_BYTES) {
               throw new IOException("Nominatim response exceeds the safety limit");
            }

            try (InputStream input = connection.getInputStream()) {
               byte[] response = InputStreamSafety.readAllBytes(input, MAX_RESPONSE_BYTES, "Nominatim response");
               try {
                  return JsonParser.parseString(new String(response, StandardCharsets.UTF_8));
               } catch (RuntimeException error) {
                  throw new IOException("Nominatim returned malformed JSON", error);
               }
            }
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

   static NominatimGeocoder.Location parseLocation(JsonObject object) {
      JsonObject address = getObject(object, "address");
      if (address == null) {
         return null;
      }

      String city = firstNonBlank(
         getString(address, "city"),
         getString(address, "town"),
         getString(address, "village"),
         getString(address, "municipality"),
         getString(address, "hamlet"),
         getString(address, "locality"),
         getString(address, "county")
      );
      String state = firstNonBlank(
         getString(address, "state"),
         getString(address, "region"),
         getString(address, "state_district"),
         getString(address, "province")
      );
      String country = getString(address, "country");
      if (city.isBlank() && state.isBlank() && country.isBlank()) {
         return null;
      }

      return new NominatimGeocoder.Location(city, state, country);
   }

   private static String firstNonBlank(String... values) {
      for (String value : values) {
         if (value != null && !value.isBlank()) {
            return value;
         }
      }

      return "";
   }

   private static boolean isValidCoordinate(double latitude, double longitude) {
      return Double.isFinite(latitude)
         && Double.isFinite(longitude)
         && latitude >= -90.0
         && latitude <= 90.0
         && longitude >= -180.0
         && longitude <= 180.0;
   }

   private String displayName(JsonObject object, List<String> languageCodes) {
      String displayName = getString(object, "display_name");
      if (!displayName.isBlank()) {
         return displayName;
      } else {
         JsonObject namedetails = getObject(object, "namedetails");
         String localizedName = this.firstNamedValue(namedetails, languageCodes);
         return localizedName.isBlank() ? getString(object, "name") : localizedName;
      }
   }

	   private double scoreCandidate(String normalizedQuery, JsonObject object, List<String> languageCodes, int index) {
	      Set<String> searchNames = this.searchNames(object, languageCodes);
	      double score = Math.max(0.0, 30.0 - index * 2.0);
	      score += importance(object) * 20.0;
      score += matchScore(normalizedQuery, searchNames);
      score += featureTypeBoost(normalizedQuery, object);
      return score;
   }

   private Set<String> searchNames(JsonObject object, List<String> languageCodes) {
      Set<String> names = new LinkedHashSet<>();
      addSearchName(names, getString(object, "display_name"));
      addSearchName(names, firstDisplayNamePart(getString(object, "display_name")));
      addSearchName(names, getString(object, "name"));
      JsonObject namedetails = getObject(object, "namedetails");

      for (String languageCode : languageCodes) {
         addSearchName(names, getNamedDetail(namedetails, "name:" + languageCode));
         addSearchName(names, getNamedDetail(namedetails, "official_name:" + languageCode));
         addSearchName(names, getNamedDetail(namedetails, "short_name:" + languageCode));
         addSearchName(names, getNamedDetail(namedetails, "alt_name:" + languageCode));
      }

      addSearchName(names, getNamedDetail(namedetails, "name"));
      addSearchName(names, getNamedDetail(namedetails, "official_name"));
      addSearchName(names, getNamedDetail(namedetails, "short_name"));
      addSearchName(names, getNamedDetail(namedetails, "alt_name"));
      addSearchName(names, getNamedDetail(namedetails, "int_name"));
      addSearchName(names, getNamedDetail(namedetails, "loc_name"));

      for (String value : allNameValues(namedetails)) {
         addSearchName(names, value);
      }

      return names;
   }

   private String firstNamedValue(JsonObject namedetails, List<String> languageCodes) {
      for (String languageCode : languageCodes) {
         String value = getNamedDetail(namedetails, "name:" + languageCode);
         if (!value.isBlank()) {
            return value;
         }
      }

      String name = getNamedDetail(namedetails, "name");
      return name.isBlank() ? getNamedDetail(namedetails, "int_name") : name;
   }

   private String resolveLanguagePreference() {
      try {
         return languagePreference(this.languageSupplier.get());
      } catch (RuntimeException var2) {
         Tellus.LOGGER.warn("Failed to resolve geocoder language preference", var2);
         return DEFAULT_LANGUAGE;
      }
   }

   private static String languagePreference(String selectedLanguage) {
      LinkedHashSet<String> languages = new LinkedHashSet<>();
      addLanguagePreference(languages, selectedLanguage);
      addLanguagePreference(languages, DEFAULT_LANGUAGE);
      return String.join(",", languages);
   }

   private static void addLanguagePreference(Set<String> languages, String rawPreference) {
      if (rawPreference != null) {
         for (String part : rawPreference.split(",")) {
            String language = part.split(";", 2)[0].trim();
            String normalized = normalizeLanguageTag(language);
            if (!normalized.isEmpty()) {
               languages.add(normalized);
               String baseLanguage = baseLanguage(normalized);
               if (!baseLanguage.equals(normalized)) {
                  languages.add(baseLanguage);
               }
            }
         }
      }
   }

   private static String normalizeLanguageTag(String language) {
      if (language == null) {
         return "";
      } else {
         String sanitized = language.trim().replace('_', '-');
         if (sanitized.isEmpty()) {
            return "";
         } else {
            Locale locale = Locale.forLanguageTag(sanitized);
            String tag = locale.toLanguageTag();
            return "und".equals(tag) ? sanitized : tag;
         }
      }
   }

   private static String baseLanguage(String language) {
      int separator = language.indexOf('-');
      return separator <= 0 ? language : language.substring(0, separator);
   }

   private static List<String> languageCodes(String languagePreference) {
      LinkedHashSet<String> codes = new LinkedHashSet<>();

      for (String part : languagePreference.split(",")) {
         String language = normalizeLanguageTag(part.split(";", 2)[0]);
         if (!language.isEmpty()) {
            codes.add(language);
            codes.add(language.toLowerCase(Locale.ROOT));
            codes.add(baseLanguage(language));
         }
      }

      codes.add(DEFAULT_LANGUAGE);
      return List.copyOf(codes);
   }

   private static double matchScore(String normalizedQuery, Set<String> searchNames) {
      if (normalizedQuery.isBlank() || searchNames.isEmpty()) {
         return 0.0;
      } else {
         double best = 0.0;
         List<String> queryForms = queryForms(normalizedQuery);
         String[] queryTokens = normalizedQuery.split(" ");

         for (String searchName : searchNames) {
            String normalizedName = normalizeSearchText(searchName);
            if (!normalizedName.isBlank()) {
               for (String queryForm : queryForms) {
                  if (normalizedName.equals(queryForm)) {
                     best = Math.max(best, 90.0);
                  } else if (normalizedName.startsWith(queryForm + " ")) {
                     best = Math.max(best, 70.0);
                  } else if (containsPhrase(normalizedName, queryForm)) {
                     best = Math.max(best, 55.0);
                  }
               }

               if (containsAllTokens(normalizedName, queryTokens)) {
                  best = Math.max(best, 35.0);
               }
            }
         }

         return best;
      }
   }

   private static List<String> queryForms(String normalizedQuery) {
      LinkedHashSet<String> forms = new LinkedHashSet<>();
      forms.add(normalizedQuery);
      forms.add(expandCommonAbbreviations(normalizedQuery));
      return forms.stream().filter(value -> !value.isBlank()).toList();
   }

   private static String expandCommonAbbreviations(String normalizedQuery) {
      String[] tokens = normalizedQuery.split(" ");

      for (int i = 0; i < tokens.length; i++) {
         if ("mt".equals(tokens[i])) {
            tokens[i] = "mount";
         } else if ("st".equals(tokens[i])) {
            tokens[i] = "saint";
         }
      }

      return String.join(" ", tokens);
   }

   private static boolean containsPhrase(String normalizedName, String normalizedQuery) {
      return (" " + normalizedName + " ").contains(" " + normalizedQuery + " ");
   }

   private static boolean containsAllTokens(String normalizedName, String[] queryTokens) {
      for (String token : queryTokens) {
         if (!token.isBlank() && !containsPhrase(normalizedName, token)) {
            return false;
         }
      }

      return true;
   }

   private static double featureTypeBoost(String normalizedQuery, JsonObject object) {
      if (normalizedQuery.isBlank()) {
         return 0.0;
      } else {
         String objectClass = getString(object, "class");
         String type = getString(object, "type");
         if ("natural".equals(objectClass) && "peak".equals(type) && looksLikePeakQuery(normalizedQuery)) {
            return 12.0;
         } else {
            return 0.0;
         }
      }
   }

   private static boolean looksLikePeakQuery(String normalizedQuery) {
      return containsPhrase(normalizedQuery, "mt")
         || containsPhrase(normalizedQuery, "mount")
         || containsPhrase(normalizedQuery, "monte")
         || containsPhrase(normalizedQuery, "mont")
         || containsPhrase(normalizedQuery, "peak")
         || containsPhrase(normalizedQuery, "summit")
         || containsPhrase(normalizedQuery, "berg");
   }

   private static String normalizeSearchText(String value) {
      if (value == null || value.isBlank()) {
         return "";
      } else {
         String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
         String withoutDiacritics = DIACRITIC_PATTERN.matcher(decomposed).replaceAll("");
         String searchable = NON_SEARCH_CHARACTER_PATTERN.matcher(withoutDiacritics.toLowerCase(Locale.ROOT)).replaceAll(" ");
         return WHITESPACE_PATTERN.matcher(searchable).replaceAll(" ").trim();
      }
   }

   private static void addSearchName(Set<String> names, String name) {
      if (name != null && !name.isBlank()) {
         names.add(name);
      }
   }

   private static String firstDisplayNamePart(String displayName) {
      int separator = displayName.indexOf(',');
      return separator < 0 ? displayName : displayName.substring(0, separator);
   }

   private static List<String> allNameValues(JsonObject namedetails) {
      if (namedetails == null) {
         return List.of();
      } else {
         List<String> values = new ArrayList<>();

         for (String key : namedetails.keySet()) {
            if (key.contains("name")) {
               String value = getString(namedetails, key);
               if (!value.isBlank()) {
                  values.add(value);
               }
            }
         }

         return values;
      }
   }

   private static String getNamedDetail(JsonObject namedetails, String key) {
      if (namedetails == null || key == null || key.isBlank()) {
         return "";
      } else if (namedetails.has(key)) {
         return getString(namedetails, key);
      } else {
         for (String candidateKey : namedetails.keySet()) {
            if (candidateKey.equalsIgnoreCase(key)) {
               return getString(namedetails, candidateKey);
            }
         }

         return "";
      }
   }

   private static JsonObject getObject(JsonObject object, String key) {
      JsonElement element = object.get(key);
      return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
   }

	   private static String getString(JsonObject object, String key) {
	      if (object == null) {
	         return "";
	      } else {
	         JsonElement element = object.get(key);
	         try {
	            return element == null || element.isJsonNull() ? "" : element.getAsString();
	         } catch (IllegalStateException | UnsupportedOperationException ignored) {
	            return "";
	         }
	      }
	   }

	   private static Double parseDouble(JsonObject object, String key) {
	      String value = getString(object, key);
	      if (value.isBlank()) {
	         return null;
	      }

	      try {
	         double parsed = Double.parseDouble(value);
	         return Double.isFinite(parsed) ? parsed : null;
	      } catch (NumberFormatException ignored) {
	         return null;
	      }
	   }

   private static double importance(JsonObject object) {
      String value = getString(object, "importance");
      if (value.isBlank()) {
         return 0.0;
      } else {
         try {
            double parsed = Double.parseDouble(value);
            return Math.max(0.0, Math.min(1.0, parsed));
         } catch (NumberFormatException var4) {
            return 0.0;
         }
      }
   }

   private record SearchCandidate(String displayName, double latitude, double longitude, double score, int originalIndex) {
   }

   public record Location(String city, String state, String country) {
      public Location {
         city = Objects.requireNonNullElse(city, "");
         state = Objects.requireNonNullElse(state, "");
         country = Objects.requireNonNullElse(country, "");
      }

      public String displayName() {
         LinkedHashSet<String> parts = new LinkedHashSet<>();
         if (!this.city.isBlank()) {
            parts.add(this.city);
         }
         if (!this.state.isBlank()) {
            parts.add(this.state);
         }
         if (!this.country.isBlank()) {
            parts.add(this.country);
         }
         return String.join(", ", parts);
      }
   }
}
