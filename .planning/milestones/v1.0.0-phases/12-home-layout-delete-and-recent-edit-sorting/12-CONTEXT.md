# Phase 12: home-layout-delete-and-recent-edit-sorting - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段聚焦 3 件彼此联动的工作：
1. 收口工作台首页的桌面端双栏比例、满高布局和“开始编辑”入口层级；
2. 为文档列表增加“逻辑删除”能力，并让删除后的文档从默认列表与左侧最近文档中消失；
3. 让文档列表和最近文档都改成“按最近编辑时间倒序”的后端真相源，而不是继续从当前页结果切片推导。

不在本阶段内的内容：
- 不做物理删除，不清理对象存储中的文档内容；
- 不新增回收站、恢复删除、批量删除或管理员归档视图；
- 不重做编辑页布局、ONLYOFFICE 保存协议或权限模型。

</domain>

<decisions>
## Implementation Decisions

### Homepage Layout
- **D-01:** 工作台首页桌面端采用约 `1/5 : 4/5` 的左右布局；移动端继续按单列堆叠，不为追求比例破坏可用性。
- **D-02:** 左右两侧主盒子都需要“贴到页面底边”：左侧通过“上下文卡片 + 可伸展的最近文档卡片”填满剩余高度，右侧通过“筛选工具条 + 可伸展的列表卡片 + 底部分页条”填满剩余高度。
- **D-03:** `开始编辑` 按钮上移到列表头部提示语的上方；`先查看，再决定是否进入编辑` 不再使用强调型标题，而是与右侧说明文案保持同级的字号和颜色。

### Logical Delete
- **D-04:** 删除采用逻辑删除语义，优先复用 `document_metadata.status = archived` 作为删除态，不物理删除对象存储文件。
- **D-05:** 用户侧默认列表、最近文档和常规详情 / 预览 / 编辑入口都把 `archived` 文档视为“已删除不可见”，不能继续从标准入口打开。
- **D-06:** 为避免删除仍在协同中的文档，删除动作默认只允许作用于“无活跃编辑会话”的文档；如果还有活跃编辑用户，接口应拒绝删除并返回明确错误。

### Recent Documents and Sorting
- **D-07:** 左侧最近文档不能继续通过 `documents.slice(0, 3)` 从当前页结果推导；它必须来自独立的后端真相源，且不受当前分页页码和筛选条件影响。
- **D-08:** “最近编辑时间”统一以 `document_metadata.updated_time` 作为后端排序依据，因为它已经覆盖打开编辑器、收到 callback、保存成功/失败和状态收敛等编辑相关活动；对前端应以 `lastEditedTime` 或等价只读字段暴露。
- **D-09:** 默认用户列表与最近文档都按最近编辑时间倒序排序；前端不能再把“最近保存时间”误当作排序真相源。

### the agent's Discretion
- 最近文档真相源可以通过单独的 `/api/documents/recent` 接口提供，也可以通过列表响应附带独立字段提供；重点是它不能继续依赖当前页切片。
- 已归档文档的直接访问返回 `404` 还是 `410` 可由执行时决定，但需要在前后端行为上保持一致。
- 删除按钮的交互可以选择 `el-popconfirm`、二次确认对话框或等价方案，但必须在误触保护和效率之间取得平衡。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Scope
- `.planning/ROADMAP.md` — Phase 12 的范围、依赖和计划名称。
- `.planning/STATE.md` — 当前 milestone 状态、最近 quick task 与后续 focus。
- `.planning/quick/260331-kj3-homepage-two-column-layout/260331-kj3-PLAN.md` — 前一个 quick task 已完成的首页双栏改动边界。
- `.planning/todos/pending/2026-03-31-homepage-two-column-layout-and-start-edit-entry.md` — 首页双栏与开始编辑入口的原始意图记录。

### Frontend Library Homepage
- `packages/web/src/pages/DocumentLibraryPage.vue` — 当前工作台首页的双栏布局、筛选、最近文档和分页状态。
- `packages/web/src/components/library/DocumentList.vue` — 当前列表头部文案、“开始编辑”入口和操作列。
- `packages/web/src/components/library/DocumentCreateActions.vue` — 创建 / 上传 / 导入入口的当前承载组件。
- `packages/web/src/test/DocumentLibraryPage.test.js` — 当前首页分页、创建回流和路由行为测试。

### Backend Document Visibility and Runtime
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/entity/DocumentMetadataEntity.java` — `status`、`updatedTime`、`lastOpenedTime` 等字段的主数据定义。
- `packages/server/onlyoffice-integration-data/src/main/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepository.java` — 当前列表查询与排序实现。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/DocumentMetadataService.java` — 文档主数据服务契约。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentMetadataServiceImpl.java` — 当前状态流转、列表查询和 `archived` 摘要语义。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentApiController.java` — 当前列表 / 详情 / 创建 / 上传 / 导入接口。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/web/DocumentController.java` — editor-config、文件下载和 save-status 运行时入口。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/DocumentStorageServiceImpl.java` — 文件读取和 callback 回写目前如何依赖 `requireDocument(...)`。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/AccessAuditService.java` — 访问审计动作定义。
- `packages/server/onlyoffice-integration-service/src/main/java/com/earmo/onlyoffice/integration/service/impl/AccessAuditServiceImpl.java` — 当前 create/upload/import/editor-config/callback 的审计事件落库。

### Verification Baseline
- `packages/server/onlyoffice-integration-data/src/test/java/com/earmo/onlyoffice/integration/data/repository/DocumentMetadataRepositoryTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/DocumentMetadataServiceTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/service/AccessAuditServiceTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentApiControllerTest.java`
- `packages/server/onlyoffice-integration-service/src/test/java/com/earmo/onlyoffice/integration/web/DocumentControllerTest.java`
- `packages/web/src/test/DocumentLibraryPage.test.js`

</canonical_refs>

<specifics>
## Phase-Specific Requirement IDs

- **PH12-UI-01:** 首页桌面端应采用约 `1/5 : 4/5` 左右布局，左右主盒子自适应到底边，且 `开始编辑` 与提示语层级满足新的视觉要求。
- **PH12-DELETE-01:** 文档列表提供逻辑删除能力；删除后文档不会再出现在默认列表和左侧最近文档中，也不能继续从标准入口预览或编辑。
- **PH12-RECENT-01:** 文档列表和最近文档都以最近编辑时间倒序的后端结果为准，最近文档不再从当前分页结果切片推导。

## Original User Intent

1. 首页左右布局比例改为 `1/5 : 4/5`，两边的盒子自适应到页面底边。
2. `先查看，再决定是否进入编辑` 提示语字号和颜色与右侧提示语一致，`开始编辑` 按钮移动到提示语上方。
3. 列表增加删除操作，可删除文档（逻辑删除），删除后列表和左侧最近文档不再展示该文档。
4. 列表排序按照最近编辑时间倒序排序。

</specifics>

<deferred>
## Deferred Ideas

- 不在本阶段加入回收站、恢复删除或批量删除。
- 不在本阶段增加管理员查看归档文档的专用列表。
- 不在本阶段扩展“最近文档”到跨租户或跨来源系统的聚合分析。

</deferred>

---

*Phase: 12-home-layout-delete-and-recent-edit-sorting*
*Context gathered: 2026-03-31*
