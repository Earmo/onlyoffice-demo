---
phase: 11-document-id-session-exit-and-list-pagination
plan: 01
subsystem: api
tags: [document-id, ulid, upload, create]
requires:
  - phase: 10-element-plus-element-plus
    provides: Element Plus 前端基线
provides:
  - 新建、上传、导入统一使用服务端 ULID documentId
  - title 与内部 documentId 解耦
  - externalDocumentId 幂等复用时回滚多余对象写入
affects: [document-api, document-storage, metadata]
tech-stack:
  added: []
  patterns: [服务端生成内部文档标识, 标题与内部主键解耦]
key-files:
  created: []
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/CreateDocumentRequest.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStorageServiceImpl.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStorageServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java
key-decisions:
  - "内部 documentId 一律由服务端生成，不再接受前端/外部调用方传入值作为内部主键"
  - "上传文档保留原始文件名作为 title，避免 UI 把 ULID 暴露成标题"
  - "externalDocumentId 命中已存在文档时，清理刚生成但未被采用的对象文件"
patterns-established:
  - "内部标识只用于系统引用，展示标题由业务字段单独承载"
requirements-completed: [PH11-ID-01]
duration: 35min
completed: 2026-03-31
---

# Phase 11 / Plan 01 Summary

**文档内部主键现在统一由 MyBatis-Flex ULID 生成，新建、上传和导入链路不再把标题或外部传入 ID 混成 `documentId`。**

## Accomplishments

- `DocumentStorageServiceImpl` 改成统一生成 ULID 作为内部 `documentId`，并让上传标题保留原始文件名。
- `DocumentApiController#create` 显式忽略请求体中的 `documentId`，只保留兼容字段说明。
- 当 `externalDocumentId` 命中已有文档时，会回滚这次新生成但未采用的对象文件，避免脏数据残留。
- 后端定向测试已覆盖上传标题解耦和显式创建接口忽略外部 `documentId` 的行为。

## Verification

- `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest,DocumentApiControllerTest,DocumentMetadataServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## Notes

- MyBatis-Flex `ULIDKeyGenerator` 生成的是 26 位小写 Crockford Base32，测试断言已按真实格式对齐。

---
*Phase: 11-document-id-session-exit-and-list-pagination*
*Completed: 2026-03-31*
