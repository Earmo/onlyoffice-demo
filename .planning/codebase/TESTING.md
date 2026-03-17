# Testing Patterns

**Analysis Date:** 2026-03-17

## Test Framework

**Runner:**
- JUnit 5（通过 `spring-boot-starter-test` 引入）
- 当前未见单独 `junit-platform.properties` 或自定义测试配置文件

**Assertion Library:**
- `org.junit.jupiter.api.Assertions`
- 常用断言包括 `assertEquals`、`assertTrue`、`assertNotNull`、`assertNull`

**Run Commands:**
```bash
cd packages/server
mvn test

cd packages/server
mvn -Dtest=OnlyofficeConfigServiceTest test
```

前端目前没有自动化测试命令；`packages/web/package.json` 只定义了 `dev`、`build`、`preview`。

## Test File Organization

**Location:**
- 后端测试全部位于 `packages/server/src/test/java/com/earmo/onlyoffice/demo/service/`
- 目前只覆盖 service 层，没有 controller 层或前端测试目录

**Naming:**
- 统一采用 `*Test.java`
- 当前文件包括 `DocumentStorageServiceTest.java`、`DocumentStatusServiceTest.java`、`OnlyofficeConfigServiceTest.java`、`OnlyofficeImageServiceTest.java`

**Structure:**
```text
packages/server/src/test/java/com/earmo/onlyoffice/demo/service/
├── DocumentStorageServiceTest.java
├── DocumentStatusServiceTest.java
├── OnlyofficeConfigServiceTest.java
└── OnlyofficeImageServiceTest.java
```

## Test Structure

**Suite Organization:**
- 以类为 suite，单个 `@Test` 方法描述单一路径
- 命名偏向 `should...` 风格，个别使用 `@DisplayName` 补中文描述
- 一般直接 new 目标 service，而不是启动 Spring 容器

**Patterns:**
- `@TempDir` 用于文件系统隔离，见 `DocumentStorageServiceTest`
- 直接构造 `DemoProperties`、`RestClient.builder()`、`MockHttpServletRequest` 作为依赖
- 单测偏轻量，聚焦同步业务逻辑和返回值结构

## Mocking

**Framework:**
- 当前几乎不使用 Mockito 或 Spring Test MockBean
- 唯一显式模拟对象是 `MockHttpServletRequest`

**Patterns:**
- 通过真实 `DemoProperties` 和真实 service 实例跑最小闭环
- 网络请求相关逻辑没有被真正打到外部服务；已覆盖的测试主要验证参数组装，而不是远程交互成功路径

**What is effectively mocked by design:**
- 文件系统通过 `@TempDir` 替代真实项目目录
- HTTP request 上下文通过 Spring mock servlet 对象替代

## Fixtures and Factories

**Test Data:**
- 简单输入内联写在测试中，例如 `"sales-report.xlsx"`、`"https://example.com/assets/logo.png"`
- 没有单独 `fixtures/` 或 `factories/` 目录
- 文档与图片测试数据以字符串、字节数组和临时目录构造

## Coverage

**Covered Areas:**
- editor config 构造与只读模式切换
- 默认 demo 文档生成与上传文档扩展名保留
- 保存状态 state machine
- insertImage payload 的签名与代理 URL 生成

**Not Covered:**
- `DocumentController` 各 HTTP 路由
- `GlobalExceptionHandler` 的响应映射
- `DocumentStorageService.importRemoteDocument()` 与 `saveCallbackDocument()` 的远程下载行为
- `OnlyofficeImageService.proxyRemoteImage()` 的真实代理下载与媒体类型判断
- 前端 `packages/web/src/App.vue` 的任意行为

**Requirements:**
- 未见覆盖率门槛、JaCoCo 报表或 CI 阻断规则
- 当前更像是“关键业务函数最小保护”，而非完整测试体系

## Test Types

**Unit Tests:**
- 当前全部属于偏单元测试的 service 级验证
- 不启动 Spring 容器，不依赖数据库，不走真实 Docker 服务

**Integration Tests:**
- 严格意义上的前后端集成测试与 ONLYOFFICE 联调测试暂缺
- `packages/server/target/surefire-reports/` 表明 Maven 测试曾被本地执行

**E2E Tests:**
- 未发现 Playwright、Cypress 或 Selenium
- 文档描述中的主流程目前主要靠人工验证

## Common Patterns

**Async / IO-adjacent Testing:**
- 当前通过临时目录和直接方法调用测试 I/O 结果，而非复杂异步编排

**Error Testing:**
- 已覆盖部分失败消息，例如 `DocumentStatusServiceTest` 中保存失败文案
- URL 非法、本地回环地址、远程下载失败等更多异常路径还未形成系统性测试

**Regression Expectations:**
- 按 `AGENTS.md` 与仓库指南，新增功能应至少补 1 条正常路径测试
- 修复缺陷时建议优先在 `packages/server/src/test/java/...` 补服务层回归用例

---

*Testing analysis: 2026-03-17*
*Update when test patterns change*
