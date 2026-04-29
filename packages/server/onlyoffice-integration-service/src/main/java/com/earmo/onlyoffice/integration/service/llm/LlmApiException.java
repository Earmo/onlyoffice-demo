package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.common.exception.BaseException;
import org.springframework.http.HttpStatus;

/**
 * LLM 模块统一业务异常。
 *
 * <p>同时携带稳定错误码和建议返回给客户端的 HTTP 状态码。
 */
public class LlmApiException extends BaseException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  /**
   * 创建一个带稳定错误码和 HTTP 状态的业务异常。
   *
   * @param errorCode 稳定错误码
   * @param httpStatus 建议返回给客户端的 HTTP 状态
   * @param message 异常说明
   */
  public LlmApiException(String errorCode, HttpStatus httpStatus, String message) {
    super(errorCode, message, httpStatus);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  /**
   * 返回稳定错误码。
   *
   * @return 稳定错误码
   */
  public String errorCode() {
    return errorCode;
  }

  /**
   * 返回建议映射到响应的 HTTP 状态。
   *
   * @return HTTP 状态
   */
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
