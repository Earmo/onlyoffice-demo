const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "";

// 本地开发和独立调试时，前端需要自己带上最小访问上下文，
// 否则后端的 AccessContextResolver 会把请求当成“完全没有身份来源”而直接拒绝。
// 真正接入上游系统后，这些默认值可以被外部注入的 env 覆盖，或进一步替换成网关透传值。
/**
 * 判断字符串是否可以安全写入浏览器请求头。
 *
 * @param {string} value - 待检查的 header value。
 * @returns {boolean} true 表示所有字符都位于 ISO-8859-1 范围内。
 */
function isIso88591Safe(value) {
  return /^[\u0000-\u00FF]*$/.test(value);
}

/**
 * 将 env 或表单里的访问上下文字段规整成 fetch 可接受的 header value。
 *
 * @param {unknown} value - 外部输入值，可能为空、非字符串或包含中文。
 * @param {string} fallbackValue - 输入不可用时使用的 ASCII 兜底值。
 * @returns {string} 可直接放入请求头的值。
 */
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

/**
 * 读取前端运行环境中的默认访问上下文。
 *
 * @returns {Record<string, string>} 后端 AccessContextResolver 需要的最小 header 集合。
 */
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
  // 支持在 nginx 同源代理和独立调试 baseUrl 两种场景之间切换。
  return `${apiBaseUrl}${path}`;
}

const CUSTOM_CONTEXT_STORAGE_KEY = "MOCK_ACCESS_CONTEXT";

/**
 * 获取本地调试覆盖的访问上下文。
 *
 * @returns {{tenantId?: string, actorUser?: string, actorName?: string, sourceSystem?: string} | null}
 *   用户在工作台里保存过的上下文；不存在或 JSON 损坏时返回 null。
 */
export function getCustomAccessContext() {
  try {
    const stored = localStorage.getItem(CUSTOM_CONTEXT_STORAGE_KEY);
    return stored ? JSON.parse(stored) : null;
  } catch {
    return null;
  }
}

/**
 * 保存或清空本地调试访问上下文。
 *
 * @param {{tenantId?: string, actorUser?: string, actorName?: string, sourceSystem?: string} | null} context
 *   传入对象时写入 localStorage，传入 null/undefined 时清空。
 */
export function saveCustomAccessContext(context) {
  // 允许传 null 作为“清空上下文”的语义，方便后续扩展重置动作。
  if (!context) {
    localStorage.removeItem(CUSTOM_CONTEXT_STORAGE_KEY);
  } else {
    localStorage.setItem(CUSTOM_CONTEXT_STORAGE_KEY, JSON.stringify(context));
  }
}

/**
 * 合并默认上下文、本地调试上下文和调用方自定义 header。
 *
 * @param {HeadersInit} headers - 单次请求额外传入的 header，优先级最高。
 * @returns {Record<string, string>} 发送给后端的最终 header 集合。
 */
export function createAccessContextHeaders(headers = {}) {
  const defaultAccessContextHeaders = resolveDefaultAccessContextHeaders();
  const customContext = getCustomAccessContext() || {};

  // 默认值 + 本地自定义上下文 + 调用方传入 headers 三层合并：
  // 调用方 headers 放在最后，保留针对单个请求显式覆盖的能力。
  const mergedHeaders = { ...defaultAccessContextHeaders };

  if (customContext.tenantId) {
    mergedHeaders["X-Tenant-Id"] = normalizeHeaderValue(customContext.tenantId, mergedHeaders["X-Tenant-Id"]);
  }
  if (customContext.sourceSystem) {
    mergedHeaders["X-Source-System"] = normalizeHeaderValue(customContext.sourceSystem, mergedHeaders["X-Source-System"]);
  }
  if (customContext.actorUser) {
    mergedHeaders["X-External-User-Id"] = normalizeHeaderValue(customContext.actorUser, mergedHeaders["X-External-User-Id"]);
  }
  if (customContext.actorName) {
    mergedHeaders["X-User-Display-Name"] = normalizeHeaderValue(customContext.actorName, mergedHeaders["X-User-Display-Name"]);
  }

  return {
    ...mergedHeaders,
    ...headers
  };
}

/**
 * 判断 headers 中是否已经显式设置某个 header。
 *
 * @param {Record<string, string>} headers - 已合并后的请求头。
 * @param {string} headerName - 待查找的 header 名称。
 * @returns {boolean} true 表示已存在同名 header。
 */
function hasHeader(headers, headerName) {
  const normalizedHeaderName = headerName.toLowerCase();
  return Object.keys(headers).some((name) => name.toLowerCase() === normalizedHeaderName);
}

/**
 * 项目统一 fetch 入口。
 *
 * @param {string} path - API 路径，支持相对路径并自动拼接 VITE_API_BASE_URL。
 * @param {RequestInit} options - 原生 fetch 参数。
 * @returns {Promise<Response>} 原生 fetch response。
 */
export function apiFetch(path, options = {}) {
  // 项目内统一通过 apiFetch 发请求，避免有人漏带访问上下文头。
  const headers = createAccessContextHeaders(options.headers);
  if (typeof options.body === "string" && !hasHeader(headers, "Content-Type")) {
    headers["Content-Type"] = "application/json";
  }

  return fetch(buildApiUrl(path), {
    ...options,
    headers
  });
}

export async function parseJsonEnvelope(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(payload?.message || `请求失败，HTTP ${response.status}`);
    error.status = response.status;
    error.errorCode = payload?.code || payload?.errorCode || "";
    error.payload = payload;
    throw error;
  }
  if (payload && typeof payload === "object" && Object.prototype.hasOwnProperty.call(payload, "data")) {
    return payload.data;
  }
  return payload;
}
