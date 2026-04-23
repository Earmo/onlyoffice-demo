package com.earmo.onlyoffice.integration.service.llm;

import org.springframework.http.HttpStatus;

/**
 * LLM 模块统一业务异常。
 *
 * <p>同时携带稳定错误码和建议返回给客户端的 HTTP 状态码。
 */
public class LlmApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  /**
   * 创建一个带稳定错误码和 HTTP 状态的业务异常。
   */
  public LlmApiException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  /**
   * 返回稳定错误码。
   */
  public String errorCode() {
    return errorCode;
  }

  /**
   * 返回建议映射到响应的 HTTP 状态。
   */
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
