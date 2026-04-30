package com.earmo.onlyoffice.integration.service.llm;

import reactor.core.publisher.Flux;

/**
 * 上游模型 provider 的统一适配接口。
 *
 * <p>领域层只依赖这里定义的最小能力集：
 * 1. provider 自身名称；
 * 2. 流式输出统一 chunk；
 * 3. 是否支持上游取消；
 * 4. 取消请求入口。
 */
public interface SpringAiLlmProvider {

    /**
     * 返回 provider 实现名。
     */
    String providerName();

    /**
     * 以统一 chunk 结构流式返回模型输出。
     */
    Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request);

    /**
     * 当前 provider 是否支持向上游发送取消请求。
     */
    boolean supportsUpstreamCancel();

    /**
     * 请求上游取消指定 request。
     */
    void cancelRequest(String providerRequestId);
}
