package com.earmo.onlyoffice.demo.service;

/**
 * 文档不存在时抛出的异常。
 */
public class DocumentNotFoundException extends RuntimeException {

  public DocumentNotFoundException(String documentId) {
    super("文档不存在：" + documentId);
  }
}
