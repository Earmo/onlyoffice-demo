package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import com.earmo.onlyoffice.integration.service.DocumentRuntimeEventStreamService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文档运行态 SSE 服务实现。
 *
 * <p>这里维护的是 Phase 14.1 的核心链路：
 * 1. 以 documentId 为分桶管理订阅者；
 * 2. 打开流时先发首帧 `save-status`，再发 `session-active`；
 * 3. 用固定 keepalive 同时保活代理链路和编辑会话；
 * 4. 任意发送失败都要走统一清理，避免 keepalive future 泄漏。
 *
 * <p>可以把这个类理解成“文档运行态广播中心”：
 * - `DocumentController.runtimeEvents()` 负责把单个浏览器接进来；
 * - `DocumentStatusServiceImpl.publishAndReturn()` 负责把后端状态变化推过来；
 * - 这里把两边接起来，保证同一个 documentId 下的浏览器都收到一致的运行态事实。
 */
@Service
public class DocumentRuntimeEventStreamServiceImpl implements DocumentRuntimeEventStreamService {

  // runtime-events 是文档级长连接，keepalive 负责保活，不应该被固定总时长强制切断。
  static final long DEFAULT_EMITTER_TIMEOUT_MILLIS = 0L;
  static final long DEFAULT_KEEPALIVE_INTERVAL_MILLIS = 25000L;

  private static final AtomicInteger KEEPALIVE_THREAD_COUNTER = new AtomicInteger(1);

  private final ConcurrentHashMap<String, CopyOnWriteArrayList<RuntimeSubscriber>> subscribersByDocumentId =
      new ConcurrentHashMap<>();
  private final ScheduledExecutorService keepaliveScheduler;
  private final Clock clock;
  private final long emitterTimeoutMillis;
  private final long keepaliveIntervalMillis;
  private final LongFunction<SseEmitter> emitterFactory;

