package com.earmo.onlyoffice.demo.service;

import com.earmo.onlyoffice.demo.model.DocumentSaveStatusResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 记录文档最近一次 callback 与保存结果。
 *
 * <p>这个示例没有数据库，因此直接把运行态状态保存在内存里。
 * 目标不是做持久化审计，而是给前端一个足够直观的“最近有没有真正回写成功”信号。
 */
@Service
public class DocumentStatusService {

  private final Map<String, DocumentStatusSnapshot> statuses = new ConcurrentHashMap<>();

  /**
   * 确保文档至少有一份初始状态，便于前端首次打开时就能拿到明确反馈。
   */
  public DocumentSaveStatusResponse initialize(String documentId) {
    return toResponse(documentId, statuses.computeIfAbsent(documentId, ignored -> DocumentStatusSnapshot.idle()));
  }

  /**
   * 记录已收到 ONLYOFFICE callback。
   */
  public DocumentSaveStatusResponse recordCallbackReceived(String documentId, Integer callbackStatus) {
    DocumentStatusSnapshot snapshot = statuses.compute(documentId, (ignored, current) -> {
      DocumentStatusSnapshot base = current == null ? DocumentStatusSnapshot.idle() : current;
      return base.withCallback(callbackStatus, Instant.now());
    });
    return toResponse(documentId, snapshot);
  }

  /**
   * 记录文档已成功回写到本地存储。
   */
  public DocumentSaveStatusResponse recordSaveSucceeded(String documentId, Integer callbackStatus) {
    DocumentStatusSnapshot snapshot = statuses.compute(documentId, (ignored, current) -> {
      DocumentStatusSnapshot base = current == null ? DocumentStatusSnapshot.idle() : current;
      Instant now = Instant.now();
      return new DocumentStatusSnapshot(
          "saved",
          "最新修改已成功回写到 Spring Boot 本地存储。",
          callbackStatus != null ? callbackStatus : base.lastCallbackStatus(),
          base.lastCallbackAt() != null ? base.lastCallbackAt() : now,
          now
      );
    });
    return toResponse(documentId, snapshot);
  }

  /**
   * 记录保存失败，便于前端明确提示“当前改动可能仍停留在 ONLYOFFICE 缓存中”。
   */
  public DocumentSaveStatusResponse recordSaveFailed(String documentId, Integer callbackStatus, String failureReason) {
    DocumentStatusSnapshot snapshot = statuses.compute(documentId, (ignored, current) -> {
      DocumentStatusSnapshot base = current == null ? DocumentStatusSnapshot.idle() : current;
      Instant now = Instant.now();
      String message = "回写本地存储失败";
      if (failureReason != null && !failureReason.isBlank()) {
        message += "：" + failureReason;
      }
      return new DocumentStatusSnapshot(
          "save-failed",
          message,
          callbackStatus != null ? callbackStatus : base.lastCallbackStatus(),
          base.lastCallbackAt() != null ? base.lastCallbackAt() : now,
          base.lastSavedAt()
      );
    });
    return toResponse(documentId, snapshot);
  }

  /**
   * 查询当前文档最近一次保存状态。
   */
  public DocumentSaveStatusResponse getStatus(String documentId) {
    return toResponse(documentId, statuses.computeIfAbsent(documentId, ignored -> DocumentStatusSnapshot.idle()));
  }

  private DocumentSaveStatusResponse toResponse(String documentId, DocumentStatusSnapshot snapshot) {
    return new DocumentSaveStatusResponse(
        documentId,
        snapshot.state(),
        snapshot.message(),
        snapshot.lastCallbackStatus(),
        snapshot.lastCallbackAt(),
        snapshot.lastSavedAt()
    );
  }

  /**
   * 内部状态快照。
   */
  private record DocumentStatusSnapshot(
      String state,
      String message,
      Integer lastCallbackStatus,
      Instant lastCallbackAt,
      Instant lastSavedAt
  ) {

    private static DocumentStatusSnapshot idle() {
      return new DocumentStatusSnapshot(
          "idle",
          "尚未收到 ONLYOFFICE 保存回调。",
          null,
          null,
          null
      );
    }

    private DocumentStatusSnapshot withCallback(Integer callbackStatus, Instant callbackAt) {
      return new DocumentStatusSnapshot(
          "callback-received",
          "已收到 ONLYOFFICE 保存回调，正在回写本地存储。",
          callbackStatus,
          callbackAt,
          lastSavedAt
      );
    }
  }
}
