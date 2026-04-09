import { describe, expect, it, vi } from "vitest";
import { createOnlyofficeBridge, ONLYOFFICE_AI_BRIDGE_EVENTS } from "../components/editor/onlyofficeBridge";

describe("onlyofficeBridge", () => {
  it("应记录 ready 事件来源并向真实插件窗口发送后续请求", async () => {
    // 这是修复过的关键回归点：
    // ready 事件来自插件真正所在窗口，后续命令必须发回这个 source，而不是中间 iframe。
    const pluginWindow = {
      postMessage: vi.fn()
    };
    const editorWindow = {
      postMessage: vi.fn()
    };

    const bridge = createOnlyofficeBridge({
      getEditor: () => null,
      getIframe: () => ({
        contentWindow: editorWindow
      }),
      requestTimeoutMs: 1000
    });

    window.dispatchEvent(new MessageEvent("message", {
      data: {
        channel: "onlyoffice-ai-bridge",
        type: ONLYOFFICE_AI_BRIDGE_EVENTS.ready,
        capability: "plugin"
      },
      source: pluginWindow
    }));

    await expect(bridge.waitForReady()).resolves.toEqual({ capability: "plugin" });

    const capturePromise = bridge.captureSelectedText();
    await Promise.resolve();
    expect(pluginWindow.postMessage).toHaveBeenCalledWith(
      expect.objectContaining({
        channel: "onlyoffice-ai-bridge",
        type: ONLYOFFICE_AI_BRIDGE_EVENTS.captureSelection
      }),
      "*"
    );
    expect(editorWindow.postMessage).not.toHaveBeenCalled();

    const captureRequest = pluginWindow.postMessage.mock.calls[0][0];
    window.dispatchEvent(new MessageEvent("message", {
      data: {
        channel: "onlyoffice-ai-bridge",
        type: ONLYOFFICE_AI_BRIDGE_EVENTS.selectionCaptured,
        requestId: captureRequest.requestId,
        payload: {
          text: "已选中文本",
          emptySelection: false
        }
      },
      source: pluginWindow
    }));

    await expect(capturePromise).resolves.toEqual({
      text: "已选中文本",
      emptySelection: false
    });

    bridge.dispose();
  });
});
