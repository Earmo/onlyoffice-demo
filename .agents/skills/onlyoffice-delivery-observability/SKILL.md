---
name: onlyoffice-delivery-observability
description: ONLYOFFICE 构建、部署、测试、日志、安全配置和交付规则。运行验证、修改 docker-compose、profile、环境变量、docs、日志、PR/提交或安全配置时使用。
---

# ONLYOFFICE 交付与可观测性规则

## 统一验证

根目录命令：

```powershell
npm run verify
```

分层命令：

```powershell
npm run test:server
npm run test:web
npm run build:web
npm run verify:compose
```

后端测试：

```powershell
mvn -f packages/server/pom.xml test
```

前端测试和构建：

```powershell
corepack pnpm --dir packages/web test -- --run
corepack pnpm --dir packages/web build
```

## Compose 联调

启动：

```powershell
docker compose up -d
```

默认聚合入口：

```text
http://localhost:12333/
```

主要服务：

- `postgres`: `localhost:15434`
- `onlyoffice`: `localhost:18080`
- `minio`: `localhost:9000` / `localhost:9001`
- `server`
- `web`: `${WEB_PORT:-12333}`

## Profile 与本地调试

后端 profile：

- `dev`
- `test`
- `prod`
- `windows-debug`

Windows 本地断点调试可只启动依赖容器，再本机跑后端和前端：

```powershell
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d postgres minio onlyoffice
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run -Dspring-boot.run.profiles=windows-debug
cd packages/web
corepack pnpm dev
```

## 安全配置

- 不要提交密钥、令牌或本地 `.env`。
- 示例配置写入 `.env.example`。
- 第三方服务接入说明写入 `docs/`，记录权限、回调地址和最小配置项。
- 真实环境必须覆盖 JWT、MinIO/COS、数据库等凭据。

## 日志与排障

新增或修改业务代码时，补齐关键 `info` 级业务日志，覆盖请求开始、状态迁移、外部依赖调用、异步/流式链路收口点。

禁止在 info 日志中输出密钥、令牌、完整敏感正文或 token 级高频噪音；高频明细只允许放在 debug，并默认关闭。

## 提交与 PR

建议使用 Conventional Commits，例如：

```text
feat: add editor bootstrap
fix: handle empty document state
```

PR 应包含变更摘要、验证方式、关联问题；涉及 UI 附截图，涉及配置注明新增环境变量和默认值。
