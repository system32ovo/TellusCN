/*
 * TellusCN - Chinese Mirror & CDN Support for Tellus Mod
 * Copyright (c) 2026 BlackHoleEra-Team
 *
 * Original Tellus Mod Copyright (c) Yucareux
 * Licensed under LGPL-3.0
 */

package com.yucareux.tellus.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Overture 数据发布版本与瓦片地址配置工具类
 *
 * 背景说明：Overture Maps 会滚动删除旧版本的 tiles 发布目录。
 * 一旦代码里写死的版本号被官方删除，PMTiles 文件头就会读取失败（HTTP 404），
 * 而数据源初始化时读不到文件头就会把自身标记为不可用，导致建筑、道路整体不再生成。
 * 这里把发布版本集中到一处，并允许通过 JVM 参数覆盖，方便日后快速修复。
 *
 * 输入：无（或一个瓦片文件名、一组候选地址）
 * 输出：当前生效的 Overture 版本号、拼接好的瓦片地址、去重后的候选地址列表
 * 边界条件：JVM 参数为空/纯空白时回退默认版本；文件名为空时抛异常；候选地址为空时返回空列表
 */
public final class OvertureDataConfig {

   // Overture 官方瓦片发布的根地址，建筑与道路数据都在这个 S3 桶下
   private static final String OVERTURE_TILES_BASE_URL = "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/";
   // 当前内置的默认 Overture 发布版本号，格式形如 2026-09-23.1
   public static final String DEFAULT_OVERTURE_RELEASE = "2026-09-23.1";
   // 用于覆盖发布版本的 JVM 参数名，用法：-Dtellus.overture.release=2026-09-23.1
   private static final String OVERTURE_RELEASE_PROPERTY = "tellus.overture.release";

   // 工具类不允许被实例化
   private OvertureDataConfig() {
   }

   /**
    * 取得当前生效的 Overture 发布版本号
    *
    * @return 版本号字符串，例如 2026-09-23.1
    */
   public static String overtureRelease() {
      // 读取主人可能通过 JVM 参数设置的覆盖版本
      String overrideRelease = System.getProperty(OVERTURE_RELEASE_PROPERTY);
      // 喵~防御：参数为 null、空串或纯空白时一律回退到内置默认版本，避免拼出非法网址
      if (overrideRelease == null || overrideRelease.isBlank()) {
         return DEFAULT_OVERTURE_RELEASE;
      }
      // 去掉首尾空白后再使用，防止主人手滑多打空格导致 404
      return overrideRelease.trim();
   }

   /**
    * 拼接某个图层的官方 PMTiles 地址
    *
    * @param tileFileName 图层文件名（不含 .pmtiles 后缀），例如 buildings、transportation
    * @return 完整的官方 PMTiles 网址
    */
   public static String officialTileUrl(String tileFileName) {
      // 喵~防御：文件名为 null 或空白时直接抛异常，避免拼出 ".../<release>/.pmtiles" 这种坏地址
      if (tileFileName == null || tileFileName.isBlank()) {
         throw new IllegalArgumentException("Overture tile file name must not be blank");
      }
      // 按 "根地址 + 版本号 + 文件名 + 后缀" 的顺序拼接完整网址
      return OVERTURE_TILES_BASE_URL + overtureRelease() + "/" + tileFileName.trim() + ".pmtiles";
   }

   /**
    * 组装按顺序尝试的数据源候选地址列表
    *
    * 主要给 PMTiles 读取器使用：先试主地址（通常是国内镜像），失败再试官方地址兜底。
    *
    * @param primaryUrl         主地址，通常是镜像地址，可以为空
    * @param officialFallbackUrl 兜底的官方地址，可以为空
    * @return 去重且已过滤空值的候选地址列表，可能为空列表
    */
   public static List<String> sourceCandidates(String primaryUrl, String officialFallbackUrl) {
      // 最多只有两个候选，所以按 2 预分配容量，避免扩容
      List<String> candidates = new ArrayList<>(2);
      // 先放入主地址，保证镜像优先
      addCandidateIfUsable(candidates, primaryUrl);
      // 再放入官方兜底地址
      addCandidateIfUsable(candidates, officialFallbackUrl);
      // 返回不可变副本，防止调用方意外修改内部状态
      return List.copyOf(candidates);
   }

   /**
    * 把一个地址加入候选列表（内部辅助方法）
    *
    * @param candidates 候选列表，会被就地修改
    * @param url        待加入的地址，可以为 null
    */
   private static void addCandidateIfUsable(List<String> candidates, String url) {
      // 喵~防御：地址为 null 或空白时直接跳过，避免无意义的重试请求
      if (url == null || url.isBlank()) {
         return;
      }
      // 去掉首尾空白，统一后续比较用的格式
      String normalizedUrl = url.trim();
      // 喵~防御：同名地址只保留一次，避免同一个地址被连续请求两遍浪费时间
      if (!candidates.contains(normalizedUrl)) {
         candidates.add(normalizedUrl);
      }
   }
}
