package com.earmo.onlyoffice.demo.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 文档主数据仓储。
 */
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadataEntity, String> {

  List<DocumentMetadataEntity> findAllByTenantIdOrderByUpdatedAtDesc(String tenantId);

  Optional<DocumentMetadataEntity> findBySourceSystemAndExternalDocumentId(String sourceSystem, String externalDocumentId);
}
