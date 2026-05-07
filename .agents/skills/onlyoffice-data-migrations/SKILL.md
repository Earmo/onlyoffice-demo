---
name: onlyoffice-data-migrations
description: ONLYOFFICE 数据层和迁移规则。新增或修改数据库实体、Mapper、Repository、Flyway migration、文档元数据、编辑会话、运行事件、访问审计或 LLM 会话表时使用。
---

# ONLYOFFICE 数据与迁移规则

## 数据模块

数据模块位于：

```text
packages/server/onlyoffice-integration-data
```

主要包：

- `data.entity`：数据库实体。
- `data.mapper`：Mapper。
- `data.repository`：Repository。
- `src/main/resources/db/migration`：Flyway migration。

## 主要实体

- `DocumentMetadataEntity`：文档元数据。
- `DocumentEditorSessionEntity`：编辑会话。
- `DocumentRuntimeEventEntity`：运行时事件。
- `AccessAuditEventEntity`：访问审计事件。
- `DocumentLlmSessionEntity`、`DocumentLlmMessageEntity`、`DocumentLlmRequestEntity`、`DocumentLlmMessageVariantEntity`：LLM 工作台会话与消息。

## Migration

使用 Flyway 版本化迁移：

```text
V{number}__description.sql
```

当前已有 `V1` 到 `V10`。新增迁移时：

1. 使用下一个连续版本号。
2. 文件名描述清楚变更含义。
3. 同步新增或修改 Entity、Mapper、Repository。
4. 表/列含义需要注释时补 SQL 注释。
5. 测试覆盖新增字段或查询行为。

## Repository 边界

- Controller 不直接访问 Mapper。
- Service 通过 Repository 访问持久层。
- 查询条件、排序、分页逻辑优先收敛到 Service/Repository，避免散落在 Controller。
- 文档元数据、会话、运行事件、审计事件变更要注意租户和访问上下文过滤。
