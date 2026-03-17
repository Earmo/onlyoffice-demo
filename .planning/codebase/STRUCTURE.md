# Codebase Structure

**Analysis Date:** 2026-03-17

## Directory Layout

```text
onlyoffice-demo/
├── .github/                     # GSD 技能、agent 与 Copilot 指令
├── .planning/                   # 规划与 codebase map 文档（本次生成）
├── docs/                        # 集成说明与补充文档
├── packages/
│   ├── server/                  # Spring Boot 后端
│   │   ├── src/main/java/       # Java 源码
│   │   ├── src/main/resources/  # application.yml
│   │   ├── src/test/java/       # JUnit 测试
│   │   └── target/              # Maven 构建产物
│   └── web/                     # Vue + Vite 前端与 nginx 配置
├── storage/                     # 示例文档 `demo.docx`
├── docker-compose.yml           # 本地一体化运行入口
├── README.md                    # 项目总览与快速启动
└── AGENTS.md                    # 仓库协作与工具优先级约束
```

## Directory Purposes

**`.github/`:**
- Purpose: 存放 GSD 工作流资产与 AI 协作配置
- Contains: `.github/agents/*.agent.md`、`.github/skills/*/SKILL.md`、`.github/copilot-instructions.md`
- Key files: `.github/agents/gsd-codebase-mapper.agent.md`、`.github/copilot-instructions.md`
- Subdirectories: `agents/`、`skills/`、`get-shit-done/`

**`docs/`:**
- Purpose: 人工可读的集成与运行说明
- Contains: Markdown 说明文档
- Key files: `docs/minimal-integration.md`
- Subdirectories: 当前为平铺结构

**`packages/server/`:**
- Purpose: Spring Boot 后端工程
- Contains: `pom.xml`、Dockerfile、Java 源码、测试、构建产物
- Key files: `packages/server/pom.xml`、`packages/server/src/main/resources/application.yml`
- Subdirectories: `src/main/java/com/earmo/onlyoffice/demo/{config,model,service,web}`

**`packages/web/`:**
- Purpose: Vue 宿主页与 nginx 统一入口
- Contains: `package.json`、`vite.config.js`、`src/`、`nginx.conf`、Dockerfile
- Key files: `packages/web/src/App.vue`、`packages/web/nginx.conf`
- Subdirectories: `src/` 当前是单组件最小结构

**`storage/`:**
- Purpose: 本地示例文档与运行时文件落盘目录
- Contains: `demo.docx` 等被创建或导入的文件
- Key files: `storage/demo.docx`
- Subdirectories: 当前无细分

## Key File Locations

**Entry Points:**
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/OnlyofficeDemoApplication.java` - Spring Boot 启动入口
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/DocumentController.java` - 主要 API 与 callback 路由
- `packages/web/src/main.js` - Vue 挂载入口
- `packages/web/src/App.vue` - 前端核心交互与编辑器容器

**Configuration:**
- `docker-compose.yml` - ONLYOFFICE、server、web 三容器编排
- `packages/server/src/main/resources/application.yml` - Spring Boot 与 demo 配置
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/config/DemoProperties.java` - 配置绑定模型
- `packages/web/vite.config.js` - 前端开发服务器配置
- `packages/web/nginx.conf` - 同源反向代理与 SPA 托管

**Core Logic:**
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/service/` - 业务服务层
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/model/` - 请求/响应与文档模型
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/GlobalExceptionHandler.java` - 错误响应出口

**Testing:**
- `packages/server/src/test/java/com/earmo/onlyoffice/demo/service/` - 后端服务层测试
- `packages/server/target/surefire-reports/` - 已提交的 Maven 测试报告产物

**Documentation:**
- `README.md` - 快速启动与系统概览
- `docs/minimal-integration.md` - 关键接口、网络拓扑与生产前差距说明
- `AGENTS.md` - 本仓库的 AI 协作规范

## Naming Conventions

**Files:**
- Java 类使用 `PascalCase.java`，例如 `OnlyofficeConfigService.java`
- Vue 根组件使用 `PascalCase.vue`，当前是 `App.vue`
- JavaScript 配置与入口使用常见小写文件名，如 `main.js`、`vite.config.js`
- 测试文件统一以 `*Test.java` 结尾

**Directories:**
- 顶层采用清晰分区：`packages/server`、`packages/web`、`docs`、`storage`
- Java 包路径采用 `com/earmo/onlyoffice/demo/...`
- 功能分层目录为 `config`、`model`、`service`、`web`

**Special Patterns:**
- `target/` 是 Maven 构建输出目录，属于生成物
- `.github/skills/` 与 `.github/agents/` 属于 GSD 工作流元数据，不是业务代码
- `.planning/codebase/` 作为后续规划阶段读取的上下文文档目录

## Where to Add New Code

**New API / backend feature:**
- Primary code: `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/` 与 `.../service/`
- Models: `packages/server/src/main/java/com/earmo/onlyoffice/demo/model/`
- Tests: `packages/server/src/test/java/com/earmo/onlyoffice/demo/`

**New frontend behavior:**
- Implementation: `packages/web/src/`
- Shared styles: `packages/web/src/style.css`
- If UI grows: 优先在 `packages/web/src/components/` 或 feature 目录拆分，而不是继续把逻辑堆在 `App.vue`

**New docs / operator guidance:**
- User/developer docs: `docs/`
- AI / workflow rules: `.github/` 或 `AGENTS.md`

## Special Directories

**`packages/server/target/`:**
- Purpose: Maven 编译产物、Surefire 报告与打包结果
- Source: `mvn test` / `mvn package`
- Committed: 当前仓库里已存在，属于应谨慎对待的生成目录

**`.planning/codebase/`:**
- Purpose: 供 GSD 后续规划读取的代码地图
- Source: `$gsd-map-codebase`
- Committed: 视团队流程而定，当前流程建议提交

---

*Structure analysis: 2026-03-17*
*Update when directory structure changes*
