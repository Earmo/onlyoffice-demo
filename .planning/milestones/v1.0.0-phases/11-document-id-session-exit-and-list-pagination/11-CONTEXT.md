# Phase 11: document-id-session-exit-and-list-pagination - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段聚焦 3 件彼此关联的收口工作：
1. 把新建 / 上传 / 导入网络文档后的内部 `documentId` 统一改成服务端生成的稳定唯一标识；
2. 让“返回列表 / 切换文档 / 离开编辑页”真正结束当前编辑会话，并在没有活跃编辑用户时收敛列表状态；
3. 把文档列表改造成后端驱动的分页查询，前端不再基于已加载结果做本地筛选语义。

不在本阶段内的内容：
- 不重做 Phase 10 的 Element Plus 视觉方向；
- 不引入新的权限模型、批量操作或复杂统计；
- 不扩展版本历史、回滚中心或协同后台。

</domain>

<decisions>
## Implementation Decisions

### Internal Document ID
- **D-01:** `POST /api/documents`、`POST /api/documents/upload`、`POST /api/documents/import-remote` 统一由服务端生成内部 `documentId`，不再复用文件名、用户自定义名称或调用方传入的 `documentId`。
- **D-02:** 内部 `documentId` 必须使用 MyBatis-Flex 的 ULID 能力生成，计划执行时应落到 `KeyGenerators.ulid` / `ULIDKeyGenerator` 这一条具体实现链路，而不是继续使用毫秒时间戳拼接。

### Title / External ID Decoupling
- **D-03:** `title` 继续承担用户可见标题语义。显式创建沿用用户输入标题或默认 `untitled.docx`；上传和网络导入保留原始文件名作为标题。
- **D-04:** `externalDocumentId` 继续只表达上游业务对象 ID；它不能再被混同成内部文档主键。

### Editor Exit and State Convergence
- **D-05:** 返回列表、切换文档和页面卸载都必须走显式编辑会话结束动作；只有在这个动作完成后才允许导航离开当前编辑页。
- **D-06:** 文档列表与详情页的“编辑中”语义继续以活跃编辑会话为真相源；当活跃编辑数归零时，主表状态必须立即收口回 `saved` / `failed` / `draft`，不能继续残留 `editing`。

### Backend-Driven Pagination
- **D-07:** 文档列表分页、搜索与筛选全部通过后端接口完成。前端只维护查询参数和分页参数，不再把“当前已加载列表”当成筛选数据源。
- **D-08:** 保持 Phase 10 的 Element Plus 视觉体系，分页组件统一使用 `el-pagination`，不要退回自定义列表或自定义分页条。

### the agent's Discretion
- `storage=available/unavailable` 在分页下如何实现“结果正确 + total 正确”的后端路径，可以按代码成本选择双路径方案，但不能回退成前端二次筛选。
- `CreateDocumentRequest.documentId` 是否保留字段做兼容承载可由执行代理决定，但无论保留与否，都不能再让它参与内部主键生成。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope
- `.planning/ROADMAP.md` — Phase 11 的路线图入口与依赖关系。
- `.planning/STATE.md` — 最近 phase 演进记录与现有注意事项。

### Backend Identity and List Query
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStorageServiceImpl.java` — 当前 `documentId` 生成、标题回填和存储 key 拼装入口。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentMetadataServiceImpl.java` — 文档主状态、列表筛选和状态收口逻辑。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java` — 列表、创建、上传、导入接口契约。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/model/DocumentListResponse.java` — 当前列表响应模型。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepository.java` — 当前 repository 查询入口。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentMetadataEntity.java` — 文档主数据实体定义。

### Runtime Session Lifecycle
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStatusServiceImpl.java` — 活跃编辑会话与状态收敛逻辑。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java` — editor-config、close session、save-status 协议入口。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentEditorSessionRepository.java` — 活跃会话查询与计数。

### Frontend Library and Editor Pages
- `packages/web/src/pages/DocumentLibraryPage.vue` — 列表查询参数、加载与回流高亮。
- `packages/web/src/components/library/DocumentList.vue` — 列表展示组件。
- `packages/web/src/components/library/DocumentCreateActions.vue` — 新建 / 上传 / 导入入口。
- `packages/web/src/pages/DocumentEditorPage.vue` — 返回列表、切换文档与导航动作。
- `packages/web/src/components/editor/EditorShell.vue` — 关闭编辑会话、轮询和编辑器运行态。

### Verification Baseline
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStorageServiceTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentMetadataServiceTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentStatusServiceTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java`
- `packages/web/src/test/DocumentLibraryPage.test.js`
- `packages/web/src/test/DocumentEditorPage.test.js`
- `packages/web/src/test/EditorShell.test.js`
- `packages/web/vite.config.js`

</canonical_refs>

<specifics>
## Phase-Specific Requirement IDs

- **PH11-ID-01:** 新建 / 上传 / 导入网络文档后返回的内部 `documentId` 必须是服务端生成的 ULID，而不是文件名或自定义名称。
- **PH11-SESSION-01:** 返回列表、切换文档或离开页面后，如果该文档已没有活跃编辑用户，列表和详情都不应继续显示 `editing`。
- **PH11-LIST-01:** 文档列表接口必须支持分页；搜索与筛选全部经由后端接口执行，前端只负责提交查询和展示结果。

## Original User Intent

1. 重构后端文件 ID `documentId` 规则：在文档新建 / 上传 / 导入网络文档后，统一生成唯一 ID（使用 MyBatis-Flex ULID）替代当前文件名称 / 自定义名称作为 `documentId`。
2. 编辑页点击返回文档列表后，应断开文档编辑页面连接并保存文档；当前返回文档列表时仍显示“编辑中”，需要检查并修复状态管理问题。
3. 重构文档列表和列表接口，使其支持分页查询；取消列表前端筛选，所有筛选查询都基于后端接口。

</specifics>

<deferred>
## Deferred Ideas

- 不在本阶段增加游标分页、复杂排序组合或批量筛选保存。
- 不在本阶段重做文档删除、归档、批量操作入口。
- 不在本阶段扩展 ONLYOFFICE 版本历史、显式“保存并退出”独立工作流或协作成员面板。

</deferred>

---

*Phase: 11-document-id-session-exit-and-list-pagination*
*Context gathered: 2026-03-31*
