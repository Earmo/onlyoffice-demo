package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发送一次 AI 对话消息的请求体。
 */
@Schema(description = "发送一次 AI 对话消息的请求体。")
public record SendLlmMessageRequest(
    @Schema(description = "会话所属内部文档 ID。", example = "sample")
    @NotBlank
    @Size(max = 256)
    String documentId,
    @Schema(description = "目标 AI 会话 ID。", example = "session-1")
    @NotBlank
    @Size(max = 256)
    String sessionId,
    @Schema(description = "可选 provider 覆盖值；为空时使用默认 provider。", example = "dashscope")
    @Size(max = 128)
    String provider,
    @Schema(description = "可选模型覆盖值；为空时使用 provider 默认模型。", example = "qwen-plus")
    @Size(max = 256)
    String model,
    @Schema(description = "用户输入的问题正文。", example = "请总结当前选区的核心风险")
    @NotBlank
    @Size(max = 4000, message = "问题长度不能超过 4000 字符")
    String question,
    @Schema(description = "发送时捕获的 ONLYOFFICE 选区快照。")
    @Valid
    @NotNull
    SelectionSnapshot selectionSnapshot,
    @Schema(description = "发送时捕获的章节标题上下文。")
    @Valid
    @NotNull
    HeadingContext headingContext,
    @Schema(description = "是否已确认在缺少选区或重试场景下继续发送。", example = "false")
    boolean retryConfirmed,
    @Schema(description = "需要重新生成的 assistant message ID；为空表示普通提问。", example = "message-2")
    @Size(max = 128)
    String regenerateAssistantMessageId
) {

  /**
   * 用户发送消息时的选区快照。
   */
  @Schema(description = "用户发送消息时的选区快照。")
  public record SelectionSnapshot(
      @Schema(description = "选区文本；允许为空字符串以表达空选区。")
      @NotNull
      @Size(max = 32000, message = "选区快照不能超过 32000 字符")
      String text,
      @Schema(description = "当前快照是否来自空选区。", example = "false")
      boolean emptySelection
  ) {
  }

  /**
   * 用户发送消息时的章节标题上下文。
   */
  @Schema(description = "用户发送消息时的章节标题上下文。")
  public record HeadingContext(
      @Schema(description = "是否把标题上下文纳入 prompt。", example = "true")
      boolean includeHeading,
      @Schema(description = "标题节点 ID。", example = "heading-1")
      @Size(max = 256)
      String headingId,
      @Schema(description = "标题展示文本。", example = "付款条款")
      @Size(max = 512)
      String headingText
  ) {
  }
}
