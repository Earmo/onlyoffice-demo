# External Integrations

**Analysis Date:** 2026-03-17

## APIs & External Services

**Document Editing Service:**
- ONLYOFFICE Docs - 在线文档编辑、文档下载与保存回调
  - Runtime: `docker-compose.yml` 中的 `onlyoffice/documentserver:9.3`
  - Browser integration: `packages/web/src/App.vue` 通过 `@onlyoffice/document-editor-vue` 加载编辑器
  - Server integration: `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeConfigService.java`
  - Auth: 共享 JWT 密钥，经由环境变量 `JWT_SECRET` / `DEMO_ONLYOFFICE_JWT_SECRET` 与 `demo.onlyoffice.jwt-secret` 提供

**Remote Document Fetching:**
- 任意 http/https 可访问的远程文档地址 - `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java` 负责下载
  - Integration method: Spring `RestClient`
  - Constraints: 禁止 `localhost`、`127.0.0.1`、`::1`，且仅支持有限扩展名
  - Entry point: `POST /api/documents/import-remote` in `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java`

**Remote Image Fetching:**
- 任意 http/https 可访问的远程图片地址 - `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeImageService.java` 负责签名与代理下载
  - Integration method: Spring `RestClient`
  - Constraints: 禁止本地回环地址，仅支持 bmp/gif/jpg/jpeg/png/svg/tif/tiff/webp
  - Entry points: `POST /api/documents/{documentId}/images/insert` 与 `GET /api/documents/{documentId}/images/proxy`

## Data Storage

**Databases:**
- 当前没有数据库
  - 状态型数据仅保存在内存 `ConcurrentHashMap` 中，见 `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStatusService.java`
  - 文档元数据不持久化到数据库，`documentId` 直接映射到本地文件

**File Storage:**
- 本地文件系统 - `demo.storage-root` / `DEMO_STORAGE_ROOT`
  - Implementation: `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/DocumentStorageService.java`
  - Default path: `./storage`（配置名可见于 `packages/server/src/main/resources/application.yml`）
  - Docker shape: `docker-compose.yml` 中的 `storage-data` volume 挂载到 `/data/storage`

**Caching:**
- 无显式缓存层
- 运行态状态只在 JVM 内存中暂存，不具备跨重启保留能力

## Authentication & Identity

**Auth Provider:**
- 无用户登录体系
  - ONLYOFFICE 相关身份信息为固定演示用户，定义在 `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java`
  - 前端没有 session、cookie 或 token 登录逻辑

**JWT Signing:**
- 自定义共享密钥 JWT - `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/OnlyofficeJwtService.java`
  - Used for: ONLYOFFICE `config.token` 与 `insertImage.token`
  - Secret source: `demo.onlyoffice.jwt-secret` / `DEMO_ONLYOFFICE_JWT_SECRET`
  - Storage guidance: 文档中只记录字段名，不应记录真实密钥值

## Monitoring & Observability

**Error Tracking:**
- 无 Sentry、无第三方错误追踪服务
- 后端通过 `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/GlobalExceptionHandler.java` 统一返回错误响应

**Analytics:**
- 无埋点或分析服务

**Logs:**
- 前端仅见 `console.log` / `console.error`，位置在 `packages/web/src/App.vue`
- 后端没有单独日志门面，依赖 Spring Boot 默认日志输出

## CI/CD & Deployment

**Hosting:**
- 本仓库当前偏向本地 Docker Compose 演示
  - Browser entry: `packages/web/nginx.conf` 所在 nginx 容器
  - App services: `server` 与 `onlyoffice` 容器由 `docker-compose.yml` 编排

**CI Pipeline:**
- 未发现 `.github/workflows/` 下的 CI 配置
- `.github/` 主要承载 GSD 技能与自定义 agent，而不是构建流水线

## Environment Configuration

**Development:**
- 关键配置字段集中在 `packages/server/src/main/resources/application.yml` 与 `docker-compose.yml`
- 前端开发态默认同源外的后端地址由 `VITE_API_BASE_URL` 控制
- ONLYOFFICE 容器回调后端时使用 `DEMO_ONLYOFFICE_INTERNAL_BASE_URL`

**Staging / Production:**
- 仓库未提供分环境配置分层
- `document-server-url` 支持留空后自动回退为同源根路径 `/`，见 `OnlyofficeConfigService`
- 真实生产应把 JWT、白名单、对象存储等配置迁出默认值

## Webhooks & Callbacks

**Incoming:**
- ONLYOFFICE callback - `POST /api/documents/{documentId}/callback`
  - Handler: `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java`
  - Processing: `status=2` 或 `status=6` 时下载最新文件并覆盖本地存储
  - Verification: 当前没有对回调做签名验签，`docs/minimal-integration.md` 明确把它列为生产前必须补齐项

**Outgoing:**
- ONLYOFFICE 拉取文档 - 后端在 editor config 中提供 `/api/documents/{documentId}/file`
- ONLYOFFICE 拉取图片 - 后端在 insertImage payload 中提供 `/api/documents/{documentId}/images/proxy`
- 后端主动下载远程资源 - `DocumentStorageService` 与 `OnlyofficeImageService` 对外发起 HTTP GET

---

*Integration audit: 2026-03-17*
*Update when adding/removing external services*
