package com.yucareux.tellus.world.data.osm;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.world.data.source.InputStreamSafety;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class OvertureTileUrls {
   private static final String EXTRAS_BUCKET_URL = "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com";
   private static final String RELEASE_PROPERTY = "tellus.overture.release";
   private static final String FALLBACK_RELEASE = "2026-06-17.0";
   private static final int MAX_RELEASE_LISTING_BYTES = 4 * 1024 * 1024;
   private static final Pattern RELEASE_PREFIX_PATTERN = Pattern.compile("tiles/(\\d{4}-\\d{2}-\\d{2}\\.\\d+)/");
   private static final Pattern RELEASE_ID_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\.\\d+");
   private static volatile String latestRelease;

   private OvertureTileUrls() {
   }

   public static String defaultThemeUrl(String theme) {
      String safeTheme = validateTheme(theme);
      return themeUrl(resolvedRelease(), safeTheme);
   }

   public static String resolvedRelease() {
      String release = configuredRelease();
      return release == null ? latestRelease() : release;
   }

   public static String cacheNamespace(String url) {
      String safeUrl = Objects.requireNonNull(url, "url");
      return Integer.toUnsignedString(safeUrl.hashCode(), 36);
   }

   private static String themeUrl(String release, String theme) {
      return EXTRAS_BUCKET_URL + "/tiles/" + release + "/" + theme + ".pmtiles";
   }

   private static String configuredRelease() {
      String configured = System.getProperty(RELEASE_PROPERTY);
      if (configured == null || configured.isBlank()) {
         return null;
      }

      String release = configured.trim();
      if (!RELEASE_ID_PATTERN.matcher(release).matches()) {
         Tellus.LOGGER.warn("Ignoring invalid Overture release '{}'", release);
         return null;
      }

      return release;
   }

   private static String latestRelease() {
      String cached = latestRelease;
      if (cached != null) {
         return cached;
      }

      synchronized (OvertureTileUrls.class) {
         cached = latestRelease;
         if (cached != null) {
            return cached;
         }

         try {
            cached = fetchLatestRelease();
         } catch (IOException | RuntimeException error) {
            Tellus.LOGGER.warn("Failed to discover latest Overture tiles release, using {}", FALLBACK_RELEASE, error);
            cached = FALLBACK_RELEASE;
         }

         latestRelease = cached;
         return cached;
      }
   }

   private static String fetchLatestRelease() throws IOException {
      URI uri = URI.create(EXTRAS_BUCKET_URL + "/?list-type=2&prefix=tiles/&delimiter=/");
      HttpURLConnection connection = (HttpURLConnection)uri.toURL().openConnection();
      try {
         connection.setConnectTimeout(intProperty("tellus.overture.releaseLookup.connectTimeoutMs", 7000, 1, 120000));
         connection.setReadTimeout(intProperty("tellus.overture.releaseLookup.readTimeoutMs", 20000, 1, 180000));
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            throw new IOException("Overture release listing HTTP error " + responseCode);
         }
         long contentLength = connection.getContentLengthLong();
         if (contentLength > MAX_RELEASE_LISTING_BYTES) {
            throw new IOException("Overture release listing exceeds the safety limit");
         }

         try (InputStream input = connection.getInputStream()) {
            byte[] response = InputStreamSafety.readAllBytes(
               input, MAX_RELEASE_LISTING_BYTES, "Overture release listing"
            );
            Document document = newDocumentBuilderFactory().newDocumentBuilder().parse(new ByteArrayInputStream(response));
            NodeList prefixes = document.getElementsByTagName("Prefix");
            String latest = null;
            for (int i = 0; i < prefixes.getLength(); i++) {
               String prefix = prefixes.item(i).getTextContent();
               Matcher matcher = RELEASE_PREFIX_PATTERN.matcher(prefix);
               if (matcher.matches()) {
                  String release = matcher.group(1);
                  if (latest == null || release.compareTo(latest) > 0) {
                     latest = release;
                  }
               }
            }

            if (latest == null) {
               throw new IOException("No Overture tile releases found");
            }

            return latest;
         } catch (ParserConfigurationException | SAXException error) {
            throw new IOException("Failed to parse Overture release listing", error);
         }
      } finally {
         connection.disconnect();
      }
   }

   private static DocumentBuilderFactory newDocumentBuilderFactory() throws ParserConfigurationException {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setExpandEntityReferences(false);
      factory.setXIncludeAware(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory;
   }

   private static String validateTheme(String theme) {
      String safeTheme = Objects.requireNonNull(theme, "theme").toLowerCase(Locale.ROOT);
      if (!safeTheme.matches("[a-z0-9_-]+")) {
         throw new IllegalArgumentException("Invalid Overture PMTiles theme " + theme);
      }

      return safeTheme;
   }

   private static int intProperty(String key, int defaultValue, int minInclusive, int maxInclusive) {
      String value = System.getProperty(key);
      if (value == null || value.isBlank()) {
         return defaultValue;
      }

      try {
         return Math.max(minInclusive, Math.min(maxInclusive, Integer.parseInt(value.trim())));
      } catch (NumberFormatException error) {
         Tellus.LOGGER.warn("Invalid integer system property {}={}, using {}", key, value, defaultValue);
         return defaultValue;
      }
   }
}
