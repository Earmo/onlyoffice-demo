(function () {
  // 这个文件运行在 ONLYOFFICE 隐藏插件 iframe 内部。
  // 它的职责是把编辑器内部能力转换成宿主页可调用的 postMessage 协议。
  const BRIDGE_CHANNEL = "onlyoffice-ai-bridge";
  const EVENTS = {
    ready: "onlyoffice-ai-bridge:ready",
    error: "onlyoffice-ai-bridge:error",
    captureSelection: "onlyoffice-ai-bridge:capture-selection",
    selectionCaptured: "onlyoffice-ai-bridge:selection-captured",
    refreshOutline: "onlyoffice-ai-bridge:refresh-outline",
    outlineRefreshed: "onlyoffice-ai-bridge:outline-refreshed",
    jumpToHeading: "onlyoffice-ai-bridge:jump-to-heading",
    headingJumped: "onlyoffice-ai-bridge:heading-jumped"
  };

  function getHostWindow() {
    // 插件实际可能嵌在多层 iframe 里，统一发给最外层页面最稳妥。
    return window.top || window.parent;
  }

  function postMessage(type, payload, requestId) {
    getHostWindow().postMessage(
      {
        channel: BRIDGE_CHANNEL,
        type,
        requestId,
        payload: payload ?? {},
        capability: "plugin"
      },
      "*"
    );
  }

  function postError(message, requestId) {
    getHostWindow().postMessage(
      {
        channel: BRIDGE_CHANNEL,
        type: EVENTS.error,
        requestId,
        message
      },
      "*"
    );
  }

  function readSelectedText(requestId) {
    // 通过 ONLYOFFICE 插件方法读取当前选区文本。
    // 这里显式约定换行和表格分隔符，保证宿主页拿到的文本更适合直接送入 AI。
    window.Asc.plugin.executeMethod(
      "GetSelectedText",
      [{
        Numbering: false,
        Math: false,
        TableCellSeparator: "\n",
        TableRowSeparator: "\n",
        ParaSeparator: "\n",
        TabSymbol: "\t",
        NewLineSeparator: "\n"
      }],
      function (result) {
        const text = typeof result === "string" ? result : "";
        postMessage(
          EVENTS.selectionCaptured,
          {
            text,
            emptySelection: text.trim().length === 0
          },
          requestId
        );
      }
    );
  }

  function refreshOutline(requestId) {
    window.Asc.plugin.callCommand(function () {
      // ONLYOFFICE 段落 JSON 在不同版本下结构不完全一致，
      // 所以这里用递归方式尽量从 text/value/content/children 等字段中抽取纯文本。
      function readTextFromNode(node) {
        if (node === null || node === undefined) {
          return "";
        }
        if (typeof node === "string") {
          const trimmed = node.trim();
          if ((trimmed.startsWith("{") || trimmed.startsWith("[")) && trimmed.length > 1) {
            try {
              return readTextFromNode(JSON.parse(trimmed));
            } catch {
              return node;
            }
          }
          return node;
        }
        if (Array.isArray(node)) {
          return node.map(readTextFromNode).join("");
        }
        if (typeof node !== "object") {
          return "";
        }
        if (typeof node.text === "string") {
          return node.text;
        }
        if (typeof node.value === "string") {
          return node.value;
        }

        const keys = ["content", "children", "elements", "items", "runs"];
        let text = "";
        for (let index = 0; index < keys.length; index += 1) {
          const child = node[keys[index]];
          if (child) {
            text += readTextFromNode(child);
          }
        }
        return text;
      }

      function getParagraphText(paragraph) {
        // 优先走 ToJSON，拿不到时再降级遍历段落元素，兼容不同编辑器版本。
        if (paragraph && typeof paragraph.ToJSON === "function") {
          const paragraphJson = paragraph.ToJSON();
          const paragraphText = readTextFromNode(paragraphJson).replace(/\s+/g, " ").trim();
          if (paragraphText) {
            return paragraphText;
          }
        }

        if (
          paragraph
          && typeof paragraph.GetElementsCount === "function"
          && typeof paragraph.GetElement === "function"
        ) {
          const count = paragraph.GetElementsCount();
          let text = "";
          for (let index = 0; index < count; index += 1) {
            const element = paragraph.GetElement(index);
            if (element && typeof element.ToJSON === "function") {
              text += readTextFromNode(element.ToJSON());
            }
          }
          return text.replace(/\s+/g, " ").trim();
        }

        return "";
      }

      function getStyleName(paragraph) {
        // 标题级别优先从段落样式中解析，例如 Heading 1 / Heading 2。
        const paraPr = paragraph && typeof paragraph.GetParaPr === "function" ? paragraph.GetParaPr() : null;
        const style = paraPr && typeof paraPr.GetStyle === "function" ? paraPr.GetStyle() : null;
        return style && typeof style.GetName === "function" ? style.GetName() : "";
      }

      function getOutlineLevel(paragraph) {
        const outlineLevel = paragraph && typeof paragraph.GetOutlineLvl === "function"
          ? paragraph.GetOutlineLvl()
          : undefined;
        if (typeof outlineLevel === "number" && outlineLevel >= 0) {
          return outlineLevel + 1;
        }
        return null;
      }

      function getStyleLevel(styleName) {
        // ONLYOFFICE 的标题样式既可能是英文 Heading 1，也可能是中文 标题 1，
        // 所以这里统一兼容两种写法，尽量贴近左侧官方目录树的层级判断。
        const normalizedStyleName = String(styleName || "").replace(/\u00a0/g, " ").trim();
        const matched = /^(?:Heading|标题)\s*(\d+)\b/i.exec(normalizedStyleName);
        if (matched) {
          return Number(matched[1]);
        }
        return null;
      }

      function getLevel(paragraph, styleName, fallbackLevel) {
        // 优先使用显式样式级别，其次再回退到 outline level。
        return getStyleLevel(styleName) || getOutlineLevel(paragraph) || fallbackLevel || 1;
      }

      // 先收集官方认定的 heading internalId，再遍历全文兜底补齐 Heading 样式段落。
      const doc = Api.GetDocument();
      const allParagraphs = typeof doc.GetAllParagraphs === "function" ? doc.GetAllParagraphs() : [];
      const headingParagraphs = typeof doc.GetAllHeadingParagraphs === "function" ? doc.GetAllHeadingParagraphs() : [];
      const headingMetaById = {};

      for (let index = 0; index < headingParagraphs.length; index += 1) {
        const paragraph = headingParagraphs[index];
        if (paragraph && typeof paragraph.GetInternalId === "function") {
          const styleName = getStyleName(paragraph);
          headingMetaById[paragraph.GetInternalId()] = {
            level: getLevel(paragraph, styleName, null),
            styleName
          };
        }
      }

      const headings = [];
      for (let index = 0; index < allParagraphs.length; index += 1) {
        const paragraph = allParagraphs[index];
        if (!paragraph) {
          continue;
        }

        const styleName = getStyleName(paragraph);
        const internalId = typeof paragraph.GetInternalId === "function" ? paragraph.GetInternalId() : "";
        const headingMeta = internalId ? headingMetaById[internalId] : null;
        const styleLevel = getStyleLevel(styleName);
        const outlineLevel = getOutlineLevel(paragraph);
        const isHeading = Boolean(headingMeta) || styleLevel !== null || outlineLevel !== null;
        if (!isHeading) {
          continue;
        }

        headings.push({
          // 宿主页用 paragraphIndex 作为当前阶段最稳定的跳转锚点。
          id: "heading-" + index,
          text: getParagraphText(paragraph),
          level: getLevel(paragraph, styleName, headingMeta?.level ?? null),
          styleName: styleName || headingMeta?.styleName || "",
          paragraphIndex: index
        });
      }

      return headings;
    }, false, false, function (result) {
      const headings = Array.isArray(result) ? result : [];
      postMessage(
        EVENTS.outlineRefreshed,
        {
          headings,
          emptyOutline: headings.length === 0
        },
        requestId
      );
    });
  }

  function jumpToHeading(requestId, payload) {
    // Asc.scope 用来把宿主页传入的数据带到 callCommand 执行环境内部。
    window.Asc.scope.targetParagraphIndex = Number(payload && payload.paragraphIndex);
    window.Asc.scope.targetHeadingId = payload && payload.id ? String(payload.id) : "";

    window.Asc.plugin.callCommand(function () {
      const doc = Api.GetDocument();
      const allParagraphs = typeof doc.GetAllParagraphs === "function" ? doc.GetAllParagraphs() : [];
      const targetIndex = Number(Asc.scope.targetParagraphIndex);

      if (!Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= allParagraphs.length) {
        return {
          ok: false,
          message: "目标标题位置已失效，请先刷新章节目录。"
        };
      }

      // Hack to scroll the target paragraph to the top of the viewport:
      // First select the last paragraph (scrolls viewport down), then the target paragraph (scrolls viewport up).
      if (allParagraphs.length > 0) {
        const lastParagraph = allParagraphs[allParagraphs.length - 1];
        if (lastParagraph && typeof lastParagraph.Select === "function") {
          lastParagraph.Select();
        }
      }

      const paragraph = allParagraphs[targetIndex];
      const selected = paragraph && typeof paragraph.Select === "function" ? paragraph.Select() : false;
      return {
        ok: selected === true,
        id: Asc.scope.targetHeadingId,
        paragraphIndex: targetIndex,
        message: selected === true ? "" : "章节定位失败，请刷新目录后重试。"
      };
    }, false, false, function (result) {
      if (!result || result.ok !== true) {
        postError(result?.message || "章节定位失败，请刷新目录后重试。", requestId);
        return;
      }

      postMessage(
        EVENTS.headingJumped,
        {
          id: result.id,
          paragraphIndex: result.paragraphIndex
        },
        requestId
      );
    });
  }

  function handleHostMessage(event) {
    const message = event.data;
    if (!message || message.channel !== BRIDGE_CHANNEL || typeof message.type !== "string") {
      return;
    }

    try {
      // 插件只暴露非常克制的三类命令：抓选区、刷目录、跳标题。
      // 真正的 AI 调用仍放在宿主页或后端，不放进插件里。
      switch (message.type) {
        case EVENTS.captureSelection:
          readSelectedText(message.requestId);
          break;
        case EVENTS.refreshOutline:
          refreshOutline(message.requestId);
          break;
        case EVENTS.jumpToHeading:
          jumpToHeading(message.requestId, message.payload || {});
          break;
        default:
          break;
      }
    } catch (error) {
      postError(error instanceof Error ? error.message : "插件桥接执行失败。", message.requestId);
    }
  }

  let hostMessageBound = false;

  function notifyReady() {
    // ready 是宿主页 waitForReady 的唯一完成信号。
    postMessage(EVENTS.ready, {});
  }

  function registerPluginRuntime() {
    if (!window.Asc || !window.Asc.plugin) {
      return false;
    }

    window.Asc.plugin.init = function () {
      if (!hostMessageBound) {
        // 只绑定一次 message 监听，避免插件重复 init 时积累多个监听器。
        window.addEventListener("message", handleHostMessage);
        hostMessageBound = true;
      }
      notifyReady();
    };

    window.Asc.plugin.button = function () {};
    return true;
  }

  function waitForPluginRuntime() {
    // ONLYOFFICE 注入插件运行时存在异步延迟，轮询直到 Asc.plugin 可用再注册。
    if (registerPluginRuntime()) {
      return;
    }

    window.setTimeout(waitForPluginRuntime, 50);
  }

  waitForPluginRuntime();
})();
