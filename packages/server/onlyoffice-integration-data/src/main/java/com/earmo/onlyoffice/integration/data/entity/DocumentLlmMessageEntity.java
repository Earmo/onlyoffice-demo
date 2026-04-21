package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Table("document_llm_message")
@Getter
@Setter
public class DocumentLlmMessageEntity {

  @Id
  @Column("message_id")
  private String messageId;

  @Column("session_id")
  private String sessionId;

  @Column("document_id")
  private String documentId;

  @Column("tenant_id")
  private String tenantId;

  @Column("actor_user")
  private String actorUser;

  @Column("role")
  private String role;

  @Column("message_text")
  private String messageText;

  @Column("assistant_text")
  private String assistantText;

  @Column("snapshot_text")
  private String snapshotText;

  @Column("snapshot_is_empty")
  private boolean snapshotIsEmpty;

  @Column("heading_id")
  private String headingId;

  @Column("heading_text")
  private String headingText;

  @Column("include_heading")
  private boolean includeHeading;

  @Column("status")
  private String status;

  @Column("retry_of_message_id")
  private String retryOfMessageId;

  @Column("provider_usage_json")
  private String providerUsageJson;

  @Column("provider_meta_json")
  private String providerMetaJson;

  @Column("finish_reason")
  private String finishReason;

  @Column("error_code")
  private String errorCode;

  @Column("created_time")
  private Instant createdTime;
}
