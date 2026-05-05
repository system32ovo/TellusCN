package com.yucareux.tellus.config;

import com.yucareux.tellus.Tellus;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * DNS 解析器配置管理类
 * 
 * 管理自定义 DNS 解析设置：
 * 1. 启用/禁用自定义 DNS
 * 2. 选择预设 DNS（1.1.1.1, 8.8.8.8, 8.8.4.4）
 * 3. 配置域名匹配规则（白名单）
 * 
 * 通过 NameService SPI 实现，只影响匹配的域名
 */
public final class DnsResolverConfig {
   
   private static final String CONFIG_FILE = "telluscn-dns.properties";
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
   private static final Object LOCK = new Object();
   
   // 配置项 Key
   private static final String KEY_ENABLED = "dns.enabled";
   private static final String KEY_DNS_SERVER = "dns.server";
   private static final String KEY_CUSTOM_DNS = "dns.custom";
   private static final String KEY_DOMAIN_PATTERNS = "dns.domain.patterns";
   
   // 预设 DNS 服务器
   public static final String[] DNS_PRESETS = {
      "1.1.1.1",      // Cloudflare
      "8.8.8.8",      // Google
      "8.8.4.4",      // Google 备用
      "223.5.5.5",    // 阿里 DNS
      "119.29.29.29", // 腾讯 DNS
   };
   
   // 默认域名匹配规则（Tellus 相关域名）
   public static final String[] DEFAULT_DOMAIN_PATTERNS = {
      "hopto.org",
      "workers.dev",
      "cloudflare.com",
      "amazonaws.com",
      "openstreetmap.org",
      "overpass-api.de",
      "open-meteo.com",
      "nominatim.openstreetmap.org",
   };
   
   // 当前配置
   private static volatile boolean enabled = false;
   private static volatile int selectedDns = 0;  // 默认使用 1.1.1.1
   private static volatile String customDns = "";
   private static volatile List<String> domainPatterns = Arrays.asList(DEFAULT_DOMAIN_PATTERNS);
   private static volatile boolean loaded = false;
   
   private DnsResolverConfig() {
   }
   
   /**
    * 获取当前生效的 DNS 服务器地址
    */
   public static String getActiveDnsServer() {
      if (!isEnabled()) {
         return "";
      }
      
      String custom = getCustomDns();
      if (!custom.isBlank()) {
         return custom.trim();
      }
      
      int index = getSelectedDns();
      if (index >= 0 && index < DNS_PRESETS.length) {
         return DNS_PRESETS[index];
      }
      
      return DNS_PRESETS[0]; // 默认返回 1.1.1.1
   }
   
   /**
    * 检查域名是否应该使用自定义 DNS 解析
    */
   public static boolean shouldUseCustomDns(String hostname) {
      if (!isEnabled() || hostname == null || hostname.isBlank()) {
         return false;
      }
      
      String lowerHost = hostname.toLowerCase();
      for (String pattern : getDomainPatterns()) {
         if (lowerHost.contains(pattern.toLowerCase())) {
            return true;
         }
      }
      return false;
   }
   
   /**
    * 使用自定义 DNS 解析域名
    */
   public static InetAddress[] resolveWithCustomDns(String hostname) throws UnknownHostException {
      String dnsServer = getActiveDnsServer();
      if (dnsServer.isBlank()) {
         throw new UnknownHostException("Custom DNS not configured");
      }
      
      // 使用 DnsResolver SPI 进行解析
      return TellusNameService.resolveWithSpecificDns(hostname, dnsServer);
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
   
   public static int getSelectedDns() {
      ensureLoaded();
      return selectedDns;
   }
   
   public static void setSelectedDns(int index) {
      selectedDns = Math.max(0, Math.min(index, DNS_PRESETS.length - 1));
      save();
   }
   
   public static String getCustomDns() {
      ensureLoaded();
      return customDns;
   }
   
   public static void setCustomDns(String dns) {
      customDns = normalize(dns);
      save();
   }
   
   public static List<String> getDomainPatterns() {
      ensureLoaded();
      return domainPatterns;
   }
   
   public static void setDomainPatterns(List<String> patterns) {
      domainPatterns = patterns != null ? patterns : Arrays.asList(DEFAULT_DOMAIN_PATTERNS);
      save();
   }
   
   public static String[] getDnsPresets() {
      return DNS_PRESETS.clone();
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
         
         enabled = Boolean.parseBoolean(props.getProperty(KEY_ENABLED, "false"));
         selectedDns = Integer.parseInt(props.getProperty(KEY_DNS_SERVER, "0"));
         customDns = normalize(props.getProperty(KEY_CUSTOM_DNS, ""));
         
         String patternsStr = props.getProperty(KEY_DOMAIN_PATTERNS);
         if (patternsStr != null && !patternsStr.isBlank()) {
            domainPatterns = Arrays.asList(patternsStr.split(","));
         }
         
      } catch (IOException | NumberFormatException e) {
         Tellus.LOGGER.error("Failed to load DNS config", e);
      }
   }
   
   private static void save() {
      synchronized (LOCK) {
         try {
            saveLocked();
         } catch (IOException e) {
            Tellus.LOGGER.error("Failed to save DNS config", e);
         }
      }
   }
   
   private static void saveLocked() throws IOException {
      Files.createDirectories(Objects.requireNonNull(CONFIG_PATH.getParent(), "configParent"));
      
      Properties props = new Properties();
      props.setProperty(KEY_ENABLED, String.valueOf(enabled));
      props.setProperty(KEY_DNS_SERVER, String.valueOf(selectedDns));
      props.setProperty(KEY_CUSTOM_DNS, customDns);
      props.setProperty(KEY_DOMAIN_PATTERNS, String.join(",", domainPatterns));
      
      try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
         props.store(output, "TellusCN DNS Resolver Configuration - DNS解析器配置");
      }
   }
   
   // ============ 工具方法 ============
   
   private static String normalize(String value) {
      return value != null ? value.trim() : "";
   }
}
