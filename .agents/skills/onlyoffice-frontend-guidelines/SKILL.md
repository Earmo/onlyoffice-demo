---
name: onlyoffice-frontend-guidelines
description: ONLYOFFICE 前端详细规范。修改 packages/web 下 Vue 3 前端、页面、组件、路由、store、API 封装、ONLYOFFICE bridge、AI 工作台、样式或前端测试时优先使用。
---

# ONLYOFFICE 前端详细规范

## 适用范围

前端代码位于：

```text
packages/web
```

处理前端任务时，优先读取本 skill；涉及具体编辑器工作台时再读取：

- `onlyoffice-web-workbench`
- `onlyoffice-editor-security`
- `onlyoffice-delivery-observability`

## 技术栈

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Vitest

## 目录职责

- `src/pages`：页面级容器，如文档库、预览页、编辑页。
- `src/components`：可复用 UI 和业务组件。
- `src/components/editor`：ONLYOFFICE 编辑器壳层、bridge、AI 工作台、事件流。
- `src/components/library`：文档列表和创建入口。
- `src/lib/api.js`：后端 API 封装。
- `src/router`：路由定义。
- `src/stores`：Pinia store。
- `src/test`：前端测试。
- `public`：静态资源和 ONLYOFFICE 插件资源。

## 编码规范

- Vue 组件使用 `PascalCase.vue`。
- JS 变量和函数使用 `camelCase`。
- 组件职责保持清晰：页面负责组织流程，组件负责展示和局部交互。
- 后端 API 调用统一经 `src/lib/api.js` 或现有封装，不在组件里散落 fetch 细节。
- 解析后端响应时显式处理 `ResponseDto`、分页字段、错误信息。
- 状态共享使用 Pinia，不用跨组件隐式全局变量。
- 编辑器 bridge 逻辑放在 `onlyofficeBridge.js`，不要直接在页面里堆 ONLYOFFICE 全局对象操作。

## UI/交互规范

这是业务工作台，不做营销型落地页。

界面应优先满足：

- 信息密度适中，可快速扫描文档状态。
- 创建、导入、打开、预览、编辑、删除等操作入口明确。
- loading、empty、error、success 状态完整。
- 保存状态、写回状态、运行事件、AI 流式响应要有明确反馈。
- 操作失败要给用户可理解的错误，不只写 console。
- 不用装饰性过强的 hero、卡片堆叠或营销文案。

## API 与本地代理

本地开发默认通过 Vite 代理 `/api` 到后端：

```powershell
$env:VITE_DEV_API_PROXY_TARGET="http://localhost:8080"
corepack pnpm --dir packages/web dev
```

修改 API 时：

- 同步检查后端 request/response model。
- 同步更新 `api.js`。
- 同步更新页面或组件测试 mock。
- 分页、筛选、排序参数要与后端模型一致。

## ONLYOFFICE 前端集成

- 编辑器容器和生命周期由 `EditorShell.vue` 负责。
- ONLYOFFICE 全局桥接逻辑放在 `onlyofficeBridge.js`。
- 运行事件流放在 `runtimeEventStream.js`。
- LLM 消息流放在 `llmMessageStream.js`。
- 写回状态放在 `writeBackStore.js`。
- 加载 Document Server 脚本、初始化 editor、销毁 editor 时必须处理失败和清理逻辑。
- 不在前端保存 JWT secret、对象存储密钥或后端内部地址。

## 前端测试

前端测试命令：

```powershell
npm run test:web
corepack pnpm --dir packages/web test -- --run
```

新增或修改前端行为时：

- 页面变更补 page test。
- 组件变更补 component test。
- API 解析变更补 mock 响应测试。
- ONLYOFFICE bridge、事件流、LLM 流式、写回 store 改动必须补单元或组件测试。
- 修复缺陷时补回归测试。

## 前端构建检查

构建命令：

```powershell
npm run build:web
corepack pnpm --dir packages/web build
```

交付前检查：

- 前端测试通过。
- 构建通过。
- UI 状态完整，不出现无反馈按钮或静默失败。
- API mock 与真实后端响应结构一致。
- 不提交 `dist`、`node_modules`、临时截图或本地 `.env`。
