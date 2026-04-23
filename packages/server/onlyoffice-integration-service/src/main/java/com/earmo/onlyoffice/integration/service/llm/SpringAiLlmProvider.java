package com.earmo.onlyoffice.integration.service.llm;

import reactor.core.publisher.Flux;

public interface SpringAiLlmProvider {

  String providerName();

  Flux<SpringAiProviderChunk> stream(LlmRuntimeRequest request);

  boolean supportsUpstreamCancel();

  void cancelRequest(String providerRequestId);
}
