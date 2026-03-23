package com.earmo.onlyoffice.integration.data.mapper;

import com.earmo.onlyoffice.integration.data.entity.DocumentMetadataEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档主数据 Mapper。
 *
 * <p>按照 Phase 7 的约束，这里只保留 BaseMapper 提供的通用 CRUD 能力。
 * 任何带业务语义的自定义查询都必须收口到 repository，避免注解 SQL 在 mapper 接口里继续扩散。
 */
@Mapper
public interface DocumentMetadataMapper extends BaseMapper<DocumentMetadataEntity> {
}
