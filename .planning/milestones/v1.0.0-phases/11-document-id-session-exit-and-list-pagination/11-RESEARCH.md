# Phase 11: document-id-session-exit-and-list-pagination - Research

## Research Objective

回答这一个问题：为了把内部 `documentId` 改成 ULID、让编辑退出状态真正收敛、并把列表切到后端分页查询，我们需要在当前代码里改哪里、沿用什么库能力、以及哪些地方有额外风险。

## Documentation Findings

### MyBatis-Flex ID Generation

- 当前仓库使用 `mybatis-flex` `1.11.6`。
- 本地依赖检查确认 `mybatis-flex-core-1.11.6.jar` 内存在：
  - `com.mybatisflex.core.keygen.impl.ULIDKeyGenerator`
  - `com.mybatisflex.core.keygen.KeyGenerators.ulid`
- Context7 文档显示 MyBatis-Flex 支持通过 `@Id(keyType = KeyType.Generator, value = "...")` 接入自定义 / 内置主键生成器，也支持在服务层显式调用生成器后再写入实体。
- 对本阶段来说，更关键的不是“插入时自动回填主键”，而是“在写对象存储前就拿到稳定内部 ID”。因此执行时需要一条可在写存储对象前生成 ULID 的实现链路。

### MyBatis-Flex Pagination

- Context7 文档显示 `BaseMapper` 支持 `paginate(pageNumber, pageSize, queryWrapper)`。
- QueryChain 也支持 `page(new Page<>(pageNumber, pageSize))`。
- 这意味着 Phase 11 可以把 `query / status / sourceSystem / documentType / sortDirection` 这些当前内存筛选条件下沉到 repository / mapper 层，而不是先全量取回再 `.stream().filter(...)`。

### Element Plus Pagination

- Phase 10 已经切到 Element Plus。
- Phase 11 可直接使用 `el-pagination`，采用 `v-model:current-page`、`v-model:page-size` 和 `:total` 的后端分页绑定模式，不需要引入额外分页库。

## Codebase Findings

### 1. 内部 documentId 仍然绑定文件名 / 用户输入

当前主问题集中在 `DocumentStorageServiceImpl`：

- `createNativeDocument(...)` 仍允许 `rawDocumentId` 参与内部主键生成；
- `storeUploadedDocument(...)` 通过 `buildGeneratedDocumentId(stripExtension(originalFilename))` 生成 `documentId`；
- `importRemoteDocument(...)` 复用上传链路，因此也会沿用“文件名 stem + 时间戳”生成 `documentId`；
- `buildGeneratedDocumentId(...)` 当前实现是 `sanitizeDocumentId(filenameStem) + "-" + System.currentTimeMillis()`；
- 上传成功后标题会写成 `documentId + "." + extension`，导致用户标题和内部主键继续耦合。

这直接带来两类问题：

1. 内部 ID 不稳定，且仍然暴露了用户文件名语义；
2. 上传 / 导入后返回给前端的 `title` 与用户实际文件名不再解耦。

### 2. 列表查询已经部分“后端化”，但还不是分页接口

当前链路如下：

- `DocumentMetadataRepository.listByTenant(tenantId)` 只按租户取全量列表；
- `DocumentMetadataServiceImpl.listDocuments(...)` 在 service 层用 Java Stream 做：
  - `query`
  - `status`
  - `sourceSystem`
  - `documentType`
  - `sortDirection`
- `DocumentApiController.list(...)` 再额外做：
  - `activeEditingCounts`
  - `storageAvailable` 投影
  - `storage` 参数过滤
- `DocumentListResponse` 只返回 `tenantId / actorUser / actorName / documents`，没有 `pageNumber / pageSize / total / totalPages`。

这说明：

1. 当前“筛选在后端接口里”只完成了一半，仍然依赖 service 层全量取数；
2. 当前接口无法自然承载分页；
3. 一旦分页，`storage=available/unavailable` 会成为特殊路径，因为它依赖对象存储探测，不是纯数据库列。

### 3. 前端列表页仍把“已加载结果”当作筛选数据源

`DocumentLibraryPage.vue` 目前：

- 持有 `searchQuery / statusFilter / documentTypeFilter / sourceSystemFilter / storageFilter / sortDirection`；
- 每次 `loadDocuments()` 调 `/api/documents?...`；
- 但 `statusOptions / documentTypeOptions / sourceSystemOptions` 是从 `documents.value` 动态推导的；
- 整页默认仍拉全量文档，再在前端展示“最近文档”与筛选项。

这意味着：

1. 虽然不是严格的本地 `.filter()`，但前端 UI 仍依赖当前已加载集合来决定可选筛选项；
2. 一旦切成分页，当前页不应再决定“全局有哪些状态 / 类型 / 来源”。

