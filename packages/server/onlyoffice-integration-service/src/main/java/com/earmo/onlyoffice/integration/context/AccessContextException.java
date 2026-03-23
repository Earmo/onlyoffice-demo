package com.earmo.onlyoffice.integration.context;

/**
 * 访问上下文异常基类。
 */
public class AccessContextException extends RuntimeException {

  public AccessContextException(String message) {
    super(message);
  }

  public AccessContextException(String message, Throwable cause) {
    super(message, cause);
  }
}
