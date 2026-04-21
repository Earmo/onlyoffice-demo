package com.earmo.onlyoffice.integration.model.llm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendLlmMessageRequest(
    @NotBlank
    @Size(max = 256)
    String documentId,
    @NotBlank
    @Size(max = 256)
    String sessionId,
    @NotBlank
    @Size(max = 4000, message = "问题长度不能超过 4000 字符")
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
      @Size(max = 32000, message = "选区快照不能超过 32000 字符")
      String text,
      boolean emptySelection
  ) {
  }

  public record HeadingContext(
      boolean includeHeading,
      @Size(max = 256)
      String headingId,
      @Size(max = 512)
      String headingText
  ) {
  }
}