### 4. 返回列表 / 切换文档已经显式关会话，但状态收敛仍有竞态窗口

已有能力：

- `DocumentController` 已暴露 `POST /api/documents/{documentId}/editing-sessions/close`；
- `DocumentStatusServiceImpl.closeEditingSession(...)` 会：
  - 关闭当前 actor 的活跃会话；
  - 查询当前文档剩余活跃编辑数；
  - 在为 0 时调用 `documentMetadataService.reconcileClosedEditingSession(documentId)`；
- `DocumentEditorPage.goBackToLibrary()` 和 `requestOpenDocument()` 已经在导航前 `await closeCurrentEditingSession()`；
- `EditorShell.onBeforeUnmount()` 也会再次调用 `closeEditingSession({ keepalive: true, suppressErrors: true })`。

仍然存在的风险点：

1. 显式返回 / 切换与 `onBeforeUnmount` 会形成双关闭路径，需要守卫避免重复调用或顺序竞争；
2. 编辑页当前更偏“结束会话”，没有显式的“离开前收口运行态 / 停止轮询 / 保证列表重新加载”统一编排；
3. 用户反馈“返回列表后仍显示编辑中”，说明至少有一个环节没有把“活跃会话为 0”及时投影回列表结果。

### 5. “断开并保存文档”目前没有独立的退出编排

当前退出链路的重点是：

- 关闭编辑会话；
- 停止轮询；
- 组件卸载。

但没有独立的“退出前主动保存”编排层。现有保存语义仍主要依赖 ONLYOFFICE callback 流程。执行时需要明确：

- 返回列表的“保存”到底是依赖 callback 自然到达，还是需要显式等待某个运行态结果；
- 至少要保证“即便 callback 还未到达，列表也不能因为活跃会话已结束而继续显示 editing”。

### 6. 后端测试基线健康，前端测试基线存在已知破窗

实际验证结果：

- 后端定向测试
  `mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentApiControllerTest,DocumentStatusServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  可以通过。
- 前端定向测试当前在收集阶段失败，错误为：
  `Unknown file extension ".css" for ... element-plus/theme-chalk/base.css`

这意味着：

1. Phase 11 想补前端分页 / 状态回归测试，必须先修复 Element Plus 在 Vitest 环境下的样式导入基线；
2. 否则计划里的前端验证命令会形同虚设。

## Implementation Recommendations

### Recommendation 1: 把 ULID 生成抽成显式内部能力

不要继续把 `documentId` 生成逻辑散在 `buildGeneratedDocumentId(...)` 里。更合适的做法是：

- 增加一个明确的内部 ID 生成入口；
- 该入口直接调用 MyBatis-Flex ULID 能力；
- 创建 / 上传 / 导入三条链路都在写入对象存储前先拿到 ULID。

这样能同时满足：

- 存储 key 需要稳定内部 ID；
- API 返回值和数据库主键保持一致；
- 不依赖插入后回填才能知道内部 ID。

### Recommendation 2: 列表分页采用“双路径后端过滤”

为了兼顾分页正确性和实现成本，建议：

- `storage=all` 时：
  - 走 repository 分页；
  - 其他可数据库化筛选全下沉；
- `storage=available/unavailable` 时：
  - 仍然全部在后端完成；
  - 先按数据库条件取候选集，再做 `storageAvailable` 探测和后端切页；
  - 保证 `documents` 和 `total` 一致。

这样虽然 `storage` 过滤路径会更重，但能避免把问题重新推回前端。

### Recommendation 3: 前端退出路径增加一次性 leave guard

建议把以下动作收口为一次性编排：

1. 停止保存状态轮询；
2. 如当前是编辑模式，执行一次显式 close session；
3. 仅在 close 成功后继续导航；
4. `onBeforeUnmount` 只作为兜底，不重复发第二次主关闭请求。

这能降低“关闭成功了但列表还拿到旧状态”或“双请求互相覆盖”的风险。

## Validation Architecture

1. **Backend contract tests**
   - `cd packages/server && mvn -q -pl onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentStorageServiceTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,DocumentStatusServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
2. **Frontend regression tests**
   - `cd packages/web && corepack pnpm test -- --run`
   - 前提：先修好 Element Plus CSS 在 Vitest 中的导入基线。
3. **Frontend build**
   - `cd packages/web && corepack pnpm build`

## Recommended Plan Split

1. **Plan 11-01:** 后端内部文档 ID 改造成 MyBatis-Flex ULID，并解耦标题 / 外部 ID。
2. **Plan 11-02:** 把文档列表接口改成后端驱动分页查询，并让前端真正按页拉取数据。
3. **Plan 11-03:** 收口编辑页退出编排、状态收敛和前后端回归测试，确保返回列表后不再残留 `editing`。
