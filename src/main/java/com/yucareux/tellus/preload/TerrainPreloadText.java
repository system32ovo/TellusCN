package com.yucareux.tellus.preload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Converts the preload worker's locale-neutral progress snapshots into client-localized text. */
public final class TerrainPreloadText {
   private static final Pattern BUILDING_PACKAGE = Pattern.compile("Building fast terrain package \\(([0-9]+)\\/([0-9]+) rows\\)");
   private static final Pattern AREA_SUMMARY = Pattern.compile(
      "([0-9]+) x ([0-9]+) chunks, lat (-?[0-9]+(?:\\.[0-9]+)?)\\.\\.(-?[0-9]+(?:\\.[0-9]+)?), lon (-?[0-9]+(?:\\.[0-9]+)?)\\.\\.(-?[0-9]+(?:\\.[0-9]+)?)"
   );
   private static final Pattern OCEAN_CLIMATE = Pattern.compile("Ocean climate ([0-9]+)\\/([0-9]+)");
   private static final Pattern CACHED_TILE = Pattern.compile("Cached (.+) tile ([0-9]+)\\/([0-9]+) \\((.+)\\)");
   private static final Pattern CACHED_BLOCK = Pattern.compile("Cached (.+) block ([0-9]+)\\/([0-9]+) \\((.+)\\)");
   private static final Pattern LOADED_TILE = Pattern.compile("Loaded (.+) tile ([0-9]+)\\/([0-9]+) \\((.+)\\)");
   private static final Map<String, String> STATUS_KEYS = Map.ofEntries(
      Map.entry("Idle", "tellus.preload.status.idle"),
      Map.entry("Ready", "tellus.preload.status.ready"),
      Map.entry("Preload failed", "tellus.preload.status.failed"),
      Map.entry("Cancelling preload", "tellus.preload.status.cancelling"),
      Map.entry("Downloading data", "tellus.preload.status.downloading"),
      Map.entry("Terrain data cached", "tellus.preload.status.complete"),
      Map.entry("Cancelled", "tellus.preload.status.cancelled"),
      Map.entry("Processing terrain data", "tellus.preload.status.processing"),
      Map.entry("Saving terrain data", "tellus.preload.status.saving")
   );
   private static final Map<String, String> DETAIL_KEYS = Map.ofEntries(
      Map.entry("Stopping workers and cleaning the unfinished package", "tellus.preload.detail.stopping"),
      Map.entry("Planning terrain source coverage", "tellus.preload.detail.planning"),
      Map.entry(
         "Downloaded source files are ready; the area is too large for a processed terrain package at this scale.",
         "tellus.preload.detail.complete_sources"
      ),
      Map.entry(
         "Downloaded sources and the processed terrain package are ready for fast world generation.",
         "tellus.preload.detail.complete_package"
      ),
      Map.entry("Preload stopped. Completed downloads remain cached.", "tellus.preload.detail.cancelled"),
      Map.entry("Compressing fast terrain package", "tellus.preload.detail.compressing_package"),
      Map.entry("Skipping land cover; selected area is outside source coverage", "tellus.preload.detail.skip_land_cover"),
      Map.entry("Skipping land mask preload because land mask data is unavailable", "tellus.preload.detail.skip_land_mask"),
      Map.entry("Skipping DEM elevation preload because world scale is invalid", "tellus.preload.detail.skip_dem_scale"),
      Map.entry("No DEM elevation tiles intersect the selected area", "tellus.preload.detail.no_dem_tiles"),
      Map.entry("Skipping OSM infrastructure tiles because the source is unavailable", "tellus.preload.detail.skip_infrastructure"),
      Map.entry("Skipping Overture water tiles because the source is unavailable", "tellus.preload.detail.skip_water"),
      Map.entry("Skipping OSM sand tiles because the source is unavailable", "tellus.preload.detail.skip_sand"),
      Map.entry("Skipping OSM building tiles because the source is unavailable", "tellus.preload.detail.skip_buildings"),
      Map.entry("Skipping OSM road tiles because the source is unavailable", "tellus.preload.detail.skip_roads")
   );
   private static final Map<String, String> SOURCE_KEYS = Map.ofEntries(
      Map.entry("DEM elevation", "tellus.preload.source.dem"),
      Map.entry("ESA WorldCover land cover", "tellus.preload.source.land_cover"),
      Map.entry("ETH canopy height", "tellus.preload.source.canopy_height"),
      Map.entry("land mask", "tellus.preload.source.land_mask"),
      Map.entry("OSM sand", "tellus.preload.source.sand"),
      Map.entry("Overture water", "tellus.preload.source.water"),
      Map.entry("OSM roads", "tellus.preload.source.roads"),
      Map.entry("OSM infrastructure", "tellus.preload.source.infrastructure"),
      Map.entry("OSM buildings", "tellus.preload.source.buildings")
   );
   private static final Map<String, String> DOWNLOAD_PREFIX_KEYS = Map.ofEntries(
      Map.entry(" ESA WorldCover data blocks", "tellus.preload.detail.downloading_land_cover"),
      Map.entry(" Overture land-cover source tiles", "tellus.preload.detail.downloading_land_cover_fallback"),
      Map.entry(" land-mask source tiles", "tellus.preload.detail.downloading_land_mask"),
      Map.entry(" DEM source tiles with bounded parallelism", "tellus.preload.detail.downloading_dem"),
      Map.entry(" OSM infrastructure source tiles", "tellus.preload.detail.downloading_infrastructure"),
      Map.entry(" Overture water source tiles", "tellus.preload.detail.downloading_water"),
      Map.entry(" OSM sand source tiles", "tellus.preload.detail.downloading_sand"),
      Map.entry(" OSM building source tiles", "tellus.preload.detail.downloading_buildings"),
      Map.entry(" OSM road source tiles", "tellus.preload.detail.downloading_roads")
   );
   private static final Map<String, String> LOADING_PREFIX_KEYS = Map.ofEntries(
      Map.entry(" ETH canopy-height source tiles", "tellus.preload.detail.loading_canopy_height"),
      Map.entry(" OSM building source tiles", "tellus.preload.detail.loading_buildings")
   );
   private static final Map<String, String> TILE_SOURCE_KEYS = Map.ofEntries(
      Map.entry("WorldCover", "tellus.preload.source.land_cover"),
      Map.entry("Overture land-cover", "tellus.preload.source.land_cover_fallback"),
      Map.entry("ETH canopy-height", "tellus.preload.source.canopy_height"),
      Map.entry("land-mask", "tellus.preload.source.land_mask"),
      Map.entry("DEM source", "tellus.preload.source.dem"),
      Map.entry("OSM infrastructure", "tellus.preload.source.infrastructure"),
      Map.entry("Overture water", "tellus.preload.source.water"),
      Map.entry("OSM sand", "tellus.preload.source.sand"),
      Map.entry("OSM building", "tellus.preload.source.buildings"),
      Map.entry("OSM road", "tellus.preload.source.roads")
   );

