package com.earmo.onlyoffice.integration.service;

import java.io.IOException;

/**
 * ONLYOFFICE Conversion API 契约。
 *
 * <p>通过调用 ONLYOFFICE Document Server 的 {@code /converter} 端点，
 * 实现服务端静默文档格式转换（如 PDF → Word）。
 */
public interface OnlyofficeConversionService {

  /**
   * 调用 ONLYOFFICE Conversion API 同步转换文档格式。
   *
   * @param documentId     源文档内部主键（用于构造文件下载 URL）
   * @param sourceFileType 源文件类型（如 {@code "pdf"}）
   * @param outputFileType 目标文件类型（如 {@code "docx"}）
   * @return 转换结果文件的字节内容
   * @throws IOException           下载转换结果失败时抛出
   * @throws IllegalStateException 转换 API 返回错误码或未转换完成时抛出
   */
  byte[] convertDocument(String documentId, String sourceFileType, String outputFileType)
      throws IOException;
}
