---
phase: 05-distributed-editing-flow
plan: 03
subsystem: remote-resource-safety-and-runtime-closure
tags: [ssrf, remote-import, image-proxy, size-limit, content-type, integration-docs]
requires:
  - phase: 05-01
    provides: 共享运行状态与最近事件摘要
  - phase: 05-02
    provides: callback 验签与角色化运行时 URL
provides:
  - 远程导入和图片代理统一 SSRF 防护
  - 文档导入与图片代理的大小和媒体类型限制
  - Phase 5 安全边界与手动验证路径收口
affects: [phase-06]
tech-stack:
  added: [remote-resource-security-service, remote-resource-guardrails]
  patterns: [shared-ssrf-policy, explainable-security-rejection, remote-resource-size-limits]
key-files:
  created:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/RemoteResourceSecurityService.java
  modified:
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentStorageService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/OnlyofficeImageService.java
    - packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/config/OnlyofficeIntegrationProperties.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStorageServiceTest.java
    - packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/OnlyofficeImageServiceTest.java
    - packages/web/src/components/editor/EditorShell.vue
    - docs/minimal-integration.md
key-decisions:
  - "远程导入和图片代理统一纳入同一套 SSRF 边界，而不是各自保留一套示例级校验"
  - "文档导入执行扩展名加内容类型双校验，图片代理严格要求 image media type"
  - "被安全策略拒绝时返回明确 4xx 和可读错误，而不是把安全拒绝伪装成普通下载失败"
patterns-established:
  - "远程资源边界从 localhost 拦截升级为私网、回环、超大响应和非法媒体类型统一治理"
  - "编辑页保存状态现在可展示最近关键事件，便于排查 callback 与保存异常"
requirements-completed: [SAFE-02, SAFE-03]
duration: 40min
completed: 2026-03-25
---

# Phase 5 / Plan 03 Summary

**远程资源入口已经从“示例可用”升级为“有统一安全边界的服务能力”，Phase 5 的运行时说明也完成了最后收口。**

## Accomplishments

- 新增 `RemoteResourceSecurityService`，远程导入和图片代理已经共享一致的 SSRF 校验，不再只拦 `localhost`。
- `DocumentStorageService` 已补齐文档导入的响应大小上限、扩展名与内容类型双校验；`OnlyofficeImageService` 已补齐图片媒体类型和大小限制。
- `OnlyofficeIntegrationProperties` 已新增 `remoteResource.maxDocumentBytes`、`remoteResource.maxImageBytes` 和 `remoteResource.allowPrivateAddressAccess` 统一配置项。
- `EditorShell.vue` 已修正保存状态字段映射，并补充最近关键事件展示；`docs/minimal-integration.md` 已同步收口 callback JWT、运行时地址模型和远程资源安全边界说明。

## Execution Commits

- **实现提交：** `24225fa` `feat(phase5): 落地分布式编辑运行链路`

## Notes

- callback 内部文件拉取链路仍然和远程导入的 SSRF 策略分开处理，避免误伤 ONLYOFFICE 内部下载地址。
- 这一轮把安全拒绝语义做成了明确错误，但还没有扩展成完整网络治理平台或更细粒度白名单系统。

---
*Phase: 05-distributed-editing-flow*
*Completed: 2026-03-25*
