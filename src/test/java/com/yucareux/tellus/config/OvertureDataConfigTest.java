/*
 * TellusCN - Chinese Mirror & CDN Support for Tellus Mod
 * Copyright (c) 2026 BlackHoleEra-Team
 *
 * Original Tellus Mod Copyright (c) Yucareux
 * Licensed under LGPL-3.0
 */

package com.yucareux.tellus.config;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OvertureDataConfig 单元测试
 *
 * 覆盖场景：未设置 JVM 参数、设置正常参数、设置空白参数、文件名为 null/空串/纯空白、
 * 候选地址去重、候选地址空值过滤、首尾空白裁剪。
 * 该测试只依赖 JDK，不触碰 Minecraft 与 Fabric 类，因此可以在无游戏环境下直接运行。
 */
class OvertureDataConfigTest {

   // 每个用例前后都清理覆盖参数，避免用例之间互相污染
   private static final String RELEASE_PROPERTY = "tellus.overture.release";

   /**
    * 每个用例结束后清除 JVM 参数，保证测试互不影响
    */
   @AfterEach
   void clearReleaseProperty() {
      // 清掉可能被用例设置的覆盖版本
      System.clearProperty(RELEASE_PROPERTY);
   }

   /**
    * 正常场景：没有设置 JVM 参数时应使用内置默认版本
    */
   @Test
   void defaultReleaseUsedWhenPropertyAbsent() {
      // 未设置任何参数时读取版本号
      String release = OvertureDataConfig.overtureRelease();
      // 断言读到的是内置默认版本
      assertEquals(OvertureDataConfig.DEFAULT_OVERTURE_RELEASE, release);
   }

   /**
    * 正常场景：设置了合法的 JVM 参数时应使用覆盖值
    */
   @Test
   void propertyOverrideUsedWhenSet() {
      // 模拟主人通过 -Dtellus.overture.release 指定新版本
      System.setProperty(RELEASE_PROPERTY, "2027-01-15.2");
      // 读取版本号
      String release = OvertureDataConfig.overtureRelease();
      // 断言覆盖值生效
      assertEquals("2027-01-15.2", release);
   }

   /**
    * 边界场景：参数为纯空白时应回退到默认版本，而不是拼出带空格的坏地址
    */
   @Test
   void blankPropertyFallsBackToDefault() {
      // 设置一个只有空格的非法参数
      System.setProperty(RELEASE_PROPERTY, "   ");
      // 读取版本号
      String release = OvertureDataConfig.overtureRelease();
      // 断言仍然回退到默认版本
      assertEquals(OvertureDataConfig.DEFAULT_OVERTURE_RELEASE, release);
   }

   /**
    * 边界场景：参数首尾有多余空格时应被裁剪后再使用
    */
   @Test
   void propertyIsTrimmed() {
      // 设置一个首尾带空格的参数
      System.setProperty(RELEASE_PROPERTY, "  2026-10-01.0  ");
      // 读取版本号
      String release = OvertureDataConfig.overtureRelease();
      // 断言空格被裁掉
      assertEquals("2026-10-01.0", release);
   }

   /**
    * 正常场景：拼接出的官方地址应包含版本号、文件名与 .pmtiles 后缀
    */
   @Test
   void officialTileUrlUsesReleaseAndFileName() {
      // 固定版本号，保证断言结果稳定
      System.setProperty(RELEASE_PROPERTY, "2026-09-23.1");
      // 拼接建筑图层地址
      String url = OvertureDataConfig.officialTileUrl("buildings");
      // 断言地址完整且格式正确
      assertEquals(
         "https://overturemaps-extras-us-west-2.s3.us-west-2.amazonaws.com/tiles/2026-09-23.1/buildings.pmtiles",
         url
      );
   }

   /**
    * 非法场景：文件名为 null、空串或纯空白时应抛出异常
    */
   @Test
   void officialTileUrlRejectsBlankFileName() {
      // null 文件名应被拒绝
      assertThrows(IllegalArgumentException.class, () -> OvertureDataConfig.officialTileUrl(null));
      // 空串文件名应被拒绝
      assertThrows(IllegalArgumentException.class, () -> OvertureDataConfig.officialTileUrl(""));
      // 纯空白文件名应被拒绝
      assertThrows(IllegalArgumentException.class, () -> OvertureDataConfig.officialTileUrl("   "));
   }

   /**
    * 正常场景：候选列表应保持"优先地址在前、官方兜底在后"的顺序
    */
   @Test
   void sourceCandidatesKeepPrimaryFirst() {
      // 主地址模拟镜像，兜底地址模拟官方源
      List<String> candidates = OvertureDataConfig.sourceCandidates(
         "https://telluscn.ggff.net/overture/buildings",
         "https://example.com/buildings.pmtiles"
      );
      // 断言候选数量为 2
      assertEquals(2, candidates.size());
      // 断言镜像排在第一优先尝试
      assertEquals("https://telluscn.ggff.net/overture/buildings", candidates.get(0));
      // 断言官方地址排在第二兜底
      assertEquals("https://example.com/buildings.pmtiles", candidates.get(1));
   }

   /**
    * 边界场景：主地址与兜底地址相同时应去重，避免同一个地址被请求两次
    */
   @Test
   void sourceCandidatesDeduplicateIdenticalUrls() {
      // 主地址与兜底地址完全相同
      List<String> candidates = OvertureDataConfig.sourceCandidates(
         "https://example.com/buildings.pmtiles",
         "https://example.com/buildings.pmtiles"
      );
      // 断言去重后只剩一条
      assertEquals(1, candidates.size());
   }

   /**
    * 边界场景：空值与非空地址混入时应只保留有效地址
    */
   @Test
   void sourceCandidatesDropBlankEntries() {
      // 主地址为 null、兜底地址为纯空白，此时没有可用地址
      List<String> noneUsable = OvertureDataConfig.sourceCandidates(null, "   ");
      // 断言返回空列表而不是抛错
      assertTrue(noneUsable.isEmpty());
      // 主地址为空、兜底地址有效时应保留兜底地址
      List<String> fallbackOnly = OvertureDataConfig.sourceCandidates("", "https://example.com/buildings.pmtiles");
      // 断言只剩兜底地址
      assertEquals(List.of("https://example.com/buildings.pmtiles"), fallbackOnly);
   }

   /**
    * 边界场景：地址首尾有多余空格时应被裁剪后再去重
    */
   @Test
   void sourceCandidatesTrimWhitespaceBeforeDeduplication() {
      // 两个地址内容相同但一个带首尾空格
      List<String> candidates = OvertureDataConfig.sourceCandidates(
         "  https://example.com/buildings.pmtiles  ",
         "https://example.com/buildings.pmtiles"
      );
      // 断言裁剪后被识别为重复地址，只保留一条
      assertEquals(List.of("https://example.com/buildings.pmtiles"), candidates);
   }
}
