package com.earmo.onlyoffice.integration.model.llm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record LlmMessageResponse(
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
    List<LlmMessageVariantResponse> variants,
    Integer activeVariantIndex,
    Instant createdTime
) {

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
