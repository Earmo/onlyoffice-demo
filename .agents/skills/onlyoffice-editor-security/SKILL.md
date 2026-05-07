---
name: onlyoffice-editor-security
description: ONLYOFFICE 编辑器集成与安全规则。修改 editor-config、Document Server URL、callback、JWT、download-as、远程图片、命令服务、OnlyofficeJwtService 或安全边界时使用。
---

# ONLYOFFICE 编辑器与安全规则

## 关键服务

- `OnlyofficeConfigService`：生成 editor config。
- `OnlyofficeJwtService`：ONLYOFFICE JWT 签名与校验。
- `OnlyofficeCommandService`：Document Server command 调用。
- `OnlyofficeImageService`：远程图片插入和图片资源处理。
- `OnlyofficeDocumentKeyResolver`：文档 key 解析。
- `RemoteResourceSecurityService`：远程资源安全校验。
- `DocumentStatusService`：保存状态与运行事件。

## URL 配置

常用环境变量：

```text
ONLYOFFICE_INTEGRATION_PUBLIC_BASE_URL
ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_URL
ONLYOFFICE_INTEGRATION_DOCUMENT_SERVER_COMMAND_URL
ONLYOFFICE_INTEGRATION_INTERNAL_BASE_URL
ONLYOFFICE_INTEGRATION_JWT_SECRET
```

浏览器访问 Document Server 资源时，默认同源前缀形如：

```text
http://host:12333/api/office/
```

Document Server 回调或命令访问内部服务时，注意区分 public、document-server、command、internal base URL。

## JWT 与安全

- ONLYOFFICE JWT secret 必须由环境变量提供，真实环境不要使用示例默认值。
- editor config、callback、command 请求涉及 JWT 时必须保持签名逻辑一致。
- 不要在日志或前端输出 JWT secret、token、完整 callback payload 中的敏感内容。
- 远程图片或外部 URL 必须经过 `RemoteResourceSecurityService` 检查，避免 SSRF 和不可信资源访问。

## Callback 与保存

修改 callback 逻辑时：

- 保持 ONLYOFFICE 状态码语义。
- 更新文档保存状态和运行事件。
- 处理重复 callback 和并发保存冲突。
- 失败路径写入可排查日志，但不要泄露敏感内容。

## ONLYOFFICE Nginx 补丁

Compose 为 ONLYOFFICE `download as` 的长 URI 场景注入：

```text
infra/nginx/onlyoffice-header-buffers.conf
```

不要随意移除该挂载，否则可能因 header buffer 过小触发 413。
