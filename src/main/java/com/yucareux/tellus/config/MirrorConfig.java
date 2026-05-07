/*
 * TellusCN - Chinese Mirror & CDN Support for Tellus Mod
 * Copyright (c) 2026 BlackHoleEra-Team
 * 
 * Original Tellus Mod Copyright (c) Yucareux
 * Licensed under LGPL-3.0
 */

package com.yucareux.tellus.config;

import com.yucareux.tellus.Tellus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * TellusCN 镜像配置管理类
 * 
 * 管理所有数据源的镜像设置，支持：
 * 1. 官方预设 Workers 域名
 * 2. 自定义 Workers 域名
 * 3. 关闭镜像使用原版源
 * 
 * 优先级：游戏内设置 > JVM 启动参数 > 默认官方源
 */
public final class MirrorConfig {
   
   private static final String CONFIG_FILE = "telluscn-mirror.properties";
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
   private static final Object LOCK = new Object();
   
   // 配置项 Key
   private static final String KEY_ENABLED = "mirror.enabled";
   private static final String KEY_CUSTOM_DOMAIN = "mirror.custom_domain";
   private static final String KEY_SELECTED_PRESET = "mirror.selected_preset";
   
   // 官方预设 Workers 域名（国内加速）
   public static final String[] OFFICIAL_PRESETS = {
      "https://telluscn.ggff.net",  // Cloudflare Workers 官方节点
   };
   
   // 当前配置
   private static volatile boolean enabled = true;
   private static volatile String customDomain = "";
   private static volatile int selectedPreset = 0;
   private static volatile boolean loaded = false;
   
   private MirrorConfig() {
   }
   
   /**
    * 获取当前生效的 Workers 基础域名
    * 优先级：自定义 > 预设 > 空（使用原版）
    */
   public static String getActiveDomain() {
      if (!isEnabled()) {
         return "";
      }
      
      String custom = getCustomDomain();
      if (!custom.isBlank()) {
         return normalizeDomain(custom);
      }
      
      int presetIndex = getSelectedPreset();
      if (presetIndex >= 0 && presetIndex < OFFICIAL_PRESETS.length) {
         return OFFICIAL_PRESETS[presetIndex];
      }
      
      return "";
   }
   
   /**
    * 获取指定数据源的完整端点 URL
    */
   public static String getEndpoint(String source) {
      String domain = getActiveDomain();
      if (domain.isBlank()) {
         return ""; // 返回空表示使用原版默认源
      }
      return domain + "/" + source;
   }
   
   /**
    * 获取所有数据源的端点配置（用于 JVM 参数格式）
    */
   public static String[] getAllEndpointArgs() {
      String domain = getActiveDomain();
      if (domain.isBlank()) {
         return new String[0];
      }
      
      return new String[] {
         "-Dtellus.elevation.endpoint=" + domain + "/elevation",
         "-Dtellus.copernicus30.endpoint=" + domain + "/copernicus30",
         "-Dtellus.copernicus90.endpoint=" + domain + "/copernicus90",
         "-Dtellus.usgs.endpoint=" + domain + "/usgs",
         "-Dtellus.japangsi.endpoint=" + domain + "/japangsi",
         "-Dtellus.arcticdem.endpoint=" + domain + "/arcticdem",
         "-Dtellus.rema.endpoint=" + domain + "/rema",
         "-Dtellus.landcover.endpoint=" + domain + "/landcover",
         "-Dtellus.weather.endpoint=" + domain + "/weather",
         "-Dtellus.geocoding.endpoint=" + domain + "/geocoding",
         "-Dtellus.osm.overpass.endpoints=" + domain + "/overpass",
         "-Dtellus.landmask.baseUrl=" + domain + "/landmask/",
         "-Dtellus.s3proxy.endpoint=" + domain + "/s3",
         "-Dtellus.tiles.endpoint=" + domain + "/tiles",
         "-Dtellus.overture.roads.endpoint=" + domain + "/overture/roads",
         "-Dtellus.overture.buildings.endpoint=" + domain + "/overture/buildings",
         "-Dtellus.overture.water.endpoint=" + domain + "/overture/water",
         "-Dtellus.overture.sand.endpoint=" + domain + "/overture/sand",
      };
   }
   
   // ============ Getter / Setter ============
   
   public static boolean isEnabled() {
      ensureLoaded();
      return enabled;
   }
   
   public static void setEnabled(boolean value) {
      enabled = value;
      save();
   }
   
   public static String getCustomDomain() {
      ensureLoaded();
      return customDomain;
   }
   
   public static void setCustomDomain(String domain) {
      customDomain = normalize(domain);
      save();
   }
   
   public static int getSelectedPreset() {
      ensureLoaded();
      return selectedPreset;
   }
   
   public static void setSelectedPreset(int index) {
      selectedPreset = Math.max(0, Math.min(index, OFFICIAL_PRESETS.length - 1));
      save();
   }
   
   public static String[] getOfficialPresets() {
      return OFFICIAL_PRESETS.clone();
   }
   
   // ============ 持久化 ============
   
   private static void ensureLoaded() {
      if (!loaded) {
         synchronized (LOCK) {
            if (!loaded) {
               load();
               loaded = true;
            }
         }
      }
   }
   
   private static void load() {
      if (!Files.exists(CONFIG_PATH)) {
         return;
      }
      
      Properties props = new Properties();
      try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
         props.load(input);
         
         enabled = Boolean.parseBoolean(props.getProperty(KEY_ENABLED, "true"));
         customDomain = normalize(props.getProperty(KEY_CUSTOM_DOMAIN, ""));
         selectedPreset = Integer.parseInt(props.getProperty(KEY_SELECTED_PRESET, "0"));
         
      } catch (IOException | NumberFormatException e) {
         Tellus.LOGGER.error("Failed to load mirror config", e);
      }
   }
   
   private static void save() {
      synchronized (LOCK) {
         try {
            saveLocked();
         } catch (IOException e) {
            Tellus.LOGGER.error("Failed to save mirror config", e);
         }
      }
   }
   
   private static void saveLocked() throws IOException {
      Files.createDirectories(Objects.requireNonNull(CONFIG_PATH.getParent(), "configParent"));
      
      Properties props = new Properties();
      props.setProperty(KEY_ENABLED, String.valueOf(enabled));
      props.setProperty(KEY_CUSTOM_DOMAIN, customDomain);
      props.setProperty(KEY_SELECTED_PRESET, String.valueOf(selectedPreset));
      
      try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
         props.store(output, "TellusCN Mirror Configuration - 镜像配置");
      }
   }
   
   // ============ 工具方法 ============
   
   private static String normalize(String value) {
      return value == null ? "" : value.trim();
   }
   
   private static String normalizeDomain(String domain) {
      String normalized = normalize(domain);
      // 移除末尾斜杠
      while (normalized.endsWith("/")) {
         normalized = normalized.substring(0, normalized.length() - 1);
      }
      // 确保以 https:// 开头
      if (!normalized.isBlank() && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
         normalized = "https://" + normalized;
      }
      return normalized;
   }
   
   /**
    * 重置所有配置
    */
   public static void reset() {
      enabled = false;
      customDomain = "";
      selectedPreset = 0;
      save();
   }
}
