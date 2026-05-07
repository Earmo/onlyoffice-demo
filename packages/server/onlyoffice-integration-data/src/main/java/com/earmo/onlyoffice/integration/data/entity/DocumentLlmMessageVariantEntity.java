package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * assistant 消息多版本回复持久化实体。
 *
 * <p>同一 assistant message 下可以有多个 variant，用于支持类似 ChatGPT 的重新生成与版本切换。
 */
@Table("document_llm_message_variant")
@Getter
@Setter
public class DocumentLlmMessageVariantEntity {

  /** variant 主键。 */
  @Id
  @Column("variant_id")
  private String variantId;

  /** 所属 assistant message 主键。 */
  @Column("message_id")
  private String messageId;

  /** 所属 AI 会话主键。 */
  @Column("session_id")
  private String sessionId;

  /** 关联的内部文档主键。 */
  @Column("document_id")
  private String documentId;

  /** variant 所属租户。 */
  @Column("tenant_id")
  private String tenantId;

  /** variant 所属组织标识。 */
  @Column("org_id")
  private String orgId;

  /** variant 所属组织名称。 */
  @Column("org_name")
  private String orgName;

  /** variant 所属用户标识。 */
  @Column("actor_user")
  private String actorUser;

  /** 同一 assistant message 下的版本序号。 */
  @Column("variant_index")
  private Integer variantIndex;

  /** assistant 返回正文。 */
  @Column("assistant_text")
  private String assistantText;

  /** variant 状态，例如 `pending`、`completed`、`failed`、`cancelled`。 */
  @Column("status")
  private String status;

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

  /** variant 创建时间。 */
  @Column("created_time")
  private Instant createdTime;

  /** variant 更新时间。 */
  @Column("updated_time")
  private Instant updatedTime;
}
