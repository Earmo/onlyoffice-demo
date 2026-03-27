const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

// 本地开发和独立调试时，前端需要自己带上最小访问上下文，
// 否则后端的 AccessContextResolver 会把请求当成“完全没有身份来源”而直接拒绝。
// 真正接入上游系统后，这些默认值可以被外部注入的 env 覆盖，或进一步替换成网关透传值。
const defaultAccessContextHeaders = {
  "X-Tenant-Id": import.meta.env.VITE_ACCESS_CONTEXT_TENANT_ID ?? "native",
  "X-Source-System": import.meta.env.VITE_ACCESS_CONTEXT_SOURCE_SYSTEM ?? "native",
  "X-External-User-Id": import.meta.env.VITE_ACCESS_CONTEXT_EXTERNAL_USER_ID ?? "starter-user",
  "X-User-Display-Name": import.meta.env.VITE_ACCESS_CONTEXT_DISPLAY_NAME ?? "默认用户",
  "X-Access-Permissions": import.meta.env.VITE_ACCESS_CONTEXT_PERMISSIONS ?? "edit=true,download=true,comment=true,print=true"
};

export function buildApiUrl(path) {
  return `${apiBaseUrl}${path}`;
}

export function createAccessContextHeaders(headers = {}) {
  return {
    ...defaultAccessContextHeaders,
    ...headers
  };
}

export function apiFetch(path, options = {}) {
  return fetch(buildApiUrl(path), {
    ...options,
    headers: createAccessContextHeaders(options.headers)
  });
}
