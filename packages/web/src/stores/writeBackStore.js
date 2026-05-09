import { createPinia, defineStore } from "pinia";

const fallbackPinia = createPinia();

const useWriteBackStoreBase = defineStore("writeBack", {
  state: () => ({
    /** @type {'idle'|'loading'|'success'|'error'} */
    status: "idle",
    /** 仅 status==='error' 时有值 */
    errorMsg: "",
  }),
  actions: {
    reset() {
      this.status = "idle";
      this.errorMsg = "";
    },
  },
});

/**
 * 写回文档操作的反馈状态 store。
 * EditorAiWorkbench 监听 status 变化以控制 loading/success/error UI；
 * EditorShell.handleInsertHtml 负责更新此 store。
 *
 * 状态流转：
 *   idle -> loading（confirmWriteBack 触发）
 *   loading -> success（bridge.insertHtml 成功）
 *   loading -> error（bridge 未就绪或 insertHtml 抛出异常）
 *   success|error -> idle（watch 处理完毕后 reset）
 */
export function useWriteBackStore(pinia = fallbackPinia) {
  return useWriteBackStoreBase(pinia);
}
