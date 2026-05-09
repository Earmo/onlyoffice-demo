export function jsonResponse(payload, options = {}) {
  // 统一构造最小 fetch Response 形状，减少各测试文件重复手写样板。
  return Promise.resolve({
    ok: options.ok ?? true,
    status: options.status ?? 200,
    json: async () => payload
  });
}

export async function flushPromises() {
  // Vue + fetch + 微任务链组合测试里，单次 Promise.resolve 往往不够，
  // 这里封装成统一工具，保证 DOM 和异步副作用都尽量刷新完成。
  await Promise.resolve();
  await Promise.resolve();
  await new Promise(resolve => setTimeout(resolve, 0));
}
