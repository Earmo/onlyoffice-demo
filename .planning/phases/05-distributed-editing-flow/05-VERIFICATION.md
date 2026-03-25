---
phase: 05-distributed-editing-flow
verified: 2026-03-25T10:49:17Z
status: passed
score: 3/3 must-haves verified
---

# Phase 5: Verification Report

**Phase Goal:** 让文档元数据、ONLYOFFICE 编辑链路和安全边界适应分布式部署与共享状态。  
**Verified:** 2026-03-25T10:49:17Z  
**Status:** passed

## Goal Achievement

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 文档元数据和保存状态在分布式部署下拥有共享真相源 | ✓ VERIFIED | `packages/server/onlyoffice-integration-data/.../V4__create_document_runtime_event.sql`、`packages/server/onlyoffice-integration-service/.../DocumentStatusService.java` 与 `.../DocumentSaveStatusResponse.java` 已把运行事件与摘要状态切到数据库驱动模型 |
| 2 | ONLYOFFICE editor-config、文件下载和 callback 在分布式部署下保持闭环 | ✓ VERIFIED | `packages/server/onlyoffice-integration-service/.../OnlyofficeConfigService.java`、`.../OnlyofficeJwtService.java` 与 `.../DocumentController.java` 已建立角色化 URL 生成、callback JWT 验签和拒绝语义 |
| 3 | 回调安全、远程资源边界和保存状态在多实例场景下仍然可信 | ✓ VERIFIED | `packages/server/onlyoffice-integration-service/.../RemoteResourceSecurityService.java`、`.../DocumentStorageService.java`、`.../OnlyofficeImageService.java` 与 `packages/web/src/components/editor/EditorShell.vue` 已建立 SSRF/大包/媒体类型限制和最近事件展示 |

## Automated Checks

- `cd packages/server && mvn test -q`
- `docker compose config`
- `cd packages/web && corepack pnpm build`

## Result

- 后端全量测试通过，当前 data + service 两个模块共 52 个测试全部通过，说明运行事件表、callback JWT、远程资源安全边界和现有文档链路没有破坏既有行为。
- `save-status` 现在返回共享数据库驱动的摘要状态加最近关键事件，编辑页不再依赖单实例进程内状态。
- callback JWT 校验失败会返回明确 `4xx`，同时记录运行状态和访问审计，不会再出现“谁能打到接口谁就能覆盖文档”的示例级漏洞。
- `OnlyofficeConfigService` 已对 `publicBaseUrl`、`internalBaseUrl` 和 `documentServerUrl` 做 fail-fast 校验，分布式地址配置错误会尽早暴露。
- 远程导入和图片代理都已纳入统一 SSRF、防大包和媒体类型边界，被安全策略拒绝时会返回可解释错误。
- 前端生产构建通过，编辑页已能展示最近运行事件，保存状态字段与后端 Phase 5 响应保持一致。

## Residual Notes

- Maven 仍会输出你本机 `settings.xml` 里的 repository id 告警，这不是 Phase 5 引入的问题。
- Mockito 在 JDK 21 下仍会打印动态 agent 警告，但不影响当前测试与验证结论。
- `storage/documents/` 仍保留为工作区中的未跟踪本地目录，本次执行没有纳入提交。

---
*Verified: 2026-03-25T10:49:17Z*
*Verifier: Codex*