  public DocumentRuntimeEventStreamServiceImpl() {
    this(
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread thread = new Thread(runnable);
          thread.setName("runtime-sse-keepalive-" + KEEPALIVE_THREAD_COUNTER.getAndIncrement());
          thread.setDaemon(true);
          return thread;
        }),
        Clock.systemUTC(),
        DEFAULT_EMITTER_TIMEOUT_MILLIS,
        DEFAULT_KEEPALIVE_INTERVAL_MILLIS,
        SseEmitter::new
    );
  }

  DocumentRuntimeEventStreamServiceImpl(
      ScheduledExecutorService keepaliveScheduler,
      Clock clock,
      long emitterTimeoutMillis,
      long keepaliveIntervalMillis,
      LongFunction<SseEmitter> emitterFactory
  ) {
    this.keepaliveScheduler = keepaliveScheduler;
    this.clock = clock;
    this.emitterTimeoutMillis = emitterTimeoutMillis;
    this.keepaliveIntervalMillis = keepaliveIntervalMillis;
    this.emitterFactory = emitterFactory;
  }

  @Override
  public SseEmitter open(
      String documentId,
      AccessContext accessContext,
      DocumentSaveStatusResponse initialStatus,
      Runnable livenessTouch
  ) {
    RuntimeSubscriber subscriber = new RuntimeSubscriber(
        documentId,
        normalizeActorUser(accessContext),
        emitterFactory.apply(emitterTimeoutMillis),
        livenessTouch == null ? () -> {
        } : livenessTouch
    );
    // 打开订阅的顺序不能打乱，原因如下：
    //
    // 第一步：先 register。
    // - 这样 emitter 一旦 completion/timeout/error，就能立刻进入统一 cleanup。
    // - 如果还没 register 就先 send，某些容器/代理已经断流时会让失败路径失控。
    //
    // 第二步：立刻创建并登记 keepalive future。
    // - 14.1 的 WR-01 就出在这里：如果先发首帧、后登记 future，
    //   那么首帧发送失败时 cleanup 看不到 future，定时任务就会泄漏。
    //
    // 第三步：发送首帧 save-status。
    // - 这让前端一连上就能拿到当前真实状态，而不是“先空白、再等下一次事件”。
    //
    // 第四步：检查 cleanupStarted。
    // - 首帧 send 过程中如果已经触发失败清理，就不应该继续发送 session-active。
    //
    // 第五步：再发 session-active。
    // - 到这里说明连接至少已经成功活过首帧，可以把“当前用户处于活跃编辑态”发给前端。
    registerSubscriber(subscriber);
    assignKeepaliveFuture(subscriber, keepaliveScheduler.scheduleAtFixedRate(
        () -> sendKeepalive(subscriber),
        keepaliveIntervalMillis,
        keepaliveIntervalMillis,
        TimeUnit.MILLISECONDS
    ));
    sendSaveStatus(subscriber, initialStatus);
    if (subscriber.cleanupStarted.get()) {
      return subscriber.emitter;
    }
    sendSessionActive(subscriber);
    return subscriber.emitter;
  }

  @Override
  public void publishSaveStatus(String documentId, DocumentSaveStatusResponse status) {
    CopyOnWriteArrayList<RuntimeSubscriber> subscribers = subscribersByDocumentId.get(documentId);
    if (subscribers == null) {
      return;
    }
    subscribers.forEach(subscriber -> sendSaveStatus(subscriber, status));
  }

  @PreDestroy
  void shutdownScheduler() {
    keepaliveScheduler.shutdownNow();
  }

  boolean hasSubscribers(String documentId) {
    CopyOnWriteArrayList<RuntimeSubscriber> subscribers = subscribersByDocumentId.get(documentId);
    return subscribers != null && !subscribers.isEmpty();
  }

  private void registerSubscriber(RuntimeSubscriber subscriber) {
    // subscriber 的生命周期全部收口到 cleanupSubscriber，理由是：
    // - completion：浏览器正常断开；
    // - timeout：服务端 emitter 到时；
    // - error：send 失败或底层连接异常。
    // 这三条路径如果分开各写一套逻辑，很容易出现“删了 subscriber 但没停 future”、
    // 或“停了 future 但 bucket 里还残留一个空引用”的半清理状态。
    subscribersByDocumentId.computeIfAbsent(subscriber.documentId, ignored -> new CopyOnWriteArrayList<>())
        .add(subscriber);
    subscriber.emitter.onCompletion(() -> cleanupSubscriber(subscriber));
    subscriber.emitter.onTimeout(() -> cleanupSubscriber(subscriber));
    subscriber.emitter.onError(error -> cleanupSubscriber(subscriber));
  }

  private void sendSaveStatus(RuntimeSubscriber subscriber, DocumentSaveStatusResponse status) {
    sendEvent(
        subscriber,
        SseEmitter.event()
            .name("save-status")
            .id(nextEventId(subscriber.documentId))
            .data(status)
    );
  }

  private void sendSessionActive(RuntimeSubscriber subscriber) {
    sendEvent(
        subscriber,
        SseEmitter.event()
            .name("session-active")
            .data(Map.of(
                "documentId", subscriber.documentId,
                "actorUser", subscriber.actorUser
            ))
    );
  }

  private void sendKeepalive(RuntimeSubscriber subscriber) {
    try {
      subscriber.emitter.send(
          SseEmitter.event()
              .name("keepalive")
              .data(Map.of(
                  "documentId", subscriber.documentId,
                  "time", Instant.now(clock).toString()
              ))
      );
      subscriber.livenessTouch.run();
    } catch (IOException | IllegalStateException exception) {
      handleSendFailure(subscriber, exception);
    }
  }

  private void sendEvent(RuntimeSubscriber subscriber, SseEmitter.SseEventBuilder eventBuilder) {
    try {
      subscriber.emitter.send(eventBuilder);
    } catch (IOException | IllegalStateException exception) {
      handleSendFailure(subscriber, exception);
    }
  }

  private void handleSendFailure(RuntimeSubscriber subscriber, Exception exception) {
    // 失败路径的处理目标不是“尽量继续发”，而是“尽快收口”：
    // 1. 先 best-effort 发一条 runtime-error，给仍然可读的前端一个明确原因；
    // 2. 如果连这条都发不出去，说明连接已经彻底坏了，直接忽略；
    // 3. completeWithError + cleanup，停止 keepalive 并移出 registry。
    try {
      subscriber.emitter.send(
          SseEmitter.event()
              .name("runtime-error")
              .data(Map.of(
                  "documentId", subscriber.documentId,
                  "message", exception.getMessage() == null ? "runtime stream send failed" : exception.getMessage()
              ))
      );
    } catch (IOException | IllegalStateException ignored) {
      // 连接已经不可用时不再放大异常，直接进入清理。
    }
    subscriber.emitter.completeWithError(exception);
    cleanupSubscriber(subscriber);
  }

  private void cleanupSubscriber(RuntimeSubscriber subscriber) {
    // 这里先 cancelKeepalive、再 compareAndSet，是专门为竞态兜底：
    // - 场景 A：sendSaveStatus 刚失败，cleanup 已被别处调用；
    // - 场景 B：scheduleAtFixedRate 返回值稍后才写进 subscriber.keepaliveFuture。
    // 如果先 compareAndSet，A 场景会让 B 场景的 future 永远失去取消机会。
    // 先 cancel 再 compareAndSet，哪怕 cleanupStarted 已经为 true，也还能补停 future。
    cancelKeepalive(subscriber);
    if (!subscriber.cleanupStarted.compareAndSet(false, true)) {
      return;
    }
    // bucket 里最后一个 subscriber 被移除后，连 documentId 键一起删掉。
    // 否则长时间运行后，map 会积累很多“空 bucket”，属于典型的慢性泄漏。
    subscribersByDocumentId.computeIfPresent(subscriber.documentId, (documentId, subscribers) -> {
      subscribers.remove(subscriber);
      return subscribers.isEmpty() ? null : subscribers;
    });
  }

  private void assignKeepaliveFuture(RuntimeSubscriber subscriber, ScheduledFuture<?> keepaliveFuture) {
    // 这里兜的是“future 刚写进去，cleanup 已经先完成”的反向竞态。
    // 如果 cleanupStarted 已经是 true，就说明前面某个 send 过程已经认定连接失效，
    // 那么这个 future 不能再留着跑，必须立刻 cancel。
    subscriber.keepaliveFuture = keepaliveFuture;
    if (subscriber.cleanupStarted.get()) {
      keepaliveFuture.cancel(true);
    }
  }

  private void cancelKeepalive(RuntimeSubscriber subscriber) {
    ScheduledFuture<?> keepaliveFuture = subscriber.keepaliveFuture;
    if (keepaliveFuture != null) {
      keepaliveFuture.cancel(true);
    }
  }

  private String nextEventId(String documentId) {
    return documentId + ":" + Instant.now(clock).toEpochMilli() + ":" + UUID.randomUUID();
  }

  private String normalizeActorUser(AccessContext accessContext) {
    if (accessContext == null || !StringUtils.hasText(accessContext.actorUser())) {
      return "";
    }
    return accessContext.actorUser();
  }

  private static final class RuntimeSubscriber {

    private final String documentId;
    private final String actorUser;
    private final SseEmitter emitter;
    private final Runnable livenessTouch;
    private final AtomicBoolean cleanupStarted = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> keepaliveFuture;

    private RuntimeSubscriber(String documentId, String actorUser, SseEmitter emitter, Runnable livenessTouch) {
      this.documentId = documentId;
      this.actorUser = actorUser;
      this.emitter = emitter;
      this.livenessTouch = livenessTouch;
    }
  }
}
