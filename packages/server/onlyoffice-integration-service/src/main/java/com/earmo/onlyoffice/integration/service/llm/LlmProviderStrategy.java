package com.earmo.onlyoffice.integration.service.llm;

public interface LlmProviderStrategy {

  String providerName();

  LlmProviderResponse sendChat(LlmProviderRequest request);

  boolean supportsUpstreamCancel();

  void cancelRequest(String providerRequestId);
}
