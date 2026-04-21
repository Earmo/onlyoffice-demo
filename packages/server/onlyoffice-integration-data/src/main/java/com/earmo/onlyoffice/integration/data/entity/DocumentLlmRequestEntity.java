package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Table("document_llm_request")
@Getter
@Setter
public class DocumentLlmRequestEntity {

  @Id
  @Column("request_id")
  private String requestId;

  @Column("session_id")
  private String sessionId;

  @Column("document_id")
  private String documentId;

  @Column("tenant_id")
  private String tenantId;

  @Column("actor_user")
  private String actorUser;

  @Column("user_message_id")
  private String userMessageId;

  @Column("assistant_message_id")
  private String assistantMessageId;

  @Column("provider_request_id")
  private String providerRequestId;

  @Column("status")
  private String status;

  @Column("cancel_requested")
  private boolean cancelRequested;

  @Column("cancel_source")
  private String cancelSource;

  @Column("started_time")
  private Instant startedTime;

  @Column("finished_time")
  private Instant finishedTime;
}
