const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

// 本地开发和独立调试时，前端需要自己带上最小访问上下文，
// 否则后端的 AccessContextResolver 会把请求当成“完全没有身份来源”而直接拒绝。
// 真正接入上游系统后，这些默认值可以被外部注入的 env 覆盖，或进一步替换成网关透传值。
function isIso88591Safe(value) {
  return /^[\u0000-\u00FF]*$/.test(value);
}

function normalizeHeaderValue(value, fallbackValue) {
  const stringValue = String(value ?? "").trim();
  if (!stringValue) {
    return fallbackValue;
  }

  // fetch 对 header value 只接受 ISO-8859-1 字符集。
  // 本地调试如果直接把“默认用户”这类中文塞进请求头，浏览器会在发请求前就抛错。
  // 这里统一兜底成 ASCII 值，保证页面至少能成功把最小访问上下文发到后端。
  return isIso88591Safe(stringValue) ? stringValue : fallbackValue;
}

function resolveDefaultAccessContextHeaders() {
  return {
    "X-Tenant-Id": normalizeHeaderValue(import.meta.env.VITE_ACCESS_CONTEXT_TENANT_ID, "native"),
    "X-Source-System": normalizeHeaderValue(import.meta.env.VITE_ACCESS_CONTEXT_SOURCE_SYSTEM, "native"),
    "X-External-User-Id": normalizeHeaderValue(import.meta.env.VITE_ACCESS_CONTEXT_EXTERNAL_USER_ID, "starter-user"),
    "X-User-Display-Name": normalizeHeaderValue(import.meta.env.VITE_ACCESS_CONTEXT_DISPLAY_NAME, "Default User"),
    "X-Access-Permissions": normalizeHeaderValue(
      import.meta.env.VITE_ACCESS_CONTEXT_PERMISSIONS,
      "edit=true,download=true,comment=true,print=true"
    )
  };
}

export function buildApiUrl(path) {
  return `${apiBaseUrl}${path}`;
}

export function createAccessContextHeaders(headers = {}) {
  const defaultAccessContextHeaders = resolveDefaultAccessContextHeaders();
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
