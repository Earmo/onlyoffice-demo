# Phase 12: home-layout-delete-and-recent-edit-sorting - Research

## Research Objective

回答这一个问题：为了把首页双栏布局收口到新的比例与满高约束、给列表加上逻辑删除，并让最近文档和主列表都按最近编辑时间倒序，我们需要在当前代码里改哪里，以及哪些地方最容易留下“看起来改了、语义其实没收口”的风险。

## Documentation Findings

### Frontend Baseline

- 当前前端已经在 Phase 10 引入 Element Plus，并在 Phase 11 修复了 Vitest 对 Element Plus 样式导入的基线问题。
- 这意味着本阶段不需要引入新的 UI 库，布局、表格操作列、确认框、分页和弹窗都可以继续沿用 Element Plus 现有能力。

### Backend Baseline

- 当前后端列表分页已经在 Phase 11 收口为数据库分页 + 服务端筛选的组合模式。
- `document_metadata` 已具备 `status`、`updated_time`、`last_opened_time`、`last_saved_time` 等字段，足够承载“逻辑删除”和“最近编辑时间排序”，不需要额外做一轮大表结构扩张。

## Codebase Findings

### 1. 首页比例已经接近，但还没有达到本次要求

`DocumentLibraryPage.vue` 当前桌面端使用 `:lg="10"` 和 `:lg="14"`，约等于 `2/5 : 3/5`。这来自 2026-03-31 的 quick task，只解决了“左右分栏”，没有解决这次新增的 3 个更细粒度约束：

1. 目标比例改成约 `1/5 : 4/5`；
2. 左右两边的主盒子要自适应撑到底边；
3. 列表头部的 CTA 层级要收口到“按钮在上、说明在下”的形式。

当前页面 `el-row` 和卡片都还是自然高度流式布局，因此在桌面端高度不足时，左右两侧不会形成真正的“贴底”工作台。

### 2. 列表头部文案层级仍然是旧版本

`DocumentList.vue` 当前头部结构是：

- 一个 `18px` 的 `<h2>`：`先查看，再决定是否进入编辑`
- 按钮 `开始编辑` 与这个标题并排
- 右侧另有一条 `14px` 的 `muted-copy`

这与用户的最新要求正好相反：提示语应该退回到与说明文字同级的次级文案，而不是继续作为强调标题；`开始编辑` 应该上移到提示语的上方，变成更清晰的主动作入口。

### 3. 左侧“最近文档”仍然是从当前页切出来的

`DocumentLibraryPage.vue` 目前通过：

```js
const recentDocuments = computed(() => documents.value.slice(0, 3));
```

生成最近文档。这会带来 3 个问题：

1. 一旦用户切到第 2 页，左侧最近文档就不再是“最近文档”，而是“当前页前 3 条”；
2. 一旦用户输入搜索词或筛选条件，左侧最近文档就被当前过滤结果污染；
3. 删除动作即便成功，如果最近文档不是独立真相源，也可能因为当前页未覆盖该文档而产生“列表没了、左侧还在”或反过来的错觉。

结论：左侧最近文档必须从独立的后端真相源获取，不能继续共享当前页 `documents`。

### 4. “最近编辑排序”后端已经有雏形，但前后端语义没打通

`DocumentMetadataRepository.buildTenantQuery(...)` 当前默认就是按 `updated_time DESC` 排序，这意味着：

- 后端已经有“最近优先”的基本能力；
- 但这套语义还没有被正式命名为“最近编辑时间”；
- `DocumentSummaryResponse` 也没有把 `updatedTime` 暴露给前端。

结果就是：

1. 前端列表只能显示 `lastSavedTime`，用户看到的是“最近保存”，不是“最近编辑”；
2. 最近文档卡片也显示 `lastSavedTime`，进一步加剧“排序依据”和“展示依据”不一致的问题。

本阶段更像是“把已存在的 `updated_time` 语义正式收口成产品合同”，而不是重新发明排序字段。

### 5. 逻辑删除所需的 `archived` 状态存在，但没有变成可用能力

当前服务层已经定义了：

- `DocumentMetadataService.STATUS_ARCHIVED = "archived"`

但现状仍然缺少完整的删除能力闭环：

1. 没有删除接口；
2. repository 列表查询不会默认排除 `archived`；
3. `DocumentApiController.detail(...)`、`DocumentController.editorConfig(...)`、`DocumentController.file(...)`、`DocumentStorageServiceImpl.getRequiredDocument(...)` 都继续按“只要存在就可访问”的语义运行；
4. 访问审计服务也没有记录“删除文档”这一业务动作。

