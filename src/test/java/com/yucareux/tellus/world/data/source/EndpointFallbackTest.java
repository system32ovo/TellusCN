/*
 * TellusCN - Chinese Mirror & CDN Support for Tellus Mod
 * Copyright (c) 2026 BlackHoleEra-Team
 *
 * Original Tellus Mod Copyright (c) Yucareux
 * Licensed under LGPL-3.0
 */

package com.yucareux.tellus.world.data.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EndpointFallback 单元测试
 *
 * 覆盖场景：首个候选成功、首个候选失败后降级成功、全部候选失败、候选列表为 null、
 * 候选列表为空、回调为 null、回调抛出的非 IO 异常不参与降级。
 * 该测试只依赖 JDK 与 JUnit，不发起任何真实网络请求，因此可以在无游戏、无网络环境下直接运行。
 */
class EndpointFallbackTest {

   /**
    * 首个候选就成功时，应当直接返回结果，且不再尝试后面的候选。
    */
   @Test
   void firstCandidateSucceedsAndStops() throws IOException {
      // 记录实际被尝试过的地址，用来验证"成功后不再继续试"
      List<String> attemptedUrls = new ArrayList<>();
      // 两个候选地址：第一个就是可用的
      List<String> candidateUrls = List.of("https://mirror.example.com/weather", "https://api.open-meteo.com/v1");

      String result = EndpointFallback.tryCandidates(
         candidateUrls,
         "test weather",
         endpointUrl -> {
            attemptedUrls.add(endpointUrl);
            return "ok:" + endpointUrl;
         }
      );

      assertEquals("ok:https://mirror.example.com/weather", result);
      assertEquals(List.of("https://mirror.example.com/weather"), attemptedUrls);
   }

   /**
    * 首个候选失败时，应当自动降级到第二个候选并返回它的结果。
    */
   @Test
   void fallsBackToSecondCandidateWhenFirstFails() throws IOException {
      // 记录尝试顺序，验证确实是"先镜像后官方"
      List<String> attemptedUrls = new ArrayList<>();
      // 两个候选地址：第一个会失败
      List<String> candidateUrls = List.of("https://mirror.example.com/weather", "https://api.open-meteo.com/v1");

      String result = EndpointFallback.tryCandidates(
         candidateUrls,
         "test weather",
         endpointUrl -> {
            attemptedUrls.add(endpointUrl);
            // 喵~防御：模拟镜像返回 500 时的失败，触发降级
            if (endpointUrl.contains("mirror.example.com")) {
               throw new IOException("mirror returned HTTP 500");
            }
            return "ok:" + endpointUrl;
         }
      );

      assertEquals("ok:https://api.open-meteo.com/v1", result);
      assertEquals(candidateUrls, attemptedUrls);
   }

   /**
    * 全部候选都失败时，应当抛出最后一个候选产生的异常（官方源的错误更有参考价值）。
    */
   @Test
   void throwsLastErrorWhenAllCandidatesFail() {
      // 两个候选地址：都会失败
      List<String> candidateUrls = List.of("https://mirror.example.com/weather", "https://api.open-meteo.com/v1");
      // 预先构造两个不同的异常，方便断言最终抛出的是"最后一个"
      IOException mirrorError = new IOException("mirror failed");
      IOException officialError = new IOException("official failed");

      IOException thrown = assertThrows(IOException.class, () -> EndpointFallback.tryCandidates(
         candidateUrls,
         "test weather",
         endpointUrl -> {
            throw endpointUrl.contains("mirror.example.com") ? mirrorError : officialError;
         }
      ));

      assertSame(officialError, thrown);
   }

   /**
    * 候选列表为 null 时应当抛出带描述的 IOException，而不是空指针。
    */
   @Test
   void rejectsNullCandidateList() {
      IOException thrown = assertThrows(IOException.class, () -> EndpointFallback.tryCandidates(
         null,
         "test weather",
         endpointUrl -> "never"
      ));

      assertTrue(thrown.getMessage().contains("test weather"));
   }

   /**
    * 候选列表为空时应当抛出带描述的 IOException，而不是静默返回 null。
    */
   @Test
   void rejectsEmptyCandidateList() {
      IOException thrown = assertThrows(IOException.class, () -> EndpointFallback.tryCandidates(
         List.of(),
         "test weather",
         endpointUrl -> "never"
      ));

      assertTrue(thrown.getMessage().contains("test weather"));
   }

   /**
    * 回调为 null 属于调用方编程错误，应当立刻抛 NullPointerException。
    */
   @Test
   void rejectsNullAttempt() {
      assertThrows(NullPointerException.class, () -> EndpointFallback.tryCandidates(
         List.of("https://api.open-meteo.com/v1"),
         "test weather",
         null
      ));
   }

   /**
    * 回调抛出的非 IO 异常（例如参数非法）不应触发降级，而是立刻向上传递，
    * 否则会把"请求写错了"误判成"地址挂了"而白白重试一遍。
    */
   @Test
   void doesNotFallBackOnNonIoFailure() {
      // 记录尝试次数，用来验证第二个候选没有被碰到
      int[] attemptCount = {0};
      List<String> candidateUrls = List.of("https://mirror.example.com/weather", "https://api.open-meteo.com/v1");

      assertThrows(IllegalArgumentException.class, () -> EndpointFallback.tryCandidates(
         candidateUrls,
         "test weather",
         endpointUrl -> {
            attemptCount[0]++;
            throw new IllegalArgumentException("coordinates outside range");
         }
      ));

      assertEquals(1, attemptCount[0]);
   }
}
