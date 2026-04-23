package com.earmo.onlyoffice.integration.service.llm;

/**
 * LLM 模块对外暴露的稳定错误码集合。
 *
 * <p>controller、service 和前端都应依赖这些常量，而不是依赖底层异常文本。
 */
public final class LlmErrorCodes {

  public static final String LLM_DISABLED = "LLM_DISABLED";
  public static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
  public static final String LLM_PROVIDER_TIMEOUT = "LLM_PROVIDER_TIMEOUT";
  public static final String LLM_PROVIDER_BAD_REQUEST = "LLM_PROVIDER_BAD_REQUEST";
  public static final String LLM_PROVIDER_UPSTREAM_ERROR = "LLM_PROVIDER_UPSTREAM_ERROR";
  public static final String LLM_PROVIDER_NOT_ALLOWED = "LLM_PROVIDER_NOT_ALLOWED";
  public static final String LLM_MODEL_NOT_ALLOWED = "LLM_MODEL_NOT_ALLOWED";
  public static final String LLM_REQUEST_CANCELLED = "LLM_REQUEST_CANCELLED";
  public static final String LLM_SESSION_FORBIDDEN = "LLM_SESSION_FORBIDDEN";
  public static final String LLM_SESSION_NOT_FOUND = "LLM_SESSION_NOT_FOUND";

  /**
   * 工具类不允许实例化。
   */
  private LlmErrorCodes() {
  }
}
