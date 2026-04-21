package com.earmo.onlyoffice.integration.model.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendLlmMessageRequest(
    @NotBlank
    String documentId,
    @NotBlank
    String sessionId,
    @NotBlank
    String question,
    @Valid
    @NotNull
    SelectionSnapshot selectionSnapshot,
    @Valid
    @NotNull
    HeadingContext headingContext,
    boolean retryConfirmed
) {

  public record SelectionSnapshot(
      @NotNull
      String text,
      boolean emptySelection
  ) {
  }

  public record HeadingContext(
      boolean includeHeading,
      String headingId,
      String headingText
  ) {
  }
}
