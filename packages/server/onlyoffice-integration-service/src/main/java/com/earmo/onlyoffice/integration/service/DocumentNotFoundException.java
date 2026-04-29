package com.earmo.onlyoffice.integration.service;

/**
 * 文档不存在时抛出的异常。
 */
public class DocumentNotFoundException extends RuntimeException {

  /**
   * 创建文档不存在异常。
   *
   * @param documentId 文档唯一标识
   */
  public DocumentNotFoundException(String documentId) {
    super("文档不存在：" + documentId);
  }
}