   private TerrainPreloadText() {
   }

   public static Component status(String status) {
      return translateExact(status, STATUS_KEYS);
   }

   public static Component detail(String detail) {
      Component exact = translateExactOrNull(detail, DETAIL_KEYS);
      if (exact != null) {
         return exact;
      }
      if (detail == null || detail.isBlank()) {
         return Component.empty();
      }

      Matcher packageMatcher = BUILDING_PACKAGE.matcher(detail);
      if (packageMatcher.matches()) {
         return Component.translatable("tellus.preload.detail.building_package", packageMatcher.group(1), packageMatcher.group(2));
      }
      Matcher areaMatcher = AREA_SUMMARY.matcher(detail);
      if (areaMatcher.matches()) {
         return Component.translatable(
            "tellus.preload.area_summary",
            areaMatcher.group(1),
            areaMatcher.group(2),
            areaMatcher.group(3),
            areaMatcher.group(4),
            areaMatcher.group(5),
            areaMatcher.group(6)
         );
      }
      Matcher oceanMatcher = OCEAN_CLIMATE.matcher(detail);
      if (oceanMatcher.matches()) {
         return Component.translatable("tellus.preload.detail.ocean_climate", oceanMatcher.group(1), oceanMatcher.group(2));
      }
      if (detail.startsWith("Downloading ")) {
         String remainder = detail.substring("Downloading ".length());
         for (Map.Entry<String, String> entry : DOWNLOAD_PREFIX_KEYS.entrySet()) {
            if (remainder.endsWith(entry.getKey())) {
               String count = remainder.substring(0, remainder.length() - entry.getKey().length());
               return Component.translatable(entry.getValue(), count);
            }
         }
      }
      if (detail.startsWith("Loading ")) {
         String remainder = detail.substring("Loading ".length());
         for (Map.Entry<String, String> entry : LOADING_PREFIX_KEYS.entrySet()) {
            if (remainder.endsWith(entry.getKey())) {
               String count = remainder.substring(0, remainder.length() - entry.getKey().length());
               return Component.translatable(entry.getValue(), count);
            }
         }
      }

      Component cached = translateTileProgress(detail, CACHED_TILE, "tellus.preload.detail.cached_tile");
      if (cached != null) {
         return cached;
      }
      Component cachedBlock = translateTileProgress(detail, CACHED_BLOCK, "tellus.preload.detail.cached_block");
      if (cachedBlock != null) {
         return cachedBlock;
      }
      Component loaded = translateTileProgress(detail, LOADED_TILE, "tellus.preload.detail.loaded_tile");
      return loaded == null ? Component.translatable("tellus.preload.detail.unexpected_error") : loaded;
   }

