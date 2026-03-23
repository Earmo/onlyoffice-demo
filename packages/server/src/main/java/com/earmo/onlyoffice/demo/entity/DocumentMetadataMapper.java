package com.earmo.onlyoffice.demo.persistence;

import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档主数据 Mapper。
 *
 * <p>这里保留了 BaseMapper 提供的通用单表 CRUD 能力，
 * 同时把“按租户列出文档”“按来源系统和外部文档 ID 查找映射”这两个领域查询
 * 用明确的 SQL 固化下来，避免为了很小的查询场景额外引入复杂的 QueryWrapper 组装逻辑。
 */
@Mapper
public interface DocumentMetadataMapper extends BaseMapper<DocumentMetadataEntity> {

  @Select("""
      select *
      from document_metadata
      where tenant_id = #{tenantId}
      order by updated_at desc
      """)
  List<DocumentMetadataEntity> selectByTenantIdOrderByUpdatedAtDesc(@Param("tenantId") String tenantId);

  @Select("""
      select *
      from document_metadata
      where source_system = #{sourceSystem}
        and external_document_id = #{externalDocumentId}
      limit 1
      """)
  DocumentMetadataEntity selectBySourceSystemAndExternalDocumentId(
      @Param("sourceSystem") String sourceSystem,
      @Param("externalDocumentId") String externalDocumentId
  );
}
