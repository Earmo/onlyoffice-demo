package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档运行事件实体。
 *
 * <p>它只承接“编辑运行态发生了什么”这一层信息：
 * 1. 编辑器被打开；
 * 2. 收到 ONLYOFFICE callback；
 * 3. 回写成功；
 * 4. 回写失败。
 *
 * <p>这样可以把运行排障需要的轨迹从文档主表里分离出来，同时保持主表只表达摘要状态。
 */
@Schema(description = "文档运行事件实体，用于记录编辑运行态关键轨迹。")
@Table("document_runtime_event")
@Getter
@Setter
public class DocumentRuntimeEventEntity {

  /** 运行事件主键。 */
  @Schema(description = "运行事件主键。", example = "evt-1")
  @Id
  @Column("event_id")
  private String eventId;

  /** 关联的文档内部主键。 */
  @Schema(description = "关联的文档内部主键。", example = "sample")
  @Column("document_id")
  private String documentId;

  /** 运行事件类型。 */
  @Schema(description = "运行事件类型。", example = "callback_received")
  @Column("event_type")
  private String eventType;

  /** ONLYOFFICE callback 状态码。 */
  @Schema(description = "ONLYOFFICE callback 状态码。", example = "2")
  @Column("callback_status")
  private Integer callbackStatus;

  /** 运行事件补充消息。 */
  @Schema(description = "给排障或前端展示的补充消息。", example = "已收到 ONLYOFFICE 保存回调。")
  @Column("event_message")
  private String eventMessage;

  /** 运行事件发生时间。 */
  @Schema(description = "运行事件发生时间。")
  @Column("event_time")
  private Instant eventTime;
}
