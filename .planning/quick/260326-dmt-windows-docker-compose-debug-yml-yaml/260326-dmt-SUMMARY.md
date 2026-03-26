# Quick Task 260326-dmt Summary

## 完成内容

- 新增 `docker-compose.debug.yml`，用于在 Windows 本地调试时覆盖暴露 PostgreSQL、MinIO 和 ONLYOFFICE 端口
- 新增 `application-windows-debug.yml`，把后端本地调试链路收口为 YAML profile
- 更新 `README.md`、`docs/standalone-deployment.md`、`docs/configuration-matrix.md`，补齐 Windows 本地调试说明

## 验证

- `docker compose -f docker-compose.yml -f docker-compose.debug.yml config`

## 结果

- 本机后端现在可以通过 `localhost:15434`、`localhost:9000` 和 `host.docker.internal:8080` 与容器中的基础依赖和 ONLYOFFICE callback 正常协作
- 调试说明已经沉淀到仓库文档，不再只存在于即时对话里
