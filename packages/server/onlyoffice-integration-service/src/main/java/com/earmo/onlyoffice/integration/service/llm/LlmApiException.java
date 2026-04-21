package com.earmo.onlyoffice.integration.service.llm;

import org.springframework.http.HttpStatus;

public class LlmApiException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  public LlmApiException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
