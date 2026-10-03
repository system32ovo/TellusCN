package com.yucareux.tellus.world.data.biome;

import com.yucareux.tellus.compat.MinecraftRelease;
import com.yucareux.tellus.Tellus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class BiomeClassification {
   private static final String RESOURCE_PATH = "tellus/biome/biome_classification_system.csv";
   private static final Map<Integer, Map<String, ResourceKey<Biome>>> BIOME_MAP = new HashMap<>();
   private static final Map<Integer, ResourceKey<Biome>> FALLBACK_MAP = new HashMap<>();
   private static final Set<ResourceKey<Biome>> ALL_BIOMES = new HashSet<>();
   private static boolean loaded;

   private BiomeClassification() {
   }

   public static ResourceKey<Biome> findBiomeKey(int esaCode, String koppenCode) {
      ensureLoaded();
      if (koppenCode == null) {
         return null;
      } else {
         Map<String, ResourceKey<Biome>> byKoppen = BIOME_MAP.get(esaCode);
         return byKoppen == null ? null : byKoppen.get(koppenCode.toUpperCase(Locale.ROOT));
      }
   }

   public static ResourceKey<Biome> findFallbackKey(int esaCode) {
      ensureLoaded();
      return FALLBACK_MAP.get(esaCode);
   }

   public static Set<ResourceKey<Biome>> allBiomeKeys() {
      ensureLoaded();
      return Set.copyOf(ALL_BIOMES);
   }

   private static void ensureLoaded() {
      if (!loaded) {
         synchronized (BiomeClassification.class) {
            if (!loaded) {
               load();
               loaded = true;
            }
         }
      }
   }

   private static void load() {
      InputStream input = BiomeClassification.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
      if (input == null) {
         Tellus.LOGGER.warn("Biome classification mapping not found at {}", RESOURCE_PATH);
      } else {
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            boolean header = true;

            String line;
            while ((line = reader.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty()) {
                  if (header) {
                     header = false;
                  } else {
                     List<String> fields = parseCsvLine(line);
                     if (fields.size() >= 5) {
                        int esaCode;
                        try {
                           esaCode = Integer.parseInt(fields.get(0).trim());
                        } catch (NumberFormatException var11) {
                           continue;
                        }

                        String koppenCode = fields.get(2).trim();
                        String biomeId = fields.get(4).trim();
                        if (!biomeId.isEmpty()) {
                           ResourceKey<Biome> biomeKey = toBiomeKey(biomeId);
                           ALL_BIOMES.add(biomeKey);
                           if ("NONE".equalsIgnoreCase(koppenCode)) {
                              FALLBACK_MAP.put(esaCode, biomeKey);
                           } else {
                              String normalized = koppenCode.toUpperCase(Locale.ROOT);
                              BIOME_MAP.computeIfAbsent(esaCode, unused -> new HashMap<>()).put(normalized, biomeKey);
                           }
                        }
                     }
                  }
               }
            }
         } catch (IOException var13) {
            Tellus.LOGGER.warn("Failed to read biome classification mapping", var13);
         }
      }
   }

   public static ResourceKey<Biome> toBiomeKey(String biomeId) {
      return MinecraftRelease.biomeKey(biomeId);
   }

   static List<String> parseCsvLine(String line) {
      List<String> fields = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      boolean inQuotes = false;

      for (int i = 0; i < line.length(); i++) {
         char ch = line.charAt(i);
         if (ch == '"') {
            if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
               current.append('"');
               i++;
            } else {
               inQuotes = !inQuotes;
            }
         } else if (ch == ',' && !inQuotes) {
            fields.add(current.toString());
            current.setLength(0);
         } else {
            current.append(ch);
         }
      }

      fields.add(current.toString());
      return fields;
   }
}
