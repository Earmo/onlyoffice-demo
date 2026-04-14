package com.earmo.onlyoffice.integration.service;

import java.io.IOException;

/**
 * ONLYOFFICE Document Builder HTTP API 契约。
 *
 * <p>通过调用 ONLYOFFICE Document Server 的 {@code /docbuilder} 端点，
 * 在服务端静默执行 Office API 脚本（如消除水印）。
 */
public interface OnlyofficeDocumentBuilderService {

  /**
   * 通过 Document Builder API 执行脚本并返回处理后的文档字节。
   *
   * @param scriptUrl      Document Server 可访问的 Builder 脚本 URL（{@code .js} 文件）
   * @param outputFileName 脚本中 {@code builder.SaveFile} 指定的输出文件名（含扩展名）
   * @return 处理后文档的字节内容
   * @throws IOException           下载结果文件失败时抛出
   * @throws IllegalStateException Builder API 返回错误或结果 URL 缺失时抛出
   */
  byte[] runScript(String scriptUrl, String outputFileName) throws IOException;

  /**
   * 生成用于消除指定文档水印的 Document Builder 脚本内容（JavaScript 格式）。
   *
   * @param documentId 文档内部主键
   * @param fileType   文档文件类型（如 {@code "docx"}, {@code "doc"}, {@code "odt"}）
   * @return JavaScript 脚本字符串，可直接返回给 ONLYOFFICE Document Server 执行
   */
  String generateRemoveWatermarkScript(String documentId, String fileType);
}
