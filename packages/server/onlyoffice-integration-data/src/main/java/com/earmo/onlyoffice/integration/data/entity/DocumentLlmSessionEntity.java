package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 对话会话持久化实体。
 *
 * <p>一条记录代表某个用户在某份文档下的一条对话线程，
 * 会话级字段保存最近一次上下文快照，消息明细保存在 document_llm_message 表。
 */
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

  /** 会话所属组织标识。 */
  @Column("org_id")
  private String orgId;

  /** 会话所属组织名称。 */
  @Column("org_name")
  private String orgName;

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

  /** 会话最近一次用户发起对话的时间。 */
  @Column("last_conversation_time")
  private Instant lastConversationTime;

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
