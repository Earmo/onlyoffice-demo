---
phase: 07-module-boundaries-and-repository-refactor
plan: 02
subsystem: data
tags: [mybatis-flex, repository, flyway, naming]
requires:
  - phase: 07-01
    provides: 多模块工程与 data 模块承载面
provides:
  - repository 驱动的文档元数据自定义查询入口
  - `*_user` / `*_time` 命名规范落地
  - 对 repository 与 mapper 的回归测试
affects: [07-03, phase-02, phase-05]
tech-stack:
  added: [query-wrapper-repository, flyway-v2]
  patterns: [repository-over-mapper-sql, time-user-naming]
key-files:
  created:
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepository.java
    - packages/server/onlyoffice-integration-data/src/main/resources/db/migration/V2__rename_document_metadata_columns.sql
    - packages/server/onlyoffice-integration-data/src/test/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepositoryTest.java
  modified:
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentMetadataEntity.java
    - packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/mapper/DocumentMetadataMapper.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java
key-decisions:
  - "Mapper 只保留 BaseMapper CRUD，自定义查询统一进入 repository"
  - "旧列名不回改 V1，而是通过 V2 显式迁移到 user/time 命名"
patterns-established:
  - "领域查询封装：service 只表达查询意图，不直接依赖注解 SQL"
  - "命名收敛：owner_user、created_time、updated_time 等成为统一基线"
requirements-completed: [DATA-01, DATA-02]
duration: 40min
completed: 2026-03-23
---

# Phase 7 / Plan 02 Summary

**数据访问层已经从 `@Select` 注解查询切到 repository，文档主表字段与实体字段同步收敛为 `user/time` 命名。**

## Accomplishments

- 新增 `DocumentMetadataRepository`，用 MyBatis-Flex `QueryWrapper` 承接租户列表查询和外部文档映射查询。
- 移除 `DocumentMetadataMapper` 中的 `@Select` 注解，让 mapper 回到纯 CRUD 职责。
- 新增 Flyway `V2` 迁移，把 `owner_user_id`、`*_at` 列统一迁移到 `owner_user`、`*_time`。
- 更新实体、服务和测试断言，把 `ownerUserId` / `createdAt` / `updatedAt` 等旧命名切换为新规范。

## Execution Commits

- **实现提交：** `c30a6a2` `feat(phase7): 完成多模块拆分与starter重构`

## Notes

- 没有直接编辑 `target/generated-sources`，而是通过重新编译让 TableDef 常量自动生成新列名。
- repository 测试覆盖了按租户倒序列表和按来源系统 + 外部文档 ID 查找两条关键路径。

---
*Phase: 07-module-boundaries-and-repository-refactor*
*Completed: 2026-03-23*
