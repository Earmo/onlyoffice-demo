import { afterEach, beforeEach, vi } from "vitest";
import { config } from "@vue/test-utils";
import ElementPlus from "element-plus";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";

config.global.plugins = [ElementPlus];
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  config.global.components[key] = component;
}

beforeEach(() => {
  // 每个用例都从干净的 fetch 和 window.DocEditor 全局状态开始，
  // 避免 ONLYOFFICE 相关测试互相污染。
  vi.stubGlobal("fetch", vi.fn());
  vi.stubGlobal("alert", vi.fn());
  window.DocEditor = { instances: {} };
});

afterEach(() => {
  // 统一清理 mock、全局 stub 和 Teleport 残留 DOM。
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.clearAllTimers();
  document.body.innerHTML = "";
});
