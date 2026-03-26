# Quick Task 260326-dmt: 做一个适配 Windows 本地调试的 docker-compose.debug.yml，将后端本地调试配置改成 yaml 文件，并把本地调试事项更新到文档里

## Tasks

### 1. 增加 Windows 本地调试覆盖文件
- 文件：
  - `docker-compose.debug.yml`
- 动作：
  - 新增只负责暴露 PostgreSQL、MinIO、ONLYOFFICE 端口的 compose 覆盖文件，保持主 compose 不变
- 验证：
  - `docker compose -f docker-compose.yml -f docker-compose.debug.yml config`
- 完成标准：
  - Windows 本机可通过覆盖文件访问 `15434`、`9000/9001`、`18080`

### 2. 增加后端本地调试 YAML 配置
- 文件：
  - `packages/server/onlyoffice-integration-service/src/main/resources/application-windows-debug.yml`
- 动作：
  - 新增适配 Windows + Docker Desktop 的本地调试 profile，收口 datasource、MinIO、ONLYOFFICE 地址
- 验证：
  - 手工检查 YAML 内容与本地调试链路一致
- 完成标准：
  - 后端可通过 `windows-debug` profile 直接使用本地暴露端口和 `host.docker.internal`

### 3. 更新本地调试文档
- 文件：
  - `README.md`
  - `docs/standalone-deployment.md`
  - `docs/configuration-matrix.md`
- 动作：
  - 补充 Windows 本地断点调试命令、地址说明和 profile 使用方式
- 验证：
  - 文档能独立说明如何启动基础容器、如何启动本机后端和前端
- 完成标准：
  - 新接手的人无需额外问答也能照文档完成 Windows 本地调试
