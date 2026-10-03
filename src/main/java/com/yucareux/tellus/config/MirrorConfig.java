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
   
   // 配置文件名（存放在 Fabric 的 config 目录下）
   private static final String CONFIG_FILE = "telluscn-mirror.properties";
   private static final Object LOCK = new Object();

   /**
    * 解析镜像配置文件路径。
    *
    * 喵~防御：Fabric 加载器在单元测试、数据生成等场景下可能尚未初始化，
    * 此时 getConfigDir() 会返回 null，直接 resolve 会抛空指针，并连带拖垮调用方的整条静态初始化链
    * （例如世界生成器在静态字段里创建数据源）。这里返回 null 表示"当前没有可用的配置文件"，
    * 读写都会被安全跳过，内存中的默认配置照常生效。
    */
   private static Path resolveConfigPath() {
      try {
         // 向 Fabric 加载器索取 config 目录；未初始化时为 null
         Path configDir = FabricLoader.getInstance().getConfigDir();
         return configDir == null ? null : configDir.resolve(CONFIG_FILE);
      } catch (Throwable error) {
         // 喵~防御：加载器未初始化时连 getInstance 都可能抛异常，统一降级成"无配置文件"
         return null;
      }
   }
   
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
    * 生成一组可直接写进启动器的 JVM 参数（-D形式）。
    *
    * 用途：不方便使用游戏内镜像设置界面时，可以把这组参数贴进 HMCL / PCL2 等启动器。
    * 说明：只列出当前版本真正会读取的端点键；上游 0.8.x 已把高程源换成
    * Mapterhorn(陆地)/OpenWaters(海面)、地表覆盖换成 Overture 与 ESA WorldCover COG，
    * 旧的 elevation / copernicus / usgs / japangsi / arcticdem / rema / s3proxy 键已不再生效，故不再输出。
    */
   public static String[] getAllEndpointArgs() {
      String domain = getActiveDomain();
      if (domain.isBlank()) {
         return new String[0];
      }

      return new String[] {
         "-Dtellus.weather.endpoint=" + domain + "/weather",
         "-Dtellus.geocoding.endpoint=" + domain + "/geocoding",
         "-Dtellus.osm.overpass.endpoints=" + domain + "/overpass",
         "-Dtellus.landmask.baseUrl=" + domain + "/landmask/",
         "-Dtellus.map.tiles.endpoint=" + domain + "/tiles",
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
      // 喵~防御：拿不到配置文件路径时直接跳过读取，保留内存中的默认值
      Path configPath = resolveConfigPath();
      if (configPath == null) {
         return;
      }
      // 配置文件还不存在时按默认值处理
      if (!Files.exists(configPath)) {
         return;
      }

      Properties props = new Properties();
      try (InputStream input = Files.newInputStream(configPath)) {
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
      // 喵~防御：拿不到配置文件路径时直接跳过写入，避免空指针；内存配置依然生效
      Path configPath = resolveConfigPath();
      if (configPath == null) {
         return;
      }
      Files.createDirectories(Objects.requireNonNull(configPath.getParent(), "configParent"));

      Properties props = new Properties();
      props.setProperty(KEY_ENABLED, String.valueOf(enabled));
      props.setProperty(KEY_CUSTOM_DOMAIN, customDomain);
      props.setProperty(KEY_SELECTED_PRESET, String.valueOf(selectedPreset));

      try (OutputStream output = Files.newOutputStream(configPath)) {
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
