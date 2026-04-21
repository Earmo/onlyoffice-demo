package com.earmo.onlyoffice.integration.service.llm;

public final class LlmErrorCodes {

  public static final String LLM_DISABLED = "LLM_DISABLED";
  public static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
  public static final String LLM_PROVIDER_TIMEOUT = "LLM_PROVIDER_TIMEOUT";
  public static final String LLM_PROVIDER_BAD_REQUEST = "LLM_PROVIDER_BAD_REQUEST";
  public static final String LLM_PROVIDER_UPSTREAM_ERROR = "LLM_PROVIDER_UPSTREAM_ERROR";
  public static final String LLM_REQUEST_CANCELLED = "LLM_REQUEST_CANCELLED";
  public static final String LLM_SESSION_FORBIDDEN = "LLM_SESSION_FORBIDDEN";
  public static final String LLM_SESSION_NOT_FOUND = "LLM_SESSION_NOT_FOUND";

  private LlmErrorCodes() {
  }
}
