# ONLYOFFICE Integration Starter

这是一套面向“独立服务 + 可嵌入微服务”的 ONLYOFFICE 在线文档编辑骨架，包含：

- `packages/server/onlyoffice-integration-data`：数据库实体、repository、Flyway 迁移
- `packages/server/onlyoffice-integration-service`：Spring Boot starter 服务，负责 API、编辑配置、文件流和回调
- `packages/web`：Vue 3 官方前端宿主
- `docker-compose.yml`：本地联调用的聚合启动方式

默认联调入口：

```text
http://localhost:12333/
```

## 快速启动

先启动 PostgreSQL、ONLYOFFICE Docs 和聚合前端：

```bash
docker compose up -d
```

单独启动后端 starter：

```bash
cd packages/server
mvn -pl onlyoffice-integration-service spring-boot:run
```

单独启动前端：

```bash
cd packages/web
npm install
npm run dev
```

更多接入细节见 [docs/minimal-integration.md](docs/minimal-integration.md)。
