package com.earmo.onlyoffice.integration.model.llm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话详情中的单条 AI 对话消息。
 */
@Schema(description = "会话详情中的单条 AI 对话消息。")
public record LlmMessageResponse(
    @Schema(description = "消息主键。", example = "message-1")
    String messageId,
    @Schema(description = "消息角色，例如 user 或 assistant。", example = "assistant")
    String role,
    @Schema(description = "用户提问正文；仅 user 消息通常有值。")
    String question,
    @Schema(description = "当前 active variant 对应的 assistant 正文。")
    String assistantText,
    @Schema(description = "发送消息时携带的选区快照。")
    String snapshotText,
    @Schema(description = "发送消息时是否为空选区。", example = "false")
    boolean snapshotEmptySelection,
    @Schema(description = "发送消息时关联的标题 ID。", example = "heading-1")
    String headingId,
    @Schema(description = "发送消息时关联的标题文本。", example = "付款条款")
    String headingText,
    @Schema(description = "本次请求是否把标题上下文纳入 prompt。", example = "true")
    boolean includeHeading,
    @Schema(description = "消息状态。", example = "completed")
    String status,
    @Schema(description = "失败时的稳定错误码。", example = "LLM_PROVIDER_ERROR")
    String errorCode,
    @Schema(description = "模型返回的结束原因。", example = "stop")
    String finishReason,
    @Schema(description = "active variant 对应的 token 用量。")
    LlmUsageResponse usage,
    @Schema(description = "active variant 对应的 provider 响应元数据。")
    Map<String, Object> providerResponseMeta,
    @Schema(description = "assistant 消息下的全部回复版本。")
    List<LlmMessageVariantResponse> variants,
    @Schema(description = "当前正在展示和写回的 variant 序号。", example = "0")
    Integer activeVariantIndex,
    @Schema(description = "消息创建时间。")
    Instant createdTime
) {

  /**
   * 向后兼容旧调用点的构造器。
   *
   * <p>Phase 17 引入 variants 后，部分 controller/service 测试仍按旧字段构造消息。
   * 该构造器保留旧签名，并把 variants 初始化为空列表。
   */
  public LlmMessageResponse(
      String messageId,
      String role,
      String question,
      String assistantText,
      String snapshotText,
      boolean snapshotEmptySelection,
      String headingId,
      String headingText,
      boolean includeHeading,
      String status,
      String errorCode,
      String finishReason,
      LlmUsageResponse usage,
      Map<String, Object> providerResponseMeta,
      Instant createdTime
  ) {
    this(
        messageId,
        role,
        question,
        assistantText,
        snapshotText,
        snapshotEmptySelection,
        headingId,
        headingText,
        includeHeading,
        status,
        errorCode,
        finishReason,
        usage,
        providerResponseMeta,
        List.of(),
        null,
        createdTime
    );
  }
}
