---
name: onlyoffice-web-workbench
description: ONLYOFFICE Vue 前端工作台规则。修改 packages/web、文档列表、创建入口、预览页、编辑页、EditorShell、ONLYOFFICE bridge、AI 工作台、Pinia store、Vite 代理或前端测试时使用。
---

# ONLYOFFICE 前端工作台规则

## 前端模块

前端位于：

```text
packages/web
```

技术栈：

- Vue 3
- Vite
- Vue Router
- Pinia
- Element Plus
- Vitest

## 主要页面与组件

- `DocumentLibraryPage.vue`：文档列表和创建入口。
- `DocumentPreviewPage.vue`：文档预览。
- `DocumentEditorPage.vue`：独立编辑页。
- `DocumentList.vue`：列表组件。
- `DocumentCreateActions.vue`：创建入口。
- `EditorShell.vue`：ONLYOFFICE 编辑器壳层。
- `EditorAiWorkbench.vue`：编辑器 AI 工作台。
- `onlyofficeBridge.js`：ONLYOFFICE 前端桥接。
- `runtimeEventStream.js`：运行事件流。
- `llmMessageStream.js`：LLM 消息流。
- `writeBackStore.js`：写回状态 store。

## API 与代理

前端 API 封装在：

```text
packages/web/src/lib/api.js
```

本地开发默认通过 Vite 代理 `/api` 到后端：

```powershell
$env:VITE_DEV_API_PROXY_TARGET="http://localhost:8080"
corepack pnpm dev
```

修改接口时同步检查后端 `ResponseDto` / 分页结构和前端解析逻辑。

## 测试

前端测试位于：

```text
packages/web/src/test
```

运行：

```powershell
corepack pnpm --dir packages/web test -- --run
```

新增页面、组件、桥接或 store 行为时补 Vitest 测试。

## UI 规则

- 这是业务工作台，不做营销型落地页。
- 优先保持信息密度、清晰状态和可操作性。
- 文档编辑、保存状态、运行事件、AI 流式响应要有明确 loading、error、empty、success 状态。
