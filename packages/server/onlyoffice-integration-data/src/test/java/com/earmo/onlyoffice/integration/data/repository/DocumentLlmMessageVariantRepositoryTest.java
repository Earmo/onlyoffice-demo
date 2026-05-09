package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.DataModuleTestApplication;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageEntity;
import com.earmo.onlyoffice.integration.data.entity.DocumentLlmMessageVariantEntity;
import com.earmo.onlyoffice.integration.data.mapper.DocumentLlmMessageMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataModuleTestApplication.class)
class DocumentLlmMessageVariantRepositoryTest {

  @Autowired
  private DocumentLlmMessageMapper documentLlmMessageMapper;

  @Autowired
  private DocumentLlmMessageRepository documentLlmMessageRepository;

  @Autowired
  private DocumentLlmMessageVariantRepository documentLlmMessageVariantRepository;

  @Test
  void shouldReadVariantsOnlyInsideDocumentTenantAndActorScope() {
    DocumentLlmMessageEntity message = assistantMessage("assistant-scope-1", "doc-scope-1", "tenant-scope-1", "user-scope-1");
    documentLlmMessageMapper.insert(message);

    DocumentLlmMessageVariantEntity variant = variant("variant-scope-1", message, 0, "已完成版本");
    documentLlmMessageVariantRepository.insert(variant);

    Optional<DocumentLlmMessageVariantEntity> sameScope = documentLlmMessageVariantRepository.findVariantByMessageScope(
        "assistant-scope-1",
        "variant-scope-1",
        "doc-scope-1",
        "tenant-scope-1",
        "user-scope-1"
    );
    Optional<DocumentLlmMessageVariantEntity> otherActor = documentLlmMessageVariantRepository.findVariantByMessageScope(
        "assistant-scope-1",
        "variant-scope-1",
        "doc-scope-1",
        "tenant-scope-1",
        "other-user"
    );

    assertThat(sameScope).isPresent();
    assertThat(sameScope.get().getAssistantText()).isEqualTo("已完成版本");
    assertThat(otherActor).isEmpty();
  }

  @Test
  void shouldListVariantsByMessageIdsInVariantIndexOrder() {
    DocumentLlmMessageEntity firstMessage = assistantMessage("assistant-list-1", "doc-list-1", "tenant-list-1", "user-list-1");
    DocumentLlmMessageEntity secondMessage = assistantMessage("assistant-list-2", "doc-list-1", "tenant-list-1", "user-list-1");
    documentLlmMessageMapper.insert(firstMessage);
    documentLlmMessageMapper.insert(secondMessage);
    documentLlmMessageVariantRepository.insert(variant("variant-list-2", firstMessage, 1, "第二版"));
    documentLlmMessageVariantRepository.insert(variant("variant-list-1", firstMessage, 0, "第一版"));
    documentLlmMessageVariantRepository.insert(variant("variant-list-3", secondMessage, 0, "另一轮"));

    List<DocumentLlmMessageVariantEntity> variants = documentLlmMessageVariantRepository.findByMessageIdsScope(
        List.of("assistant-list-1", "assistant-list-2"),
        "doc-list-1",
        "tenant-list-1",
        "user-list-1"
    );

    assertThat(variants)
        .extracting(DocumentLlmMessageVariantEntity::getVariantId)
        .containsExactly("variant-list-1", "variant-list-2", "variant-list-3");
  }

  @Test
  void shouldCreateNextVariantWithMessageActiveIndexUpdate() {
    DocumentLlmMessageEntity message = assistantMessage("assistant-next-1", "doc-next-1", "tenant-next-1", "user-next-1");
    documentLlmMessageMapper.insert(message);
    documentLlmMessageVariantRepository.insert(variant("variant-next-0", message, 0, "第一版"));

    DocumentLlmMessageVariantEntity created = documentLlmMessageVariantRepository.createNextVariantForMessageScope(
        "assistant-next-1",
        "doc-next-1",
        "tenant-next-1",
        "user-next-1",
        "pending",
        Instant.parse("2026-04-28T00:00:00Z")
    );
    documentLlmMessageRepository.updateActiveVariantIndex(
        "assistant-next-1",
        "doc-next-1",
        "tenant-next-1",
        "user-next-1",
        created.getVariantIndex()
    );

    assertThat(created.getVariantIndex()).isEqualTo(1);
    DocumentLlmMessageEntity reloaded = documentLlmMessageRepository.findMessageByScope(
        "assistant-next-1",
        "doc-next-1",
        "tenant-next-1",
        "user-next-1"
    ).orElseThrow();
    assertThat(reloaded.getActiveVariantIndex()).isEqualTo(1);
  }

  private DocumentLlmMessageEntity assistantMessage(
      String messageId,
      String documentId,
      String tenantId,
      String actorUser
  ) {
    DocumentLlmMessageEntity entity = new DocumentLlmMessageEntity();
    entity.setMessageId(messageId);
    entity.setSessionId("session-" + messageId);
    entity.setDocumentId(documentId);
    entity.setTenantId(tenantId);
    entity.setActorUser(actorUser);
    entity.setRole("assistant");
    entity.setStatus("completed");
    entity.setActiveVariantIndex(0);
    entity.setCreatedTime(Instant.parse("2026-04-28T00:00:00Z"));
    return entity;
  }

  private DocumentLlmMessageVariantEntity variant(
      String variantId,
      DocumentLlmMessageEntity message,
      int variantIndex,
      String assistantText
  ) {
    DocumentLlmMessageVariantEntity entity = new DocumentLlmMessageVariantEntity();
    entity.setVariantId(variantId);
    entity.setMessageId(message.getMessageId());
    entity.setSessionId(message.getSessionId());
    entity.setDocumentId(message.getDocumentId());
    entity.setTenantId(message.getTenantId());
    entity.setActorUser(message.getActorUser());
    entity.setVariantIndex(variantIndex);
    entity.setAssistantText(assistantText);
    entity.setStatus("completed");
    entity.setCreatedTime(Instant.parse("2026-04-28T00:00:00Z"));
    entity.setUpdatedTime(Instant.parse("2026-04-28T00:00:00Z"));
    return entity;
  }
}
