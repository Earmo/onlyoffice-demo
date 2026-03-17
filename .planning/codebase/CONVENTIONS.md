# Coding Conventions

**Analysis Date:** 2026-03-17

## Naming Patterns

**Files:**
- Java 源文件采用 `PascalCase.java`，按职责放到 `config`、`model`、`service`、`web`
- Vue 当前只有 `App.vue` 单根组件；若继续扩展，组件名应延续 `PascalCase.vue`
- Java 测试采用 `*Test.java`，位于 `packages/server/src/test/java/...`
- 文档与配置常见为小写或 kebab-case，如 `minimal-integration.md`、`docker-compose.yml`

**Functions / Methods:**
- Java 与 JavaScript 均使用 `camelCase`
- 事件处理或 UI 动作用 `handle*`、`toggle*`、`load*`、`switchTo*` 风格，见 `packages/web/src/App.vue`
- 后端 service 方法以业务动作命名，如 `buildEditorConfig`、`saveCallbackDocument`、`recordSaveSucceeded`

**Variables:**
- 局部变量与字段使用 `camelCase`
- 常量使用 `UPPER_SNAKE_CASE`，例如 `DEFAULT_EXTENSION`、`SUPPORTED_EXTENSIONS`
- Vue 响应式状态普遍带 `is*`、`current*`、`*Ref` 前缀，便于区分布尔态与引用

**Types:**
- Java record / DTO / 配置类使用 `PascalCase`
- 不使用 `I` 前缀接口命名风格
- 响应类命名清晰指向用途，如 `EditorConfigResponse`、`InsertImageResponse`

## Code Style

**Formatting:**
- 仓库文档约定 UTF-8 与 2 空格缩进，见 `AGENTS.md`
- Java 与 Vue 源码普遍保持较短语句块、空行分组和尾随分号
- JavaScript 使用双引号；Java 维持 Spring 生态常见格式
- 注释量偏多，但主要在解释“为什么这样做”，不是机械复述

**Linting / Formatting Tooling:**
- 未发现 ESLint、Prettier、Checkstyle 或 Spotless 配置文件
- 前端 `packages/web/package.json` 未定义 `lint` 脚本
- 当前风格更多依赖现有代码习惯而不是自动化规则

## Import Organization

**Java:**
1. 先导入项目内包
2. 再导入 JDK / 第三方包
3. 同类导入相邻放置，按格式化工具结果排序

**JavaScript / Vue:**
1. 外部依赖先于本地依赖
2. 本地样式放在最后，如 `packages/web/src/main.js`
3. 未使用路径别名，均为相对路径或包名导入

## Error Handling

**Patterns:**
- service 内直接抛 `IllegalArgumentException` 或 `IOException`
- controller 通常不做大段 `try/catch`，把失败交给统一异常处理器
- `packages/server/src/main/java/com/earmo/onlyoffice/demo/web/GlobalExceptionHandler.java` 是后端错误边界
- 前端 fetch 流程统一先检查 `response.ok`，再通过 `readErrorMessage()` 回收服务端消息

**Error Types:**
- 输入不合法: 抛 `IllegalArgumentException`
- 远程资源为空、回写失败等 I/O 场景: 抛 `IOException`
- 保存回调失败会额外记入 `DocumentStatusService`，再继续抛出错误

## Logging

**Framework:**
- 未引入专门日志框架封装
- 后端依赖 Spring Boot 默认日志
- 前端使用少量 `console.log` / `console.error`

**Patterns:**
- 日志不是当前示例的重点，更多通过接口返回状态给 UI
- 可见状态展示优先于详细日志，比如最近保存状态卡片

## Comments

**When to Comment:**
- 代码里大量块注释解释 ONLYOFFICE 协议背景、布局字段作用、网络拓扑原因
- 注释明显偏向“为什么这样设计”与“生产环境注意事项”
- 复杂配置段前会有简短说明，例如 `OnlyofficeConfigService` 对 layout/customization 的解释

**Doc Comments:**
- Java public 类与方法普遍有 Javadoc 风格注释
- Vue `<script setup>` 中也有中文行内注释说明页面状态与交互目的

## Function Design

**Patterns:**
- 后端 service 倾向一个公开方法配若干私有 helper，职责边界比较清晰
- controller 方法较薄，主要做参数接线与响应拼装
- 前端 `App.vue` 当前集中承载所有交互，适合最小示例，但继续扩展时应拆组件或 composable

**Return Values:**
- Java 后端优先返回专用响应 DTO，而不是裸 `Map`，callback 成功响应是少数例外
- 前端异步函数倾向早返回和 `try/catch/finally` 结构

## Module Design

**Backend:**
- 典型 Spring 分层：controller -> service -> model/config
- `DocumentStorageService` 与 `OnlyofficeImageService` 是边界型 service，负责外部网络与文件系统访问

**Frontend:**
- 当前没有组件树与状态管理库，`App.vue` 就是主模块
- `main.js` 尽量保持极薄，只做挂载和全局对象初始化

## Repository-Level Guidance

- `AGENTS.md` 要求优先使用 `fd`、`rg`、`sg` 搜索，并排除 `.git`、`node_modules`、`dist`、`coverage`
- 仓库说明建议新增功能至少补 1 条正常路径测试，修 bug 要补回归测试
- GSD 指令存在于 `.github/` 与 `.codex/`，做自动化协作时应尽量遵循这些本地规范

---

*Convention analysis: 2026-03-17*
*Update when patterns change*
