import { afterEach, beforeEach, vi } from "vitest";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
  vi.stubGlobal("alert", vi.fn());
  window.DocEditor = { instances: {} };
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.clearAllTimers();
  document.body.innerHTML = "";
});
