// MANUALLY MAINTAINED - 此文件不由构建系统生成，须与 onlyofficeBridge.js ONLYOFFICE_AI_BRIDGE_EVENTS 手动保持同步
(function () {
  // 这个文件运行在 ONLYOFFICE 隐藏插件 iframe 内部。
  // 它的职责是把编辑器内部能力转换成宿主页可调用的 postMessage 协议。
  const BRIDGE_CHANNEL = "onlyoffice-ai-bridge";
  // 所有桥接事件常量：宿主页和插件之间的通信协议约定。
  const EVENTS = {
    ready: "onlyoffice-ai-bridge:ready",              // 插件初始化完成
    error: "onlyoffice-ai-bridge:error",              // 通用错误回传
    captureSelection: "onlyoffice-ai-bridge:capture-selection",       // 宿主页请求抓取选区
    selectionCaptured: "onlyoffice-ai-bridge:selection-captured",     // 选区文本回传
    refreshOutline: "onlyoffice-ai-bridge:refresh-outline",           // 宿主页请求刷新章节目录
    outlineRefreshed: "onlyoffice-ai-bridge:outline-refreshed",       // 章节目录回传
    jumpToHeading: "onlyoffice-ai-bridge:jump-to-heading",            // 宿主页请求跳转到指定标题
    headingJumped: "onlyoffice-ai-bridge:heading-jumped",             // 跳转结果回传
    locateText: "onlyoffice-ai-bridge:locate-text",                   // 宿主页请求定位并临时选中文本
    textLocated: "onlyoffice-ai-bridge:text-located",                 // 定位选中文本结果回传
    insertHtml: "onlyoffice-ai-bridge:insert-html",                   // 宿主页请求向文档写入 HTML
    htmlInserted: "onlyoffice-ai-bridge:html-inserted"                // 写入结果回传
  };

  function getHostWindow() {
    // 插件实际可能嵌在多层 iframe 里，统一发给最外层页面最稳妥。
    return window.top || window.parent;
  }

  /**
   * 向宿主页发送消息。
   * @param {string} type      事件类型，来自 EVENTS 常量
   * @param {object} payload   业务数据
   * @param {string} requestId 请求标识，用于宿主页将响应与请求配对
   */
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

  /**
   * 向宿主页发送错误消息。
   * @param {string} message   错误描述
   * @param {string} requestId 请求标识
   */
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

  /**
   * 通过 ONLYOFFICE 插件方法读取当前选区文本。
   * 显式约定换行和表格分隔符，保证宿主页拿到的文本更适合直接送入 AI。
   */
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

  /**
   * 刷新章节大纲：在编辑器内部扫描所有段落，提取标题信息并回传给宿主页。
   * 整个 callCommand 回调在编辑器沙箱内执行，不能访问外部闭包变量。
   */
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

      /**
       * 提取段落的纯文本内容。
       * 优先走 ToJSON，拿不到时再降级遍历段落元素，兼容不同编辑器版本。
       */
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

      /**
       * 获取段落的样式名称，例如 "Heading 1"、"标题 2" 等。
       */
      function getStyleName(paragraph) {
        const paraPr = paragraph && typeof paragraph.GetParaPr === "function" ? paragraph.GetParaPr() : null;
        const style = paraPr && typeof paraPr.GetStyle === "function" ? paraPr.GetStyle() : null;
        return style && typeof style.GetName === "function" ? style.GetName() : "";
      }

      /**
       * 获取段落的大纲级别（outline level）。
       * ONLYOFFICE API 中 GetOutlineLvl 返回 0-based 索引，这里 +1 转为 1-based 以匹配标题级别。
       * 注意：GetOutlineLvl 是 ApiParaPr 的方法，需先通过 GetParaPr() 获取段落属性对象。
       */
      function getOutlineLevel(paragraph) {
        var paraPr = paragraph && typeof paragraph.GetParaPr === "function" ? paragraph.GetParaPr() : null;
        var outlineLevel = paraPr && typeof paraPr.GetOutlineLvl === "function"
          ? paraPr.GetOutlineLvl()
          : undefined;
        if (typeof outlineLevel === "number" && outlineLevel >= 0) {
          return outlineLevel + 1;
        }
        return null;
      }

      /**
       * 从样式名称中解析标题级别。
       * ONLYOFFICE 的标题样式既可能是英文 "Heading 1"，也可能是中文 "标题 1"，
       * 这里统一兼容两种写法，尽量贴近左侧官方目录树的层级判断。
       */
      function getStyleLevel(styleName) {
        const normalizedStyleName = String(styleName || "").replace(/\u00a0/g, " ").trim();
        const matched = /^(?:Heading|标题)\s*(\d+)\b/i.exec(normalizedStyleName);
        if (matched) {
          return Number(matched[1]);
        }
        return null;
      }

      /**
       * 综合判定段落的标题级别。
       * 优先级：大纲级别 > 样式名称 > 回退值 > 默认 1 级。
       * 大纲级别优先是为了与 ONLYOFFICE 原生导航面板行为一致。
       */
      function getLevel(paragraph, styleName, fallbackLevel) {
        return getOutlineLevel(paragraph) || getStyleLevel(styleName) || fallbackLevel || 1;
      }

      // --- 主流程：先收集官方认定的 heading，再遍历全文兜底补齐 ---
      const doc = Api.GetDocument();
      // 获取文档中所有段落和所有标题段落
      const allParagraphs = typeof doc.GetAllParagraphs === "function" ? doc.GetAllParagraphs() : [];
      const headingParagraphs = typeof doc.GetAllHeadingParagraphs === "function" ? doc.GetAllHeadingParagraphs() : [];
      // 用 internalId 索引标题元数据，后续遍历全文时可快速判断段落是否为官方标题
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

      // 遍历全文所有段落，筛选出标题并检测编号状态
      var hasAnyNumbering = false;
      var headings = [];
      for (var index = 0; index < allParagraphs.length; index += 1) {
        var paragraph = allParagraphs[index];
        if (!paragraph) {
          continue;
        }

        var styleName = getStyleName(paragraph);
        var internalId = typeof paragraph.GetInternalId === "function" ? paragraph.GetInternalId() : "";
        var headingMeta = internalId ? headingMetaById[internalId] : null;
        var styleLevel = getStyleLevel(styleName);
        var outlineLevel = getOutlineLevel(paragraph);
        var isHeading = Boolean(headingMeta) || styleLevel !== null || outlineLevel !== null;
        if (!isHeading) {
          continue;
        }

        // 检测段落是否带有自动编号（如 "1."、"2.1" 等列表编号）。
        // GetNumbering() 返回 ApiNumberingLevel 对象表示有编号，返回 null 表示无编号。
        var numbered = false;
        if (typeof paragraph.GetNumbering === "function") {
          var numLevel = paragraph.GetNumbering();
          if (numLevel !== null && numLevel !== undefined) {
            numbered = true;
            hasAnyNumbering = true;
          }
        }

        headings.push({
          // 宿主页用 paragraphIndex 作为当前阶段最稳定的跳转锚点
          id: "heading-" + index,
          text: getParagraphText(paragraph),
          level: getLevel(paragraph, styleName, headingMeta ? headingMeta.level : null),
          styleName: styleName || (headingMeta ? headingMeta.styleName : "") || "",
          paragraphIndex: index,
          numbered: numbered
        });
      }

      // 当文档中存在带编号的标题时，按层级计算编号前缀。
      // 例如：H1 依次为 1, 2, 3；H1=2 下的 H2 依次为 2.1, 2.2；以此递推。
      // 没有 list 编号的标题若文本以数字开头（如 "1 接口开关"），仍同步更新 counter，
      // 避免其子级 list-numbered 标题算出错误的父级编号（如 "0.1" 而非 "1.1"）。
      if (hasAnyNumbering) {
        // counters[i] 记录第 i+1 级标题的当前计数
        var counters = [];
        // 提取文本开头的数字前缀，例如 "1 接口开关" → "1"，"2.3 节" → "2.3"
        var textNumRe = /^(\d+(?:\.\d+)*)\s/;
        for (var h = 0; h < headings.length; h += 1) {
          var heading = headings[h];
          var lvl = heading.level;
          // 按需扩展 counters 数组长度
          while (counters.length < lvl) {
            counters.push(0);
          }
          // 重置所有更深层级的计数器（进入新的父级时子级归零）
          for (var r = lvl; r < counters.length; r += 1) {
            counters[r] = 0;
          }
          if (heading.numbered) {
            // 当前层级计数 +1
            counters[lvl - 1] += 1;
            // 拼接从第 1 级到当前级的编号，如 "2.3.1"
            var parts = [];
            for (var p = 0; p < lvl; p += 1) {
              parts.push(counters[p] || 0);
            }
            heading.numberingPrefix = parts.join(".");
          } else {
            // 非 list-numbered 标题：若文本以数字前缀开头，同步覆盖 counter 状态
            // 使后续子级的自动编号能衔接正确的父级序号
            var textMatch = textNumRe.exec(heading.text || "");
            if (textMatch) {
              var numParts = textMatch[1].split(".").map(Number);
              for (var i = 0; i < numParts.length && i < lvl; i += 1) {
                counters[i] = numParts[i];
              }
            }
            heading.numberingPrefix = "";
          }
        }
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

  /**
   * 跳转到指定标题位置。
   * Asc.scope 用来把宿主页传入的数据带到 callCommand 执行环境内部。
   */
  function jumpToHeading(requestId, payload) {
    window.Asc.scope.targetParagraphIndex = Number(payload && payload.paragraphIndex);
    window.Asc.scope.targetHeadingId = payload && payload.id ? String(payload.id) : "";

    window.Asc.plugin.callCommand(function () {
      const doc = Api.GetDocument();
      const allParagraphs = typeof doc.GetAllParagraphs === "function" ? doc.GetAllParagraphs() : [];
      const targetIndex = Number(Asc.scope.targetParagraphIndex);

      // 校验索引有效性，避免文档编辑后段落位置变化导致越界
      if (!Number.isInteger(targetIndex) || targetIndex < 0 || targetIndex >= allParagraphs.length) {
        return {
          ok: false,
          message: "目标标题位置已失效，请先刷新章节目录。"
        };
      }

      // 将目标段落滚动到可视区域顶部的技巧：
      // 先选中末尾段落（视口滚到底部），再选中目标段落（视口滚回目标位置）。
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

  /**
   * 根据页码和文本定位并临时选中匹配内容。
   * 当前 ONLYOFFICE DOCX API 没有公开页内搜索范围，因此 GoToPage 只作为视口定位辅助；
   * 真实命中仍来自全文 Search，调用方可通过 pageScoped=false 向用户解释这个限制。
   */
  function locateText(requestId, payload) {
    const pageIndex = Number(payload && payload.pageIndex);
    const occurrence = Number(payload && payload.occurrence);
    const text = payload && typeof payload.text === "string" ? payload.text : "";

    window.Asc.scope.locateTextPageIndex = Number.isInteger(pageIndex) ? pageIndex : -1;
    window.Asc.scope.locateTextOccurrence = Number.isInteger(occurrence) && occurrence >= 0 ? occurrence : 0;
    window.Asc.scope.locateTextMatchCase = Boolean(payload && payload.matchCase);
    window.Asc.scope.locateTextText = text;

    window.Asc.plugin.callCommand(function () {
      const doc = Api.GetDocument();
      const targetText = String(Asc.scope.locateTextText || "");
      const targetPageIndex = Number(Asc.scope.locateTextPageIndex);
      const targetOccurrence = Number(Asc.scope.locateTextOccurrence);
      const matchCase = Boolean(Asc.scope.locateTextMatchCase);

      if (!targetText.trim()) {
        return {
          ok: false,
          message: "请输入需要定位的文本。"
        };
      }

      if (Number.isInteger(targetPageIndex) && targetPageIndex >= 0 && typeof doc.GoToPage === "function") {
        doc.GoToPage(targetPageIndex);
      }

      if (typeof doc.Search !== "function") {
        return {
          ok: false,
          message: "当前文档类型或 ONLYOFFICE 版本不支持文本搜索。"
        };
      }

      const ranges = doc.Search(targetText, matchCase) || [];
      if (!Array.isArray(ranges) || ranges.length === 0) {
        return {
          ok: false,
          message: "未在文档中找到指定文本。"
        };
      }

      if (targetOccurrence >= ranges.length) {
        return {
          ok: false,
          message: "指定文本存在，但 occurrence 超出命中数量。"
        };
      }

      const range = ranges[targetOccurrence];
      if (!range || typeof range.Select !== "function") {
        return {
          ok: false,
          message: "已找到文本，但当前 ONLYOFFICE 版本无法选中该范围。"
        };
      }

      const selected = range.Select();
      return {
        ok: selected === true || selected === undefined,
        pageIndex: targetPageIndex,
        occurrence: targetOccurrence,
        totalMatches: ranges.length,
        pageScoped: false,
        message: selected === false ? "已找到文本，但选中操作未成功。" : ""
      };
    }, false, false, function (result) {
      if (!result || result.ok !== true) {
        postError(result?.message || "定位并选中文本失败。", requestId);
        return;
      }

      postMessage(
        EVENTS.textLocated,
        {
          pageIndex: result.pageIndex,
          occurrence: result.occurrence,
          totalMatches: result.totalMatches,
          pageScoped: result.pageScoped
        },
        requestId
      );
    });
  }

  /**
   * 将 HTML 字符串粘贴到文档当前光标位置。
   * ONLYOFFICE PasteHtml 语义：有选区时替换选区，无选区时在光标处插入。
   *
   * 已知 API 限制（ONLYOFFICE PasteHtml void callback）：
   *   PasteHtml callback 不携带任何参数，无法区分成功/失败。
   *   此处将 callback 触发视为 best-effort 成功（false-positive 风险低于永久挂起）。
   *   同步异常（如插件未初始化）通过 try/catch 捕获并回传 error 事件。
   *   异步失败（如 ONLYOFFICE 内部错误）只能由宿主页的 requestTimeoutMs 超时检测。
   */
  function insertHtmlAtCursor(requestId, payload) {
    const html = payload && typeof payload.html === "string" ? payload.html : "";
    try {
      window.Asc.plugin.executeMethod(
        "PasteHtml",
        [html],
        function () {
          // PasteHtml 是 void - callback 无错误参数，视为 best-effort 成功。
          postMessage(EVENTS.htmlInserted, { success: true }, requestId);
        }
      );
    } catch (err) {
      // executeMethod 同步抛出时（例如插件未初始化），回传 error 事件。
      postError(err && err.message ? err.message : "insertHtml failed", requestId);
    }
  }

  /**
   * 处理宿主页发来的 postMessage 请求。
   * 插件只暴露非常克制的几类命令：抓选区、刷目录、跳标题、定位文本、写入 HTML。
   * 真正的 AI 调用仍放在宿主页或后端，不放进插件里。
   */
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
        case EVENTS.locateText:
          locateText(message.requestId, message.payload || {});
          break;
        case EVENTS.insertHtml:
          insertHtmlAtCursor(message.requestId, message.payload || {});
          break;
        default:
          break;
      }
    } catch (error) {
      postError(error instanceof Error ? error.message : "插件桥接执行失败。", message.requestId);
    }
  }

  // 保证 message 监听只绑定一次，避免插件重复 init 时积累多个监听器。
  let hostMessageBound = false;

  /**
   * 发送 ready 事件通知宿主页插件已就绪。
   * 这是宿主页 waitForReady 的唯一完成信号。
   */
  function notifyReady() {
    postMessage(EVENTS.ready, {});
  }

  /**
   * 注册插件运行时回调。
   * 当 window.Asc.plugin 可用时挂载 init 和 button 回调。
   * @returns {boolean} 是否注册成功
   */
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

    // ONLYOFFICE 要求插件必须声明 button 回调，即使不使用也需保留空实现。
    window.Asc.plugin.button = function () {};
    return true;
  }

  /**
   * ONLYOFFICE 注入插件运行时存在异步延迟，轮询直到 Asc.plugin 可用再注册。
   */
  function waitForPluginRuntime() {
    if (registerPluginRuntime()) {
      return;
    }

    window.setTimeout(waitForPluginRuntime, 50);
  }

  waitForPluginRuntime();
})();
