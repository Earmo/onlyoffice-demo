package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Prompt 窗口构建器。
 *
 * <p>负责把系统提示词、历史消息、当前问题、文档选区和标题上下文拼成最终发送给模型的消息列表，
 * 并在有限预算内优先保留最近的历史内容。
 */
@Component
public class LlmPromptWindowBuilder {

  /**
   * 构建发送给上游模型的消息窗口。
   *
   * <p>处理步骤：
   * 1. 先组装当前用户问题对应的 prompt；
   * 2. 计算系统提示词和当前问题已经占用的 token 预算；
   * 3. 倒序挑选最近历史消息，直到预算耗尽；
   * 4. 最终按 `system -> history -> current user` 的顺序返回。
   *
   * @param properties LLM 配置。
   * @param history 当前会话历史消息。
   * @param question 当前用户问题。
   * @param snapshotText 当前选区快照文本。
   * @param emptySelection 当前选区是否为空。
   * @param includeHeading 是否纳入标题上下文。
   * @param headingText 当前标题文本。
   * @return 发送给上游 provider 的消息窗口。
   */
  public List<LlmProviderMessage> buildMessages(
      LlmProperties properties,
      List<DocumentLlmMessageEntity> history,
      String question,
      String snapshotText,
      boolean emptySelection,
      boolean includeHeading,
      String headingText
  ) {
    return buildMessages(properties, history, Map.of(), question, snapshotText, emptySelection, includeHeading, headingText);
  }

  /**
   * 构建发送给上游模型的消息窗口，并允许指定 assistant 消息的 active variant 文本。
   *
   * @param properties LLM 配置。
   * @param history 当前会话历史消息。
   * @param activeAssistantTextByMessageId assistant message ID 到 active variant 文本的映射。
   * @param question 当前用户问题。
   * @param snapshotText 当前选区快照文本。
   * @param emptySelection 当前选区是否为空。
   * @param includeHeading 是否纳入标题上下文。
   * @param headingText 当前标题文本。
   * @return 发送给上游 provider 的消息窗口。
   */
  public List<LlmProviderMessage> buildMessages(
      LlmProperties properties,
      List<DocumentLlmMessageEntity> history,
      Map<String, String> activeAssistantTextByMessageId,
      String question,
      String snapshotText,
      boolean emptySelection,
      boolean includeHeading,
      String headingText
  ) {
    String currentUserPrompt = buildCurrentUserPrompt(question, snapshotText, emptySelection, includeHeading, headingText);
    // 先扣掉系统提示词和当前问题的固定成本，历史消息只能消费剩余预算。
    int baseTokens = estimateTokens(properties.getDefaultSystemPrompt()) + estimateTokens(currentUserPrompt);
    int remainingBudget = Math.max(properties.getHistoryBudgetTokens() - baseTokens, 0);

    List<LlmProviderMessage> historicalMessages = new ArrayList<>();
    List<DocumentLlmMessageEntity> ordered = new ArrayList<>(history);
    // 从最近消息开始回填预算，确保被截断时优先保留最新上下文。
    Collections.reverse(ordered);
    for (DocumentLlmMessageEntity entity : ordered) {
      String content = "assistant".equals(entity.getRole())
          ? activeAssistantTextByMessageId.getOrDefault(entity.getMessageId(), entity.getAssistantText())
          : entity.getMessageText();
      int estimated = estimateTokens(content);
      // chars_div_4: estimatedTokens = ceil(charCount / 4.0)，按最新消息优先、倒序累积、超预算即停止。
      if (estimated > remainingBudget) {
        break;
      }
      historicalMessages.add(new LlmProviderMessage(entity.getRole(), content == null ? "" : content));
      remainingBudget -= estimated;
    }
    Collections.reverse(historicalMessages);

    List<LlmProviderMessage> prompt = new ArrayList<>();
    prompt.add(new LlmProviderMessage("system", properties.getDefaultSystemPrompt()));
    prompt.addAll(historicalMessages);
    prompt.add(new LlmProviderMessage("user", currentUserPrompt));
    return prompt;
  }

  /**
   * 用一个稳定且廉价的启发式估算 token 数。
   *
   * <p>当前实现按字符数除以 4 向上取整，用于预算裁剪，不追求和 provider 完全一致。
   *
   * @param value 待估算的文本。
   * @return 估算 token 数。
   */
  public int estimateTokens(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return (int) Math.ceil(value.length() / 4.0d);
  }

  /**
   * 把当前问题、选区和标题上下文拼成一段最终用户提示词。
   *
   * @param question 当前用户问题。
   * @param snapshotText 当前选区快照文本。
   * @param emptySelection 当前选区是否为空。
   * @param includeHeading 是否纳入标题上下文。
   * @param headingText 当前标题文本。
   * @return 最终发送给模型的用户提示词。
   */
  private String buildCurrentUserPrompt(
      String question,
      String snapshotText,
      boolean emptySelection,
      boolean includeHeading,
      String headingText
  ) {
    StringBuilder builder = new StringBuilder();
    builder.append("问题：\n").append(question).append("\n\n");
    builder.append("当前选区：\n");
    if (emptySelection) {
      builder.append("[空选区]\n\n");
    } else {
      builder.append(snapshotText == null ? "" : snapshotText).append("\n\n");
    }
    if (includeHeading && headingText != null && !headingText.isBlank()) {
      builder.append("当前标题上下文：\n").append(headingText.trim()).append("\n");
    }
    return builder.toString().trim();
  }
}
