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

  /** AI 会话主键。 */
  @Id
  @Column("session_id")
  private String sessionId;

  /** 关联的内部文档主键。 */
  @Column("document_id")
  private String documentId;

  /** 会话所属租户。 */
  @Column("tenant_id")
  private String tenantId;

  /** 会话所属用户标识。 */
  @Column("actor_user")
  private String actorUser;

  /** 会话标题。 */
  @Column("title")
  private String title;

  /** 最近一次发送时的选区快照文本。 */
  @Column("last_snapshot_text")
  private String lastSnapshotText;

  /** 最近一次发送是否为空选区。 */
  @Column("last_snapshot_is_empty")
  private boolean lastSnapshotIsEmpty;

  /** 最近一次发送时使用的标题 ID。 */
  @Column("last_heading_id")
  private String lastHeadingId;

  /** 最近一次发送时使用的标题文本。 */
  @Column("last_heading_text")
  private String lastHeadingText;

  /** 会话创建时间。 */
  @Column("created_time")
  private Instant createdTime;

  /** 会话最近更新时间。 */
  @Column("updated_time")
  private Instant updatedTime;

  /** 会话归档时间。 */
  @Column("archived_time")
  private Instant archivedTime;
}
