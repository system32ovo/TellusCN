package com.yucareux.tellus.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalizationCoverageTest {
   // TellusCN：把 zh_cn 也纳入检查。上游原本只校验 es_es / ja_jp，
   // 导致 zh_cn 长期落后（曾只有 229 个 key，而 en_us 有 432 个），
   // 未翻译的条目在游戏里会直接显示英文原文。加入后四种语言必须始终与 en_us 保持一致。
   private static final List<String> LOCALES = List.of("es_es", "ja_jp", "zh_cn");
   private static final Pattern FORMAT_TOKEN = Pattern.compile("%(?:[0-9]+\\$)?(?:%|[A-Za-z])");

   @Test
   void shippedLocalesMatchEnglishKeysAndFormatTokens() throws IOException {
      JsonObject english = loadLocale("en_us");
      Set<String> englishKeys = english.keySet();

      for (String locale : LOCALES) {
         JsonObject translated = loadLocale(locale);
         assertEquals(englishKeys, translated.keySet(), locale + " keys differ from en_us");

         for (String key : englishKeys) {
            assertEquals(
               formatTokens(english.get(key)),
               formatTokens(translated.get(key)),
               locale + " format tokens differ for " + key
            );
         }
      }

      assertTrue(english.has("tellus.preload.source.canopy_height"));
      assertTrue(english.has("tellus.preload.detail.loading_canopy_height"));
   }

   private static JsonObject loadLocale(String locale) throws IOException {
      String path = "/assets/tellus/lang/" + locale + ".json";
      try (InputStream input = LocalizationCoverageTest.class.getResourceAsStream(path)) {
         assertNotNull(input, "Missing locale resource " + path);
         try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
         }
      }
   }

   private static List<String> formatTokens(JsonElement element) {
      Matcher matcher = FORMAT_TOKEN.matcher(element.getAsString());
      List<String> tokens = new ArrayList<>();
      while (matcher.find()) {
         tokens.add(matcher.group());
      }
      return tokens;
   }
}
