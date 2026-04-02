package com.earmo.onlyoffice.integration.data.mapper;

import com.earmo.onlyoffice.integration.data.entity.DocumentRuntimeEventEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档运行事件 Mapper。
 *
 * <p>保持和仓库既有约定一致：这里只保留 BaseMapper 能力，
 * 带业务语义的查询统一进入 repository。
 */
@Mapper
public interface DocumentRuntimeEventMapper extends BaseMapper<DocumentRuntimeEventEntity> {
}
