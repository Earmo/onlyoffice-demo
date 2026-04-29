package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.common.exception.BaseException;

/**
 * 访问上下文异常基类。
 */
public class AccessContextException extends BaseException {

  public AccessContextException(String message) {
    super("ACCESS_CONTEXT_ERROR", message);
  }

  public AccessContextException(String message, Throwable cause) {
    super("ACCESS_CONTEXT_ERROR", message, cause);
  }
}
