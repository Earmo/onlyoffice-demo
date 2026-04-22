package com.earmo.onlyoffice.integration.service.impl;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRuntimeEventStreamServiceImplTest {

  @Test
  void shouldRegisterInitialSubscriberAndCleanupOnCompletion() throws Exception {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      DocumentRuntimeEventStreamServiceImpl service = new DocumentRuntimeEventStreamServiceImpl(
          scheduler,
          Clock.fixed(Instant.parse("2026-04-22T00:00:00Z"), ZoneOffset.UTC),
          180000L,
          25000L,
          timeout -> new CapturingSseEmitter(timeout)
      );

      CapturingSseEmitter emitter = (CapturingSseEmitter) service.open(
          "demo",
          accessContext("user-a"),
          status("demo", "initial"),
          () -> {
          }
      );

      assertEquals(180000L, emitter.getTimeout());
      assertEquals(2, emitter.sendCount());
      assertTrue(service.hasSubscribers("demo"));

      emitter.complete();

      assertFalse(service.hasSubscribers("demo"));
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void shouldPublishSaveStatusOnlyToMatchingDocumentSubscribers() {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      DocumentRuntimeEventStreamServiceImpl service = new DocumentRuntimeEventStreamServiceImpl(
          scheduler,
          Clock.fixed(Instant.parse("2026-04-22T00:00:00Z"), ZoneOffset.UTC),
          180000L,
          25000L,
          timeout -> new CapturingSseEmitter(timeout)
      );

      CapturingSseEmitter demoEmitter = (CapturingSseEmitter) service.open(
          "demo",
          accessContext("user-a"),
          status("demo", "initial-demo"),
          () -> {
          }
      );
      CapturingSseEmitter otherEmitter = (CapturingSseEmitter) service.open(
          "other",
          accessContext("user-b"),
          status("other", "initial-other"),
          () -> {
          }
      );

      service.publishSaveStatus("demo", status("demo", "updated-demo"));

      assertEquals(3, demoEmitter.sendCount());
      assertEquals(2, otherEmitter.sendCount());
    } finally {
      scheduler.shutdownNow();
    }
  }

  @Test
  void shouldSendKeepaliveAndTouchLiveness() throws Exception {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    CountDownLatch keepaliveTouched = new CountDownLatch(1);
    try {
      DocumentRuntimeEventStreamServiceImpl service = new DocumentRuntimeEventStreamServiceImpl(
          scheduler,
          Clock.fixed(Instant.parse("2026-04-22T00:00:00Z"), ZoneOffset.UTC),
          180000L,
          10L,
          timeout -> new CapturingSseEmitter(timeout)
      );

      CapturingSseEmitter emitter = (CapturingSseEmitter) service.open(
          "demo",
          accessContext("user-a"),
          status("demo", "initial"),
          keepaliveTouched::countDown
      );

      assertTrue(keepaliveTouched.await(1, TimeUnit.SECONDS));
      assertTrue(emitter.sendCount() >= 3);
    } finally {
      scheduler.shutdownNow();
    }
  }

  private AccessContext accessContext(String actorUser) {
    return new AccessContext(
        "tenant-a",
        "native",
        actorUser,
        "Alice",
        Map.of("edit", true),
        "header"
    );
  }

  private DocumentSaveStatusResponse status(String documentId, String state) {
    return new DocumentSaveStatusResponse(documentId, state, state, null, null, null, List.of());
  }

  private static final class CapturingSseEmitter extends SseEmitter {

    private final AtomicInteger sendCount = new AtomicInteger();

    private CapturingSseEmitter(Long timeout) {
      super(timeout);
    }

    @Override
    public synchronized void send(SseEventBuilder builder) throws java.io.IOException {
      sendCount.incrementAndGet();
      super.send(builder);
    }

    private int sendCount() {
      return sendCount.get();
    }
  }
}
