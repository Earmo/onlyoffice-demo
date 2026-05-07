# ONLYOFFICE Integration Skills Index

本文件仅作为 `onlyoffice-integration-starter` 项目的 skills 索引。处理本项目文件时，优先读取并遵守本目录 `.agents/skills/*/SKILL.md`；如与上层目录 skills 冲突，以本项目 skills 为准。

## 项目 Skills

### 通用

- `.agents/skills/onlyoffice-project-basics/SKILL.md`：项目定位、模块结构、根命令、文档导航、编码命名和搜索工具优先级。
- `.agents/skills/onlyoffice-delivery-observability/SKILL.md`：构建验证、Docker Compose、profile、本地调试、安全配置、日志规范、提交和 PR。

### 后端

- `.agents/skills/onlyoffice-backend-guidelines/SKILL.md`：后端详细规范，覆盖 `packages/server` 的目录职责、编码、AccessContext、数据事务、日志、测试和交付检查。
- `.agents/skills/onlyoffice-backend-api/SKILL.md`：Spring Boot 后端 API、Controller、ResponseDto、异常处理、AccessContext 和业务日志。
- `.agents/skills/onlyoffice-data-migrations/SKILL.md`：数据模块、Entity、Mapper、Repository、Flyway migration、文档元数据、编辑会话、审计和 LLM 表。
- `.agents/skills/onlyoffice-storage-providers/SKILL.md`：local/minio/cos 存储 provider、DocumentStorageService、StorageKeyFactory、对象 key 和存储环境变量。
- `.agents/skills/onlyoffice-editor-security/SKILL.md`：ONLYOFFICE editor config、callback、JWT、Document Server URL、命令服务、远程资源安全和 nginx header buffer 补丁。

### 前端

- `.agents/skills/onlyoffice-frontend-guidelines/SKILL.md`：前端详细规范，覆盖 `packages/web` 的目录职责、Vue 编码、UI 交互、API 封装、ONLYOFFICE bridge、测试和构建检查。
- `.agents/skills/onlyoffice-web-workbench/SKILL.md`：Vue 3 前端工作台、文档列表/预览/编辑页、EditorShell、ONLYOFFICE bridge、AI 工作台、Vite 代理和前端测试。

## 使用规则

1. 先根据任务类型读取最相关的 skill，不要一次性加载全部 skill。
2. 修改 `packages/server` 时先读后端详细规范；修改 `packages/web` 时先读前端详细规范。
3. 任务跨多个主题时，按需读取多个 skill。例如修改编辑器保存链路通常需要同时读取后端规范、后端 API、数据迁移、编辑器安全、前端规范、前端工作台和交付规则。
4. 面向用户的说明、项目文档和交付记录默认使用简体中文。
