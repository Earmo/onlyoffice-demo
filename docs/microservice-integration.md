# 微服务接入说明

## 适用场景

当上游业务系统希望把本项目当作“文档编辑微服务”接入，而不是直接交付官方前端整套体验时，优先阅读本页。

## 集成原则

- `API-first`
  上游系统先调服务端 API 建立文档上下文
- `headless-first, UI-available`
  官方前端可直接复用，但不是唯一接入方式
- `low coupling`
  用户上下文通过 SPI/provider 解析，不把外部认证方案耦合进文档核心逻辑

## 推荐接入流程

1. 上游系统调用以下任一入口创建文档上下文
   - `POST /api/documents`
   - `POST /api/documents/upload`
   - `POST /api/documents/import-remote`
2. 获取内部 `documentId`
3. 调用 `GET /api/documents/{documentId}/editor-config`
4. 由上游系统决定：
   - 跳转官方前端 `/editor/{documentId}`
   - 或在自己的前端里消费 `editor-config`

## 用户上下文接入

当前用户上下文模型遵循 `SPI-first, built-ins included`：

- 内置显式策略：`header`、`jwt`
- 内置补齐策略：`default`
- 自定义接入：实现 `AccessContextProvider` Bean 并加入 `enabled-providers` / `resolution-order`

其中要特别注意：

- `header` / `jwt` 用来回答“这次请求的身份到底从哪里来”
- `default` 只负责在允许的场景里补齐缺失字段，不应被当成第三种显式身份来源
- 如果请求里完全没有命中任何显式策略，而 `require-explicit-context=true`，服务会直接返回明确 `4xx`

默认 Header 输入：

- `X-Tenant-Id`
- `X-Source-System`
- `X-External-User-Id`
- `X-User-Display-Name`
- `X-Access-Permissions`

默认 JWT 输入：

- 通过 `Authorization: Bearer <token>` 透传
- claim 映射由 `onlyoffice.integration.access-context.jwt.claim-mappings.*` 控制

最小策略配置示例：

```yaml
onlyoffice:
  integration:
    access-context:
      enabled-providers:
        - header
        - jwt
        - default
      resolution-order:
        - header
        - jwt
        - default
      require-explicit-context: true
      allow-default-context: false
```

如果你需要接入自定义用户来源，可继续注册新的 `AccessContextProvider`，并把它放到 `resolution-order` 前面；controller 和文档业务服务不需要为了新来源再改解析逻辑。

## 官方前端入口

如果你直接复用仓库内的官方前端，当前页面语义已经拆成两类：

- `/preview/{documentId}`
  只读查看，不建立活跃编辑会话
- `/editor/{documentId}`
  进入独立编辑工作台，建立当前用户的编辑会话

列表页现在会显式区分：

- `查看文件`
- `编辑文档`

## 权限与审计边界

- 当前只消费最小权限集合：`edit/comment/download/print`
- create/upload/import/editor-config 会写轻量访问审计
- ONLYOFFICE callback 会记录为 `system event`

## 你通常不需要做的事

- 不需要让上游前端自己拼 `document.url` 或 `callbackUrl`
- 不需要修改 controller 才能接入自定义用户来源
- 不需要把 ONLYOFFICE callback 可信性压在来源 IP 白名单上，Phase 5 已改成 JWT 验签为主

## 继续阅读

- 最小接入细节见 [minimal-integration.md](./minimal-integration.md)
- 环境变量与部署差异见 [configuration-matrix.md](./configuration-matrix.md)
