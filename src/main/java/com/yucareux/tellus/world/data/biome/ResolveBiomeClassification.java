package com.yucareux.tellus.world.data.biome;

import com.yucareux.tellus.Tellus;
import com.yucareux.tellus.world.data.resolve.ResolveBiome;
import com.yucareux.tellus.world.data.resolve.ResolveEcoregion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Data-driven RESOLVE + ESA + Köppen classification overrides.
 *
 * <p>Rule precedence is exact Köppen code, longest prefix pattern, then the
 * wildcard rule. Returning {@code null} preserves the legacy ESA + Köppen
 * mapping as a complete fallback.</p>
 */
final class ResolveBiomeClassification {
   private static final String RESOURCE_PATH = "tellus/biome/resolve_biome_classification.csv";
   private static final Map<ResolveBiome, Map<Integer, Map<String, ResourceKey<Biome>>>> RULES = new HashMap<>();
   private static final Set<ResourceKey<Biome>> ALL_BIOMES = new HashSet<>();
   private static volatile boolean loaded;

   private ResolveBiomeClassification() {
   }

   static ResourceKey<Biome> findBiomeKey(int esaCode, String koppenCode, ResolveEcoregion ecoregion) {
      ensureLoaded();
      if (ecoregion == null || !ecoregion.available()) {
         return null;
      }

      Map<Integer, Map<String, ResourceKey<Biome>>> byEsa = RULES.get(ecoregion.biome());
      if (byEsa == null) {
         return null;
      }
      Map<String, ResourceKey<Biome>> byKoppen = byEsa.get(esaCode);
      if (byKoppen == null) {
         return null;
      }

      if (koppenCode == null || koppenCode.isBlank()) {
         ResourceKey<Biome> none = byKoppen.get("NONE");
         return none == null ? byKoppen.get("*") : none;
      }

      String normalized = koppenCode.trim().toUpperCase(Locale.ROOT);
      ResourceKey<Biome> exact = byKoppen.get(normalized);
      if (exact != null) {
         return exact;
      }
      for (int prefixLength = normalized.length(); prefixLength >= 1; prefixLength--) {
         ResourceKey<Biome> prefix = byKoppen.get(normalized.substring(0, prefixLength) + "*");
         if (prefix != null) {
            return prefix;
         }
      }
      return byKoppen.get("*");
   }

   static Set<ResourceKey<Biome>> allBiomeKeys() {
      ensureLoaded();
      return Set.copyOf(ALL_BIOMES);
   }

   private static void ensureLoaded() {
      if (!loaded) {
         synchronized (ResolveBiomeClassification.class) {
            if (!loaded) {
               load();
               loaded = true;
            }
         }
      }
   }

   private static void load() {
      try (InputStream input = ResolveBiomeClassification.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
         if (input == null) {
            Tellus.LOGGER.warn("RESOLVE biome classification mapping not found at {}", RESOURCE_PATH);
            return;
         }

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
               Tellus.LOGGER.warn("RESOLVE biome classification mapping is empty");
               return;
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
               lineNumber++;
               line = line.trim();
               if (line.isEmpty() || line.startsWith("#")) {
                  continue;
               }

               List<String> fields = BiomeClassification.parseCsvLine(line);
               if (fields.size() < 4) {
                  Tellus.LOGGER.warn("Skipping invalid RESOLVE biome rule at line {}", lineNumber);
                  continue;
               }

               ResolveBiome resolveBiome;
               int esaCode;
               try {
                  resolveBiome = ResolveBiome.valueOf(fields.get(0).trim().toUpperCase(Locale.ROOT));
                  esaCode = Integer.parseInt(fields.get(1).trim());
               } catch (IllegalArgumentException error) {
                  Tellus.LOGGER.warn("Skipping invalid RESOLVE biome rule at line {}", lineNumber, error);
                  continue;
               }

               String pattern = fields.get(2).trim().toUpperCase(Locale.ROOT);
               if (!isValidKoppenPattern(pattern)) {
                  Tellus.LOGGER.warn("Skipping invalid Köppen pattern '{}' at line {}", pattern, lineNumber);
                  continue;
               }

               String biomeId = fields.get(3).trim();
               if (biomeId.isEmpty()) {
                  continue;
               }
               ResourceKey<Biome> biomeKey = BiomeClassification.toBiomeKey(biomeId);
               Map<String, ResourceKey<Biome>> patterns = RULES
                  .computeIfAbsent(resolveBiome, unused -> new HashMap<>())
                  .computeIfAbsent(esaCode, unused -> new HashMap<>());
               ResourceKey<Biome> previous = patterns.put(pattern, biomeKey);
               if (previous != null && !previous.equals(biomeKey)) {
                  Tellus.LOGGER.warn(
                     "RESOLVE biome rule {} / ESA {} / {} was replaced at line {}",
                     resolveBiome,
                     esaCode,
                     pattern,
                     lineNumber
                  );
               }
               ALL_BIOMES.add(biomeKey);
            }
         }
      } catch (IOException error) {
         Tellus.LOGGER.warn("Failed to read RESOLVE biome classification mapping", error);
      }
   }

   private static boolean isValidKoppenPattern(String pattern) {
      if ("*".equals(pattern) || "NONE".equals(pattern)) {
         return true;
      }
      int wildcard = pattern.indexOf('*');
      if (wildcard >= 0 && wildcard != pattern.length() - 1) {
         return false;
      }
      int letters = wildcard >= 0 ? wildcard : pattern.length();
      if (letters == 0) {
         return false;
      }
      for (int i = 0; i < letters; i++) {
         if (pattern.charAt(i) < 'A' || pattern.charAt(i) > 'Z') {
            return false;
         }
      }
      return true;
   }
}