换句话说，`archived` 现在只是一个状态值，不是完整的“逻辑删除产品能力”。

### 6. 如果只隐藏列表、不封禁运行时入口，会留下 stale link 风险

当前多个运行时入口直接依赖 `requireDocument(...)`：

- 文档详情
- editor-config
- save-status
- 文件下载
- callback 回写

如果只是把文档从列表里藏起来，而不对归档文档增加“已删除不可访问”的守卫，那么：

1. 旧的预览/编辑链接仍然可以继续打开；
2. 用户会感知到“列表里删掉了，但地址还能进”，语义不一致；
3. 后续再做恢复删除或审计时，边界会更混乱。

因此更稳妥的做法是把“用户侧已删除不可见”同步投影到标准读取入口上。

### 7. 删除仍在编辑中的文档是本阶段最大的隐藏风险

当前系统支持活跃编辑会话计数，也会把列表状态投影为 `editing`。如果允许用户直接删除仍有活跃编辑会话的文档，会带来至少两个问题：

1. 另一端编辑器可能还持有旧的 editor-config 和下载地址；
2. callback / save-status / file 等运行时接口在删除后的行为会变得很难定义。

由于用户没有要求“强制删除在线文档”，更稳妥的默认决策应是：

- 当活跃编辑数大于 0 时，删除接口拒绝删除；
- 前端在 `editing` 状态下禁用或隐藏删除按钮，并给出明确说明。

### 8. 当前测试结构要求补一个组件级验证点

`DocumentLibraryPage.test.js` 目前把 `DocumentList.vue` 整个 stub 掉了，所以它并不能验证：

- 头部按钮是否真的在提示语上方；
- 提示语是否变成了与右侧说明一致的次级文案；
- 删除按钮是否显示 / 禁用 / 触发确认。

因此 Phase 12 不能只靠页面级测试，至少还需要一个针对 `DocumentList.vue` 的组件级测试。

## Implementation Recommendations

### Recommendation 1: 以 `archived` 作为逻辑删除真相源

不要新增 `deleted` 布尔字段。本阶段直接把 `status = archived` 作为删除态，原因是：

- 现有领域模型已经承认这个状态；
- 列表、详情和运行时入口都可以围绕它快速加守卫；
- 后续如果要做回收站，也能继续基于 `archived` 扩展。

### Recommendation 2: 最近文档使用独立后端真相源

不要继续让左侧最近文档跟随当前页 `documents`。更稳定的实现是：

- 提供独立的最近文档查询路径；
- 固定按最近编辑时间倒序；
- 固定排除 `archived`；
- 删除、创建、上传、导入后都单独刷新这份数据。

### Recommendation 3: 把 `updatedTime` 正式投影成前端可读的“最近编辑时间”

当前 repository 已经按 `updated_time` 排序，但前端并不知道这件事。执行时应显式收口为：

- 后端响应新增 `lastEditedTime`（或等价字段）；
- 列表和最近文档展示都围绕这个字段；
- `lastSavedTime` 继续保留给编辑页或更细的保存状态场景，不再承担首页“最近编辑”的主角色。

### Recommendation 4: 删除进行“守卫式”设计

删除动作建议采用：

1. 后端在删除前统计活跃编辑会话；
2. 如果仍有活跃编辑用户，则拒绝删除并返回明确消息；
3. 否则把状态改成 `archived`，记录审计事件，并从用户侧读路径中隐藏。

这样可以避免“删除成功了，但另一个编辑器还在写”这种高风险状态。

## Validation Architecture

1. **Backend contract and repository tests**
   - `cd packages/server && mvn -q -pl onlyoffice-integration-data,onlyoffice-integration-service -am -DskipITs "-Dtest=DocumentMetadataRepositoryTest,DocumentMetadataServiceTest,DocumentApiControllerTest,DocumentControllerTest,AccessAuditServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
2. **Frontend regression tests**
   - `cd packages/web && corepack pnpm test -- --run`
3. **Frontend build verification**
   - `cd packages/web && corepack pnpm build`

## Recommended Plan Split

1. **Plan 12-01:** 后端逻辑删除、最近编辑排序与可见性契约。
2. **Plan 12-02:** 首页双栏比例、满高布局与 CTA 文案层级调整。
3. **Plan 12-03:** 最近文档真相源、删除操作接线与前端回归测试。
