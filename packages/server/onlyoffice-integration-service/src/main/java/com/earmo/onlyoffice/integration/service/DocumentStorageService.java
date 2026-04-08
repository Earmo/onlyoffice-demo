package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.earmo.onlyoffice.integration.model.NormalizedDocumentMetadata;
import com.earmo.onlyoffice.integration.model.RequestContext;
import com.earmo.onlyoffice.integration.model.StoredDocument;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import java.io.IOException;

/**
 * 文档文件对象编排服务契约。
 *
 * <p>接口层只表达上层真正依赖的能力：建档、上传、导入、读取、callback 回写与 provider 解析。
 * 具体如何调用 provider、生成 storageKey、做失败补偿，由默认实现负责。
 */
public interface DocumentStorageService {

  StoredDocument ensureBootstrapDocument(String rawDocumentId) throws IOException;

  StoredDocument getRequiredDocument(String rawDocumentId) throws IOException;

  byte[] readDocument(String rawDocumentId) throws IOException;

  NormalizedDocumentMetadata saveCallbackDocument(String rawDocumentId, String downloadUrl, String callbackFileType)
      throws IOException;

  StoredDocument storeUploadedDocument(String originalFilename, byte[] body) throws IOException;

  StoredDocument storeUploadedDocument(String originalFilename, byte[] body, RequestContext requestContext)
      throws IOException;

  StoredDocument importRemoteDocument(String sourceUrl) throws IOException;

  StoredDocument importRemoteDocument(String sourceUrl, RequestContext requestContext) throws IOException;

  StoredDocument createNativeDocument(
      String rawDocumentId,
      String rawTitle,
      RequestContext requestContext,
      String externalDocumentId
  ) throws IOException;

  boolean exists(DocumentMetadataEntity entity) throws IOException;

  StorageProvider resolveProvider(DocumentMetadataEntity entity);
}
