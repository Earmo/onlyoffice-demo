package com.earmo.onlyoffice.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * REST 客户端与 LLM 异步执行资源配置。
 *
 * <p>这里集中声明跨服务复用的 HTTP 客户端构建器，以及 LLM 流式调用使用的专用线程池，
 * 避免业务类各自创建基础设施对象。
 */
@Configuration
public class RestClientConfiguration {

  /**
   * 提供可注入的 RestClient.Builder。
   *
   * <p>业务侧按调用场景补充 baseUrl、headers、requestFactory 或测试替身后再 build，
   * 这样可以保持默认配置轻量，同时便于单元测试替换。
   *
   * @return Spring RestClient 构建器
   */
  @Bean
  RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  /**
   * LLM provider 调用专用线程池。
   *
   * <p>该线程池主要承载异步流式响应任务，避免长时间 LLM 请求占用 Web 容器线程。
   * 返回具体的 ThreadPoolTaskExecutor 类型，让 Spring 能按其生命周期接口完成关闭。
   *
   * @return LLM 异步任务执行器
   */
  @Bean(name = "llmExecutor")
  ThreadPoolTaskExecutor llmExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // 常驻线程覆盖日常并发；高峰期最多扩展到 20 个调用线程。
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(20);
    // 队列吸收短时突发，避免轻微抖动直接触发拒绝策略。
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("llm-exec-");
    // 队列和线程都耗尽时由调用线程兜底执行，给系统自然背压而不是直接丢任务。
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
