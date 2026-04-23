package com.earmo.onlyoffice.integration.data.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档编辑会话实体。
 *
 * <p>这张表只表达“当前是否还有人在编辑”这一层真相：
 * 1. 打开编辑工作台时建立活跃会话；
 * 2. 返回列表、切换文档或主动离开时关闭会话；
 * 3. 列表页的 `editing` 语义以活跃会话摘要为准，而不是只看 callback 或历史运行事件。
 */
@Schema(description = "文档编辑会话实体，用于表达当前活跃编辑用户。")
@Table("document_editor_session")
@Getter
@Setter
public class DocumentEditorSessionEntity {

  /** 编辑会话主键。 */
  @Schema(description = "编辑会话主键。", example = "session-1")
  @Id
  @Column("session_id")
  private String sessionId;

  /** 关联的文档内部主键。 */
  @Schema(description = "关联的文档内部主键。", example = "sample")
  @Column("document_id")
  private String documentId;

  /** 会话所属租户。 */
  @Schema(description = "会话所属租户。", example = "native")
  @Column("tenant_id")
  private String tenantId;

  /** 当前编辑用户标识。 */
  @Schema(description = "当前编辑用户标识。", example = "starter-user")
  @Column("actor_user")
  private String actorUser;

  /** 当前编辑用户显示名。 */
  @Schema(description = "当前编辑用户显示名。", example = "Alice")
  @Column("actor_name")
  private String actorName;

  /** 会话建立时间。 */
  @Schema(description = "会话建立时间。")
  @Column("opened_time")
  private Instant openedTime;

  /** 最近一次确认会话仍活跃的时间。 */
  @Schema(description = "最近一次确认会话仍活跃的时间。")
  @Column("last_seen_time")
  private Instant lastSeenTime;

  /** 会话关闭时间，`null` 表示当前仍活跃。 */
  @Schema(description = "会话关闭时间；为 null 表示当前仍活跃。")
  @Column("closed_time")
  private Instant closedTime;

  /** 记录创建时间。 */
  @Schema(description = "记录创建时间。")
  @Column("created_time")
  private Instant createdTime;

  /** 记录更新时间。 */
  @Schema(description = "记录更新时间。")
  @Column("updated_time")
  private Instant updatedTime;
}
