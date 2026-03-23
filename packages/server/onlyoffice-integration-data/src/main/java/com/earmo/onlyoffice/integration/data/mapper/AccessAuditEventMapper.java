package com.earmo.onlyoffice.integration.data.mapper;

import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 访问审计 Mapper。
 *
 * <p>按照仓库约定，这里只保留 BaseMapper 通用能力，
 * 带业务语义的查询统一进入 repository，避免 mapper 再次堆积自定义 SQL。
 */
@Mapper
public interface AccessAuditEventMapper extends BaseMapper<AccessAuditEventEntity> {
}
