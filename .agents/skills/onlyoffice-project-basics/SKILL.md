---
name: onlyoffice-project-basics
description: ONLYOFFICE integration starter 项目基础规则。处理仓库结构、根命令、文档导航、编码命名、工具优先级或新模块落位时使用。
---

# ONLYOFFICE 项目基础规则

## 项目定位

本仓库是面向“独立软件服务 + 可嵌入微服务”的 ONLYOFFICE 在线文档编辑服务基线。

当前主要模块：

- `packages/server/onlyoffice-integration-data`：数据库实体、Mapper、Repository、Flyway 迁移和持久化边界。
- `packages/server/onlyoffice-integration-service`：Spring Boot 服务，负责文档 API、ONLYOFFICE editor-config、文件流、callback、安全边界、LLM 工作台后端。
- `packages/web`：Vue 3 前端工作台，提供文档列表、创建入口、预览页、独立编辑页和编辑器 AI 工作台。
- `docs`：接入、部署、配置、验收和开发规范。
- `infra`：基础设施补丁与配置，例如 ONLYOFFICE nginx header buffer 覆盖。

## 根命令

优先从仓库根目录运行统一命令：

```powershell
npm run test:server
npm run test:web
npm run build:web
npm run verify:compose
npm run verify
```

`npm run verify` 会依次执行后端测试、前端测试、前端构建和 compose 配置校验。

## 目录与命名

- 源码放在 `packages/server` 和 `packages/web/src`。
- 测试与源码保持就近或模块内对应关系。
- 静态资源放在 `packages/web/public` 或对应资源目录。
- 文档放在 `docs/`。
- 统一 UTF-8、2 空格缩进。
- 目录名和脚本名使用 `kebab-case`。
- 变量和函数使用 `camelCase`。
- 类型、类、组件使用 `PascalCase`。
- 测试文件使用 `*.test.*` 或 `*.spec.*`。

## 工具优先级

- 文件名搜索优先 `fd`。
- 文本内容搜索优先 `rg`。
- 代码结构搜索优先 `sg`。
- 搜索时排除 `.git`、`node_modules`、`dist`、`coverage`。

## 文档导航

常用文档：

- `docs/minimal-integration.md`
- `docs/delivery-overview.md`
- `docs/development-guidelines.md`
- `docs/standalone-deployment.md`
- `docs/microservice-integration.md`
- `docs/configuration-matrix.md`
- `docs/acceptance-checklist.md`

第一次接手时先看 `README.md`，再跑 `npm run verify`，部署或接入时再按场景阅读对应文档。
