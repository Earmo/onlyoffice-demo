package com.earmo.onlyoffice.integration.model.llm;

import java.time.Instant;

public record LlmSessionSummaryResponse(
    String sessionId,
    String documentId,
    String title,
    String lastSnapshotText,
    boolean lastSnapshotIsEmpty,
    String lastHeadingId,
    String lastHeadingText,
    Instant createdTime,
    Instant updatedTime
) {
}
