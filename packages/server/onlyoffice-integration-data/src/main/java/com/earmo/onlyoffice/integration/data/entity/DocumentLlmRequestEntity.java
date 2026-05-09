package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次 AI 请求执行状态实体。
 *
 * <p>该表把 user message、assistant message、assistant variant 与 provider 请求状态串起来，
 * 用于轮询查询、取消请求、流式终态补偿和问题排查。
 */
@Table("document_llm_request")
@Getter
@Setter
public class DocumentLlmRequestEntity {

  /** AI 请求主键。 */
  @Id
  @Column("request_id")
  private String requestId;

  /** 所属 AI 会话主键。 */
  @Column("session_id")
  private String sessionId;

  /** 关联的内部文档主键。 */
  @Column("document_id")
  private String documentId;

  /** 请求所属租户。 */
  @Column("tenant_id")
  private String tenantId;

  /** 请求所属组织标识。 */
  @Column("org_id")
  private String orgId;

  /** 请求所属组织名称。 */
  @Column("org_name")
  private String orgName;

  /** 请求所属用户标识。 */
  @Column("actor_user")
  private String actorUser;

  /** 关联的用户消息主键。 */
  @Column("user_message_id")
  private String userMessageId;

  /** 关联的 assistant 消息主键。 */
  @Column("assistant_message_id")
  private String assistantMessageId;

  /** 本次请求生成的 assistant variant 主键。 */
  @Column("variant_id")
  private String variantId;

  /** 本次请求生成的 assistant variant 序号。 */
  @Column("variant_index")
  private Integer variantIndex;

  /** 上游 provider 的 request id。 */
  @Column("provider_request_id")
  private String providerRequestId;

  /** 请求状态，例如 `in_progress`、`completed`、`failed`、`cancelled`。 */
  @Column("status")
  private String status;

  /** 是否已发起取消请求。 */
  @Column("cancel_requested")
  private boolean cancelRequested;

  /** 取消来源，例如 `user`。 */
  @Column("cancel_source")
  private String cancelSource;

  /** 请求开始时间。 */
  @Column("started_time")
  private Instant startedTime;

  /** 请求结束时间。 */
  @Column("finished_time")
  private Instant finishedTime;
}
