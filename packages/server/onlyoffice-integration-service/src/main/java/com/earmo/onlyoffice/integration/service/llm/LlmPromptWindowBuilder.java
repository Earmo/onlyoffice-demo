package com.earmo.onlyoffice.integration.service.llm;

import com.earmo.onlyoffice.integration.config.LlmProperties;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LlmPromptWindowBuilder {

  public List<LlmProviderMessage> buildMessages(
      LlmProperties properties,
      List<DocumentLlmMessageEntity> history,
      String question,
      String snapshotText,
      boolean emptySelection,
      boolean includeHeading,
      String headingText
  ) {
    String currentUserPrompt = buildCurrentUserPrompt(question, snapshotText, emptySelection, includeHeading, headingText);
    int baseTokens = estimateTokens(properties.getDefaultSystemPrompt()) + estimateTokens(currentUserPrompt);
    int remainingBudget = Math.max(properties.getHistoryBudgetTokens() - baseTokens, 0);

    List<LlmProviderMessage> historicalMessages = new ArrayList<>();
    List<DocumentLlmMessageEntity> ordered = new ArrayList<>(history);
    Collections.reverse(ordered);
    for (DocumentLlmMessageEntity entity : ordered) {
      String content = "assistant".equals(entity.getRole()) ? entity.getAssistantText() : entity.getMessageText();
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

  public int estimateTokens(String value) {
    if (value == null || value.isBlank()) {
      return 0;
    }
    return (int) Math.ceil(value.length() / 4.0d);
  }

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
