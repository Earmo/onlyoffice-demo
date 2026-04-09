(function () {
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
        const paraPr = paragraph && typeof paragraph.GetParaPr === "function" ? paragraph.GetParaPr() : null;
        const style = paraPr && typeof paraPr.GetStyle === "function" ? paraPr.GetStyle() : null;
        return style && typeof style.GetName === "function" ? style.GetName() : "";
      }

      function getLevel(styleName, outlineLevel) {
        const matched = /^Heading\s+(\d+)/i.exec(styleName || "");
        if (matched) {
          return Number(matched[1]);
        }
        if (typeof outlineLevel === "number" && outlineLevel >= 0) {
          return outlineLevel + 1;
        }
        return 1;
      }

      const doc = Api.GetDocument();
      const allParagraphs = typeof doc.GetAllParagraphs === "function" ? doc.GetAllParagraphs() : [];
      const headingParagraphs = typeof doc.GetAllHeadingParagraphs === "function" ? doc.GetAllHeadingParagraphs() : [];
      const headingIds = {};

      for (let index = 0; index < headingParagraphs.length; index += 1) {
        const paragraph = headingParagraphs[index];
        if (paragraph && typeof paragraph.GetInternalId === "function") {
          headingIds[paragraph.GetInternalId()] = true;
        }
      }

      const headings = [];
      for (let index = 0; index < allParagraphs.length; index += 1) {
        const paragraph = allParagraphs[index];
        if (!paragraph) {
          continue;
        }

        const styleName = getStyleName(paragraph);
        const outlineLevel = typeof paragraph.GetOutlineLvl === "function" ? paragraph.GetOutlineLvl() : undefined;
        const internalId = typeof paragraph.GetInternalId === "function" ? paragraph.GetInternalId() : "";
        const isHeading = headingIds[internalId] || /^Heading\s+/i.test(styleName) || typeof outlineLevel === "number";
        if (!isHeading) {
          continue;
        }

        headings.push({
          id: "heading-" + index,
          text: getParagraphText(paragraph),
          level: getLevel(styleName, outlineLevel),
          styleName,
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
    postMessage(EVENTS.ready, {});
  }

  function registerPluginRuntime() {
    if (!window.Asc || !window.Asc.plugin) {
      return false;
    }

    window.Asc.plugin.init = function () {
      if (!hostMessageBound) {
        window.addEventListener("message", handleHostMessage);
        hostMessageBound = true;
      }
      notifyReady();
    };

    window.Asc.plugin.button = function () {};
    return true;
  }

  function waitForPluginRuntime() {
    if (registerPluginRuntime()) {
      return;
    }

    window.setTimeout(waitForPluginRuntime, 50);
  }

  waitForPluginRuntime();
})();
