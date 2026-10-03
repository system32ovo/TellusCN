package com.yucareux.tellus.world.data.source;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 端点降级执行器（TellusCN 镜像支持组件）。
 *
 * <p>背景：单个固定地址的数据源（天气、地理编码、地图瓦片等）在启用镜像后只有一个地址可用，
 * 一旦镜像路由失效（例如返回 500 或 404），整个功能就直接不可用。本类提供"按优先级逐个尝试、
 * 失败自动降级到下一个候选地址"的通用逻辑，让这些数据源也能像 PMTiles 那样官方源兜底。</p>
 *
 * <p>整体思路：调用方传入有序候选地址列表（优先地址在前、官方兜底在最后）和一个"用某个地址执行一次请求"
 * 的回调；本类逐个调用回调，第一个成功的结果直接返回，中途抛出的 {@link IOException} 会被记录并继续尝试下一个，
 * 只有全部候选都失败时才抛出最后一个错误。</p>
 *
 * <p>输入：候选地址列表（不可为 null 或空）、用于日志的描述文本、以及执行回调。
 * 输出：回调第一次成功时返回的结果对象。
 * 边界条件：候选列表为 null 或空时抛 IOException；回调抛出的非 IO 异常（例如参数错误）不参与降级，
 * 直接向上传递，避免把"请求写错了"误判成"地址挂了"而白白重试一遍。</p>
 */
public final class EndpointFallback {
   // 独立日志器：与 Tellus.LOGGER 同一个日志分类，但不会连带触发 Tellus 类的静态初始化
   // （Tellus 的静态初始化会访问 Minecraft 注册表，在未引导游戏的单元测试环境里会直接抛错）
   private static final Logger LOGGER = LoggerFactory.getLogger("tellus");

   private EndpointFallback() {
   }

   /**
    * 单个候选地址的执行回调。
    *
    * @param <T> 成功时返回的结果类型
    */
   @FunctionalInterface
   public interface EndpointAttempt<T> {
      /**
       * 用给定地址执行一次请求。
       *
       * @param endpointUrl 本次尝试使用的完整端点地址
       * @return 请求成功时的结果
       * @throws IOException 请求失败（网络错误、非 2xx 响应、响应体损坏等），触发降级到下一个候选
       */
      T attempt(String endpointUrl) throws IOException;
   }

   /**
    * 依次尝试每个候选地址，返回第一个成功的结果。
    *
    * @param candidateUrls 有序候选地址列表，优先地址在前；不可为 null 或空
    * @param description   用于日志的描述文本，例如 "Open-Meteo weather"
    * @param attempt       用某个地址执行一次请求的回调；不可为 null
    * @param <T>           成功时返回的结果类型
    * @return 第一个成功的结果
    * @throws IOException 候选列表为空，或全部候选地址都失败
    */
   public static <T> T tryCandidates(List<String> candidateUrls, String description, EndpointAttempt<T> attempt)
      throws IOException {
      // 喵~防御：候选列表为 null 或空时根本没有地址可试，直接抛出带描述的异常，避免调用方拿到空指针
      if (candidateUrls == null || candidateUrls.isEmpty()) {
         throw new IOException(description + ": no endpoint candidates configured");
      }
      // 喵~防御：回调为 null 属于调用方写错，立刻报错而不是等到循环里才炸
      Objects.requireNonNull(attempt, "attempt");

      // 记录最后一个失败原因，全部候选都失败时用它作为最终异常抛出
      IOException lastError = null;
      // 最后一个候选的下标，用于判断"还有没有下一个可以试"
      int lastIndex = candidateUrls.size() - 1;
      for (int index = 0; index <= lastIndex; index++) {
         // 本次要尝试的候选地址
         String endpointUrl = candidateUrls.get(index);
         try {
            // 成功就直接返回，不再尝试后面的候选
            return attempt.attempt(endpointUrl);
         } catch (IOException error) {
            lastError = error;
            // 只有"后面还有候选"时才打降级警告，最后一个候选失败直接抛给调用方，避免重复日志
            if (index < lastIndex) {
               LOGGER.warn("{} failed on {}, falling back to the next candidate endpoint", description, endpointUrl, error);
            }
         }
      }

      // 喵~防御：理论上循环至少会设置一次 lastError，这里仍做兜底判断，保证不会抛出空指针
      throw lastError != null ? lastError : new IOException(description + ": all endpoint candidates failed");
   }
}
