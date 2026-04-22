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

@Service
public class DocumentRuntimeEventStreamServiceImpl implements DocumentRuntimeEventStreamService {

  static final long DEFAULT_EMITTER_TIMEOUT_MILLIS = 180000L;
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
    registerSubscriber(subscriber);
    sendSaveStatus(subscriber, initialStatus);
    sendSessionActive(subscriber);
    subscriber.keepaliveFuture = keepaliveScheduler.scheduleAtFixedRate(
        () -> sendKeepalive(subscriber),
        keepaliveIntervalMillis,
        keepaliveIntervalMillis,
        TimeUnit.MILLISECONDS
    );
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
    if (!subscriber.cleanupStarted.compareAndSet(false, true)) {
      return;
    }
    if (subscriber.keepaliveFuture != null) {
      subscriber.keepaliveFuture.cancel(true);
    }
    subscribersByDocumentId.computeIfPresent(subscriber.documentId, (documentId, subscribers) -> {
      subscribers.remove(subscriber);
      return subscribers.isEmpty() ? null : subscribers;
    });
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
