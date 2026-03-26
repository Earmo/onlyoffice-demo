# onlyoffice-integration-starter

这是一个面向“独立软件服务 + 可嵌入微服务”的 ONLYOFFICE 在线文档编辑服务基线。仓库已经具备：

- `packages/server/onlyoffice-integration-data`
  数据库实体、repository、Flyway 迁移和持久化边界
- `packages/server/onlyoffice-integration-service`
  Spring Boot 服务，负责文档 API、ONLYOFFICE editor-config、文件流、callback 和安全边界
- `packages/web`
  Vue 3 官方前端工作台，提供文档列表、创建入口和独立编辑页
- `docker-compose.yml`
  本地联调用的聚合部署方式

默认聚合入口：

```text
http://localhost:12333/
```

## 统一验证入口

仓库根已经提供分层验证命令，默认推荐直接从根目录执行：

```bash
npm run verify
```

它会依次执行：

1. 后端测试：`npm run test:server`
2. 前端测试：`npm run test:web`
3. 前端构建：`npm run build:web`
4. compose 配置校验：`npm run verify:compose`

如果你在排查问题，也可以单独运行这些分层命令，而不需要手工拼接子模块命令。

## 本地开发与联调

### Compose 聚合联调

```bash
docker compose up -d
```

### Windows 本地断点调试

如果你在 Windows + Docker Desktop 环境下，想让 `postgres`、`minio`、`onlyoffice` 继续跑在容器里，但把后端和前端放到本机 IDE / 终端里启动，推荐使用调试覆盖文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d postgres minio onlyoffice
```

这条命令会在不改动主 compose 语义的前提下，额外暴露本机调试需要的端口：

- `localhost:15434` -> PostgreSQL
- `localhost:9000` / `localhost:9001` -> MinIO API / Console
- `localhost:18080` -> ONLYOFFICE Docs

后端本机启动时，建议直接启用新增的 YAML profile：

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run -Dspring-boot.run.profiles=windows-debug
```

前端本机启动时，把 API 指向本机后端：

```bash
cd packages/web
corepack pnpm install
$env:VITE_API_BASE_URL="http://localhost:8080"
corepack pnpm dev
```

如果要确认 ONLYOFFICE 容器能否打回你本机后端，可以执行：

```bash
docker exec -it onlyoffice-integration-docs curl http://host.docker.internal:8080/v3/api-docs
```

### 单独启动后端

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run
```

### 单独启动前端

```bash
cd packages/web
corepack pnpm install
corepack pnpm dev
```

## 文档导航

- [最小接入说明](docs/minimal-integration.md)
- [交付总览](docs/delivery-overview.md)
- [独立部署说明](docs/standalone-deployment.md)
- [微服务接入说明](docs/microservice-integration.md)
- [配置矩阵](docs/configuration-matrix.md)
- [验收清单](docs/acceptance-checklist.md)

## 推荐阅读顺序

第一次接手这个仓库时，建议按下面顺序理解和验证：

1. 先看本文，了解仓库结构和根级验证命令
2. 执行 `npm run verify`，确认本地基线可通过
3. 如果要独立部署，继续看 [独立部署说明](docs/standalone-deployment.md)
4. 如果要把它接入上游业务系统，继续看 [微服务接入说明](docs/microservice-integration.md)
5. 需要对环境变量做细化时，查 [配置矩阵](docs/configuration-matrix.md)
6. 交付前按 [验收清单](docs/acceptance-checklist.md) 做一次人工核对
