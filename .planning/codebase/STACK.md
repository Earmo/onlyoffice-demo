# Technology Stack

**Analysis Date:** 2026-03-17

## Languages

**Primary:**
- Java 17 - `packages/server/src/main/java/` 下的 Spring Boot 后端实现
- JavaScript (ES modules) - `packages/web/src/` 下的 Vue 前端与 Vite 配置

**Secondary:**
- YAML - `docker-compose.yml` 与 `packages/server/src/main/resources/application.yml` 中的运行配置
- Nginx config - `packages/web/nginx.conf` 中的统一入口与反向代理配置
- Markdown - `README.md`、`docs/minimal-integration.md`、`.github/` 与 `.codex/` 下的文档和 GSD 工作流

## Runtime

**Environment:**
- JVM 17 - 由 `packages/server/pom.xml` 的 `java.version` 指定
- Node.js - `packages/web/package.json` 使用 Vite 构建与开发服务器，README 通过 `npm` 启动
- Docker Compose - `docker-compose.yml` 用于启动 ONLYOFFICE Docs、Spring Boot、nginx 前端入口

**Package Manager:**
- Maven - 管理 `packages/server/` 的 Java 依赖与测试
- npm - README 使用 `npm install` / `npm run dev` 启动前端
- pnpm - `packages/web/package.json` 声明 `packageManager`，且仓库同时提交了 `pnpm-lock.yaml`
- Lockfiles: 根目录有 `package-lock.json`，`packages/web/` 下同时存在 `package-lock.json` 与 `pnpm-lock.yaml`

## Frameworks

**Core:**
- Spring Boot 3.5.8 - `packages/server/pom.xml` 中的后端 Web 应用框架
- Vue 3.5.x - `packages/web/package.json` 中的前端 UI 框架
- ONLYOFFICE Vue SDK 1.4.x - `@onlyoffice/document-editor-vue` 负责挂载编辑器

**Testing:**
- Spring Boot Starter Test - `packages/server/pom.xml` 中的测试依赖，实际使用 JUnit 5
- JUnit Jupiter - `packages/server/src/test/java/` 下的单元测试框架

**Build/Dev:**
- Vite 6.2.x - `packages/web/vite.config.js` 中的开发服务器与构建工具
- `@vitejs/plugin-vue` 5.2.x - Vue 单文件组件支持
- Spring Boot Maven Plugin - `packages/server/pom.xml` 中的打包与运行插件
- nginx - `packages/web/Dockerfile` 构建出的静态站点入口与同源反代层

## Key Dependencies

**Critical:**
- `spring-boot-starter-web` - 提供 REST API、文件下载、回调接收
- `spring-boot-starter-validation` - 用于请求体校验，例如 `DocumentImportRequest`、`InsertImageRequest`
- `io.jsonwebtoken:jjwt-*` - 给 ONLYOFFICE `config` 和 `insertImage` 参数签发 JWT
- `@onlyoffice/document-editor-vue` - 前端嵌入 ONLYOFFICE 编辑器的关键桥接依赖
- `vue` - 承载控制台 UI、状态切换、上传与远程导入交互

**Infrastructure:**
- `RestClient` - Spring 6 内置 HTTP 客户端，`DocumentStorageService` 与 `OnlyofficeImageService` 用于下载远程文档/图片
- Docker image `onlyoffice/documentserver:9.3` - `docker-compose.yml` 中的外部编辑器服务

## Configuration

**Environment:**
- ONLYOFFICE 相关配置集中在 `packages/server/src/main/resources/application.yml`
- 可通过环境变量覆盖 `demo.onlyoffice.*` 与 `demo.storage-root`
- `docker-compose.yml` 中实际使用的关键环境变量包括 `JWT_SECRET`、`DEMO_ONLYOFFICE_DOCUMENT_SERVER_URL`、`DEMO_ONLYOFFICE_INTERNAL_BASE_URL`、`DEMO_ONLYOFFICE_JWT_SECRET`、`DEMO_STORAGE_ROOT`
- 前端仅显式读取 `VITE_API_BASE_URL`，见 `packages/web/src/App.vue`

**Build:**
- `packages/web/vite.config.js` - 前端开发端口和 host 配置
- `packages/web/nginx.conf` - 生产容器中的静态托管与 `/api` / ONLYOFFICE 反向代理
- `packages/server/pom.xml` - 后端依赖、Java 版本、测试与打包
- `docker-compose.yml` - 本地集成运行的编排入口

## Platform Requirements

**Development:**
- 任意可运行 Java 17、Node.js、Docker 的平台
- 本地联调最少需要：ONLYOFFICE Docs 容器、Spring Boot、Vite dev server
- 如果走 Docker 一体化部署，需要容器间网络和 `storage-data` volume

**Production / Runtime Shape:**
- 当前仓库更接近 Docker Compose 示例环境，而非完整生产方案
- 统一浏览器入口是 nginx 容器暴露的 `WEB_PORT`
- 后端以单体 Spring Boot 服务运行，本地文件系统承担文档存储
- ONLYOFFICE 作为外部容器服务存在，不与前后端代码打包在同一个进程内

---

*Stack analysis: 2026-03-17*
*Update after major dependency changes*
