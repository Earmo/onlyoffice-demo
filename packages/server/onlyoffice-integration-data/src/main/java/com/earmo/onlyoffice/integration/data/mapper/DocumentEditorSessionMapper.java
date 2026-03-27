package com.earmo.onlyoffice.integration.data.mapper;

import com.earmo.onlyoffice.integration.data.entity.DocumentEditorSessionEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档编辑会话 Mapper。
 *
 * <p>和仓库既有约定一致，这里只保留 BaseMapper 能力，
 * 带业务语义的活跃会话查询统一放到 repository。
 */
@Mapper
public interface DocumentEditorSessionMapper extends BaseMapper<DocumentEditorSessionEntity> {
}
