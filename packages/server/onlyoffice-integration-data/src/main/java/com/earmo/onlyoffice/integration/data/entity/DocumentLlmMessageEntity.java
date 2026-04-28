package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 会话消息持久化实体。
 *
 * <p>user 消息保存用户问题和发送时上下文；assistant 消息作为稳定轮次容器，
 * 当前展示内容由 activeVariantIndex 指向 document_llm_message_variant 中的具体版本。
 */
@Table("document_llm_message")
@Getter
@Setter
public class DocumentLlmMessageEntity {

  /** 消息主键。 */
  @Id
  @Column("message_id")
  private String messageId;

  /** 所属 AI 会话主键。 */
  @Column("session_id")
  private String sessionId;

  /** 关联的内部文档主键。 */
  @Column("document_id")
  private String documentId;

  /** 消息所属租户。 */
  @Column("tenant_id")
  private String tenantId;

  /** 消息所属用户标识。 */
  @Column("actor_user")
  private String actorUser;

  /** 消息角色，例如 `user` 或 `assistant`。 */
  @Column("role")
  private String role;

  /** 用户消息正文。 */
  @Column("message_text")
  private String messageText;

  /** assistant 返回正文。 */
  @Column("assistant_text")
  private String assistantText;

  /** 发送时携带的选区快照文本。 */
  @Column("snapshot_text")
  private String snapshotText;

  /** 发送时是否为空选区。 */
  @Column("snapshot_is_empty")
  private boolean snapshotIsEmpty;

  /** 发送时携带的标题 ID。 */
  @Column("heading_id")
  private String headingId;

  /** 发送时携带的标题文本。 */
  @Column("heading_text")
  private String headingText;

  /** 是否将标题上下文纳入 prompt。 */
  @Column("include_heading")
  private boolean includeHeading;

  /** 消息状态，例如 `pending`、`completed`、`failed`、`cancelled`。 */
  @Column("status")
  private String status;

  /** 若为重试消息，指向被重试的消息主键。 */
  @Column("retry_of_message_id")
  private String retryOfMessageId;

  /** 当前 assistant message 展示和写回使用的 variantIndex。 */
  @Column("active_variant_index")
  private Integer activeVariantIndex;

  /** 模型 usage 元数据 JSON。 */
  @Column("provider_usage_json")
  private String providerUsageJson;

  /** 白名单过滤后的 provider 响应元数据 JSON。 */
  @Column("provider_meta_json")
  private String providerMetaJson;

  /** 模型返回的 finish reason。 */
  @Column("finish_reason")
  private String finishReason;

  /** 稳定的 machine-readable 错误码。 */
  @Column("error_code")
  private String errorCode;

  /** 消息创建时间。 */
  @Column("created_time")
  private Instant createdTime;
}
