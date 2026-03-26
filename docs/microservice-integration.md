# 微服务接入说明

## 适用场景

当上游业务系统希望把本项目当作“文档编辑微服务”接入，而不是直接交付官方前端整套体验时，优先阅读本页。

## 集成原则

- `API-first`
  上游系统先调服务端 API 建立文档上下文
- `headless-first, UI-available`
  官方前端可直接复用，但不是唯一接入方式
- `low coupling`
  用户上下文通过 SPI/provider 解析，不把外部认证方案耦合进文档核心逻辑

## 推荐接入流程

1. 上游系统调用以下任一入口创建文档上下文
   - `POST /api/documents`
   - `POST /api/documents/upload`
   - `POST /api/documents/import-remote`
2. 获取内部 `documentId`
3. 调用 `GET /api/documents/{documentId}/editor-config`
4. 由上游系统决定：
   - 跳转官方前端 `/editor/{documentId}`
   - 或在自己的前端里消费 `editor-config`

## 用户上下文接入

当前用户上下文模型遵循 `SPI-first, built-ins included`：

- 内置 provider：`header`、`jwt`、`default`
- 自定义接入：实现 `AccessContextProvider` Bean 并加入 `enabled-providers` / `resolution-order`

默认 Header 输入：

- `X-Tenant-Id`
- `X-Source-System`
- `X-External-User-Id`
- `X-User-Display-Name`
- `X-Access-Permissions`

默认 JWT 输入：

- 通过 `Authorization: Bearer <token>` 透传
- claim 映射由 `onlyoffice.integration.access-context.jwt.claim-mappings.*` 控制

## 权限与审计边界

- 当前只消费最小权限集合：`edit/comment/download/print`
- create/upload/import/editor-config 会写轻量访问审计
- ONLYOFFICE callback 会记录为 `system event`

## 你通常不需要做的事

- 不需要让上游前端自己拼 `document.url` 或 `callbackUrl`
- 不需要修改 controller 才能接入自定义用户来源
- 不需要把 ONLYOFFICE callback 可信性压在来源 IP 白名单上，Phase 5 已改成 JWT 验签为主

## 继续阅读

- 最小接入细节见 [minimal-integration.md](./minimal-integration.md)
- 环境变量与部署差异见 [configuration-matrix.md](./configuration-matrix.md)
