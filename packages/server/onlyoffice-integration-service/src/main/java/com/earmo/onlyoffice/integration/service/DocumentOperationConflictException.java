package com.earmo.onlyoffice.integration.service;

/**
 * 文档处于冲突状态时抛出的异常。
 */
public class DocumentOperationConflictException extends RuntimeException {

  /**
   * 创建文档操作冲突异常。
   *
   * @param message 冲突说明
   */
  public DocumentOperationConflictException(String message) {
    super(message);
  }
}