   public static Component sources(String sourceDetail) {
      if (sourceDetail == null || sourceDetail.isBlank()) {
         return Component.empty();
      }
      List<Component> translated = new ArrayList<>();
      for (String source : sourceDetail.split(", ")) {
         String key = SOURCE_KEYS.get(source);
         translated.add(key == null ? Component.literal(source) : Component.translatable(key));
      }
      Component joined = Component.empty();
      for (int index = 0; index < translated.size(); index++) {
         if (index > 0) {
            joined = joined.copy().append(Component.literal(", "));
         }
         joined = joined.copy().append(translated.get(index));
      }
      return joined;
   }

   public static Component areaSummary(TerrainPreloadArea area) {
      return Component.translatable(
         "tellus.preload.area_summary",
         area.chunkWidth(),
         area.chunkDepth(),
         decimal(area.southLatitude()),
         decimal(area.northLatitude()),
         decimal(area.westLongitude()),
         decimal(area.eastLongitude())
      );
   }

   private static Component translateTileProgress(String detail, Pattern pattern, String key) {
      Matcher matcher = pattern.matcher(detail);
      if (!matcher.matches()) {
         return null;
      }
      String sourceKey = TILE_SOURCE_KEYS.get(matcher.group(1));
      Component source = sourceKey == null ? Component.literal(matcher.group(1)) : Component.translatable(sourceKey);
      return Component.translatable(key, source, matcher.group(2), matcher.group(3), matcher.group(4));
   }

   private static Component translateExact(String value, Map<String, String> keys) {
      Component translated = translateExactOrNull(value, keys);
      return translated == null ? Component.literal(value == null ? "" : value) : translated;
   }

   private static Component translateExactOrNull(String value, Map<String, String> keys) {
      if (value == null) {
         return null;
      }
      String key = keys.get(value);
      return key == null ? null : Component.translatable(key);
   }

   private static String decimal(double value) {
      return String.format(Locale.ROOT, "%.5f", value);
   }
}
