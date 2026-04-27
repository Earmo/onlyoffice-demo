package com.earmo.onlyoffice.integration.model.llm;

import java.time.Instant;
import java.util.List;

public record LlmSessionDetailResponse(
    String sessionId,
    String documentId,
    String title,
    String lastSnapshotText,
    boolean lastSnapshotIsEmpty,
    String lastHeadingId,
    String lastHeadingText,
    Instant lastConversationTime,
    Instant createdTime,
    Instant updatedTime,
    List<LlmMessageResponse> messages
) {
}
