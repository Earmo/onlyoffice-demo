# Repository Guidelines

## Project Structure & Module Organization

当前仓库处于初始化阶段，顶层目录尚无源码、测试或构建配置。新代码请按以下约定落库，避免后续迁移：

- `src/`：应用源码，按功能拆分，如 `src/editor/`、`src/api/`
- `tests/`：自动化测试，与功能目录一一对应
- `public/` 或 `assets/`：静态资源，如图片、示例文档、图标
- `docs/`：设计说明、接口约定、接入记录

如果引入前端或服务端子项目，优先使用清晰的模块目录，例如 `packages/web/`、`packages/server/`。

## Build, Test, and Development Commands

仓库当前未定义构建脚本。引入工具链后，请在根目录统一提供常用命令，并保持文档同步。推荐最少包含：

- `npm install`：安装依赖
- `npm run dev`：启动本地开发环境
- `npm run build`：生成生产构建
- `npm test`：运行全部测试
- `npm run lint`：执行静态检查

如果项目不是 Node.js，请在本文件中替换为实际命令，例如 `mvn test` 或 `pnpm dev`。

## Coding Style & Naming Conventions

统一使用 UTF-8 和 2 空格缩进。目录名、脚本名使用 `kebab-case`，变量和函数使用 `camelCase`，类型、类、组件使用 `PascalCase`。测试文件建议命名为 `*.test.*` 或 `*.spec.*`。引入格式化或检查工具后，优先使用 `Prettier` + `ESLint`，并通过脚本统一执行。

## Logging & Observability

默认不要只在异常分支打日志。新增或修改业务代码时，应主动补齐足够的 `info` 级业务日志，覆盖排障最需要的关键节点，至少包括：

- 请求或任务开始
- 关键分支命中与状态迁移，例如创建、取消、完成、回退、重试、超时
- 外部依赖调用的开始与结束，例如数据库外的 HTTP、对象存储、OnlyOffice、LLM provider
- 异步链路的重要收口点，例如流式响应打开、关闭、中断、完成

日志内容应优先包含稳定的排障标识，例如 `documentId`、`sessionId`、`requestId`、`provider`、`model`、用户作用域或租户信息。禁止在 `info` 日志中输出密钥、令牌、完整敏感正文或高频 token 级噪音；高频明细只允许放在 `debug`，并默认关闭。

## Testing Guidelines

测试代码放在 `tests/` 或与源码同级的 `__tests__/` 中。每个新增功能至少补 1 个正常路径测试；修复缺陷时必须补回归测试。提交前至少运行一次完整测试和 lint。

## Commit & Pull Request Guidelines

当前目录没有 Git 历史可参考，建议从一开始采用 Conventional Commits，例如 `feat: add editor bootstrap`、`fix: handle empty document state`。PR 应包含变更摘要、验证方式、关联问题；涉及 UI 时附截图，涉及配置时注明新增环境变量和默认值。

## Security & Configuration Tips

不要提交密钥、令牌或本地 `.env`。示例配置请提供 `.env.example`。第三方服务接入说明统一写入 `docs/`，并记录所需权限、回调地址和最小配置项。

Tool Priority

- 文件名搜索：使用 `fd`
- 文本内容搜索：使用 `rg` (ripgrep)
- 代码结构搜索：使用 `sg` (ast-grep)
- 排除目录：`.git`, `node_modules`, `dist`, `coverage`
