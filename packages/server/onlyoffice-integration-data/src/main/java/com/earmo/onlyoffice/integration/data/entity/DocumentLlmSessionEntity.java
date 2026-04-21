package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Table("document_llm_session")
@Getter
@Setter
public class DocumentLlmSessionEntity {

  @Id
  @Column("session_id")
  private String sessionId;

  @Column("document_id")
  private String documentId;

  @Column("tenant_id")
  private String tenantId;

  @Column("actor_user")
  private String actorUser;

  @Column("title")
  private String title;

  @Column("last_snapshot_text")
  private String lastSnapshotText;

  @Column("last_snapshot_is_empty")
  private boolean lastSnapshotIsEmpty;

  @Column("last_heading_id")
  private String lastHeadingId;

  @Column("last_heading_text")
  private String lastHeadingText;

  @Column("created_time")
  private Instant createdTime;

  @Column("updated_time")
  private Instant updatedTime;

  @Column("archived_time")
  private Instant archivedTime;
}
