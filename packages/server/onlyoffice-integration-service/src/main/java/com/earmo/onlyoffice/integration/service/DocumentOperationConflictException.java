package com.earmo.onlyoffice.integration.service;

/**
 * 文档处于冲突状态时抛出的异常。
 */
public class DocumentOperationConflictException extends RuntimeException {

  public DocumentOperationConflictException(String message) {
    super(message);
  }
}
