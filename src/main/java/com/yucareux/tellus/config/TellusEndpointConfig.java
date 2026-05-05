/*
 * TellusCN - Chinese Mirror & CDN Support for Tellus Mod
 * Copyright (c) 2026 BlackHoleEra-Team
 * 
 * Original Tellus Mod Copyright (c) Yucareux
 * Licensed under LGPL-3.0
 */

package com.yucareux.tellus.config;

/**
 * Tellus 端点配置工具类
 * 
 * 提供统一的数据源端点获取方法
 * 优先级：游戏内设置 > JVM 启动参数 > 默认官方源
 */
public final class TellusEndpointConfig {
   
   private TellusEndpointConfig() {
   }
   
   /**
    * 获取端点 URL
    * 
    * @param systemPropertyKey JVM 参数 key
    * @param defaultValue 默认值
    * @return 实际使用的端点 URL
    */
   public static String getEndpoint(String systemPropertyKey, String defaultValue) {
      // 1. 首先检查游戏内配置（如果启用了镜像）
      if (MirrorConfig.isEnabled()) {
         String mirrorEndpoint = getMirrorEndpoint(systemPropertyKey);
         if (!mirrorEndpoint.isBlank()) {
            return mirrorEndpoint;
         }
      }
      
      // 2. 检查 JVM 启动参数
      String jvmValue = System.getProperty(systemPropertyKey);
      if (jvmValue != null && !jvmValue.isBlank()) {
         return jvmValue;
      }
      
      // 3. 返回默认值
      return defaultValue;
   }
   
   /**
    * 根据系统属性 key 获取对应的镜像端点
    */
   private static String getMirrorEndpoint(String systemPropertyKey) {
      String domain = MirrorConfig.getActiveDomain();
      if (domain.isBlank()) {
         return "";
      }
      
      // 根据系统属性 key 映射到对应的路由
      return switch (systemPropertyKey) {
         case "tellus.elevation.endpoint" -> domain + "/elevation";
         case "tellus.copernicus30.endpoint" -> domain + "/copernicus30";
         case "tellus.copernicus90.endpoint" -> domain + "/copernicus90";
         case "tellus.usgs.endpoint" -> domain + "/usgs";
         case "tellus.japangsi.endpoint" -> domain + "/japangsi";
         case "tellus.arcticdem.endpoint" -> domain + "/arcticdem";
         case "tellus.rema.endpoint" -> domain + "/rema";
         case "tellus.landcover.endpoint" -> domain + "/landcover";
         case "tellus.weather.endpoint" -> domain + "/weather";
         case "tellus.geocoding.endpoint" -> domain + "/geocoding";
         case "tellus.osm.overpass.endpoints" -> domain + "/overpass";
         case "tellus.landmask.baseUrl" -> domain + "/landmask/";
         case "tellus.s3proxy.endpoint" -> domain + "/s3";
         case "tellus.map.tiles.endpoint" -> domain + "/tiles";
         case "tellus.overture.roads.endpoint" -> domain + "/overture/roads";
         case "tellus.overture.buildings.endpoint" -> domain + "/overture/buildings";
         case "tellus.overture.water.endpoint" -> domain + "/overture/water";
         case "tellus.overture.sand.endpoint" -> domain + "/overture/sand";
         default -> "";
      };
   }
   
   /**
    * 获取 Terrarium 高程端点
    */
   public static String getElevationEndpoint(String defaultValue) {
      return getEndpoint("tellus.elevation.endpoint", defaultValue);
   }
   
   /**
    * 获取 Copernicus 30m 端点
    */
   public static String getCopernicus30Endpoint(String defaultValue) {
      return getEndpoint("tellus.copernicus30.endpoint", defaultValue);
   }
   
   /**
    * 获取 Copernicus 90m 端点
    */
   public static String getCopernicus90Endpoint(String defaultValue) {
      return getEndpoint("tellus.copernicus90.endpoint", defaultValue);
   }
   
   /**
    * 获取 USGS 端点
    */
   public static String getUsgsEndpoint(String defaultValue) {
      return getEndpoint("tellus.usgs.endpoint", defaultValue);
   }
   
   /**
    * 获取 Japan GSI 端点
    */
   public static String getJapanGsiEndpoint(String defaultValue) {
      return getEndpoint("tellus.japangsi.endpoint", defaultValue);
   }
   
   /**
    * 获取 ArcticDEM 端点
    */
   public static String getArcticDemEndpoint(String defaultValue) {
      return getEndpoint("tellus.arcticdem.endpoint", defaultValue);
   }
   
   /**
    * 获取 REMA 端点
    */
   public static String getRemaEndpoint(String defaultValue) {
      return getEndpoint("tellus.rema.endpoint", defaultValue);
   }
   
   /**
    * 获取地表覆盖端点
    */
   public static String getLandCoverEndpoint(String defaultValue) {
      return getEndpoint("tellus.landcover.endpoint", defaultValue);
   }
   
   /**
    * 获取天气端点
    */
   public static String getWeatherEndpoint(String defaultValue) {
      return getEndpoint("tellus.weather.endpoint", defaultValue);
   }
   
   /**
    * 获取地理编码端点
    */
   public static String getGeocodingEndpoint(String defaultValue) {
      return getEndpoint("tellus.geocoding.endpoint", defaultValue);
   }
   
   /**
    * 获取 Overpass 端点
    */
   public static String getOverpassEndpoint(String defaultValue) {
      return getEndpoint("tellus.osm.overpass.endpoints", defaultValue);
   }
   
   /**
    * 获取 Land Mask 基础 URL
    */
   public static String getLandMaskBaseUrl(String defaultValue) {
      return getEndpoint("tellus.landmask.baseUrl", defaultValue);
   }
   
   /**
    * 获取 S3 代理端点
    */
   public static String getS3ProxyEndpoint(String defaultValue) {
      return getEndpoint("tellus.s3proxy.endpoint", defaultValue);
   }
   
   /**
    * 获取地图瓦片端点
    */
   public static String getMapTilesEndpoint(String defaultValue) {
      return getEndpoint("tellus.map.tiles.endpoint", defaultValue);
   }
   
   /**
    * 获取 Overture Roads 端点
    */
   public static String getOvertureRoadsEndpoint(String defaultValue) {
      return getEndpoint("tellus.overture.roads.endpoint", defaultValue);
   }
   
   /**
    * 获取 Overture Buildings 端点
    */
   public static String getOvertureBuildingsEndpoint(String defaultValue) {
      return getEndpoint("tellus.overture.buildings.endpoint", defaultValue);
   }
   
   /**
    * 获取 Overture Water 端点
    */
   public static String getOvertureWaterEndpoint(String defaultValue) {
      return getEndpoint("tellus.overture.water.endpoint", defaultValue);
   }
   
   /**
    * 获取 Overture Sand 端点
    */
   public static String getOvertureSandEndpoint(String defaultValue) {
      return getEndpoint("tellus.overture.sand.endpoint", defaultValue);
   }
}
