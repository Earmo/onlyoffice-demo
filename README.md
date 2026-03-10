# Spring Boot + Vue + ONLYOFFICE 最小集成示例

这是一套最小可运行的本地集成骨架，包含：

- `packages/server`：Spring Boot 后端，负责生成 ONLYOFFICE 编辑配置、签发 JWT、提供文件访问地址、接收保存回调
- `packages/web`：Vue 3 + Vite 前端，负责拉取配置并挂载 ONLYOFFICE Vue 组件
- `docker-compose.yml`：本地启动 ONLYOFFICE Docs

默认跑通路径是编辑 `demo.docx`：

1. 浏览器打开 Vue 页面
2. 前端请求后端 `/api/documents/demo/editor-config`
3. 后端返回 ONLYOFFICE 所需 `config + token`
4. 浏览器从 `http://localhost:8088/` 加载 ONLYOFFICE Docs
5. ONLYOFFICE Docs 从后端下载文档，并在保存时回调后端

## 目录结构

```text
packages/
  server/   Spring Boot API
  web/      Vue 3 + Vite UI
docs/
  minimal-integration.md
docker-compose.yml
```

## 快速启动

先启动 ONLYOFFICE Docs：

```bash
docker compose up -d
```

再启动后端：

```bash
cd packages/server
mvn spring-boot:run
```

最后启动前端：

```bash
cd packages/web
npm install
npm run dev
```

浏览器打开：

```text
http://localhost:5173
```

详细说明见 [docs/minimal-integration.md](/d:/workspace/github/earmo/onlyoffice-demo/docs/minimal-integration.md)。
