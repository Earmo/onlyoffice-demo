package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 轻量访问审计事件实体。
 *
 * <p>Phase 3 不直接引入完整审计中心，而是先记录关键访问路径发生了什么、由谁触发、结果如何。
 * 这样既能保留后续排障和接入对账所需的信息，又不会在当前阶段把表结构扩成过重的日志体系。
 */
@Schema(description = "轻量访问审计事件实体。")
@Table("access_audit_event")
@Getter
@Setter
public class AccessAuditEventEntity {

  /** 审计事件主键。 */
  @Schema(description = "审计事件主键。", example = "evt-1")
  @Id
  @Column("event_id")
  private String eventId;

  /** 关联的文档主键。 */
  @Schema(description = "关联的文档主键。", example = "doc-1")
  @Column("document_id")
  private String documentId;

  /** 关联租户标识。 */
  @Schema(description = "关联租户标识。", example = "tenant-a")
  @Column("tenant_id")
  private String tenantId;

  /** 来源系统标识。 */
  @Schema(description = "来源系统标识。", example = "native")
  @Column("source_system")
  private String sourceSystem;

  /** 操作者用户标识。 */
  @Schema(description = "操作者用户标识。", example = "user-a")
  @Column("actor_user")
  private String actorUser;

  /** 操作者展示名。 */
  @Schema(description = "操作者展示名。", example = "Alice")
  @Column("actor_name")
  private String actorName;

  /** 审计事件类型。 */
  @Schema(description = "事件类型。", example = "document_created")
  @Column("event_type")
  private String eventType;

  /** 审计事件发生时间。 */
  @Schema(description = "事件发生时间。")
  @Column("event_time")
  private Instant eventTime;

  /** 事件来源。 */
  @Schema(description = "事件来源。", example = "header")
  @Column("event_source")
  private String eventSource;

  /** 事件结果。 */
  @Schema(description = "事件结果。", example = "success")
  @Column("event_result")
  private String eventResult;

  /** 事件补充说明。 */
  @Schema(description = "补充消息。", example = "显式创建文档成功。")
  @Column("message")
  private String message;
}
