---
phase: 02-storage-strategy-layer
plan: 01
subsystem: storage-contract
tags: [storage, local, routing, provider, key]
requires: []
provides:
  - 统一的文档存储策略接口与领域对象
  - `tenant/sourceSystem/documentId.ext` 对象键规范
  - local 兼容 provider 与路由配置模型
affects: [02-02, 02-03, phase-05]
tech-stack:
  added: [storage-provider, storage-key-factory, local-storage-strategy]
  patterns: [provider-neutral-storage, strategy-routing, compensating-write]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/DocumentStorageStrategy.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageProvider.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageWriteRequest.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StoredObjectResource.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageProviderResolver.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/StorageKeyFactory.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/storage/local/LocalDocumentStorageStrategy.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
    - packages/server/onlyoffice-integration-service/src/main/resources/application.yml
key-decisions:
  - "DocumentStorageService 只负责编排，不再直接持有本地 Path/Files 读写细节"
  - "storageKey 固定为 tenant/sourceSystem/documentId.ext，避免延续 documents/{id}.ext 的旧结构"
  - "provider 解析优先级固定为 tenant > sourceSystem > defaultProvider"
patterns-established:
  - "统一存储合同：local、minio 及未来 COS/OSS 共享同一组读写签名"
  - "开发回退：local provider 保留为 dev/test 与迁移过渡兼容路径"
requirements-completed: [STOR-01]
duration: 50min
completed: 2026-03-23
---

# Phase 2 / Plan 01 Summary

**统一存储合同、provider 路由和 local 兼容实现已经建立，文档文件主路径不再依赖 `DocumentStorageService` 内部的本地文件系统实现。**

## Accomplishments

- 扩展 `OnlyofficeIntegrationProperties` 为正式存储配置模型，补齐 `defaultProvider`、`routing`、`local`、`minio` 四组配置。
- 新增 `DocumentStorageStrategy`、`StorageWriteRequest`、`StoredObjectResource`、`StorageProviderResolver`、`StorageKeyFactory` 等 provider-neutral 合同。
- 新增 `LocalDocumentStorageStrategy`，把原本散落在业务层的本地读写、越权校验和覆盖写入逻辑收口到 local provider。
- 重构 `DocumentStorageService`，让 bootstrap、上传、导入、读取和 callback 回写全部通过统一策略接口执行。

## Execution Commits

- **实现提交：** `fdf0155` `feat(phase2): 落地存储策略与MinIO链路`

## Notes

- local provider 仍能完整跑通 bootstrap、上传和读取，但已经从“默认真相源”降级为兼容策略。
- 统一 key 结构后，数据库 `storageKey` 与对象存储 key 语义保持一致，后续替换 provider 不需要改上层 API。

---
*Phase: 02-storage-strategy-layer*
*Completed: 2026-03-23*
