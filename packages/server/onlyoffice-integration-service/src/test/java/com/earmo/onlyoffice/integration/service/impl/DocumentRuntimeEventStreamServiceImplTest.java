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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

  @Test
  void shouldCancelKeepaliveWhenInitialSendFails() {
    ControlledScheduledExecutorService scheduler = new ControlledScheduledExecutorService();
    DocumentRuntimeEventStreamServiceImpl service = new DocumentRuntimeEventStreamServiceImpl(
        scheduler,
        Clock.fixed(Instant.parse("2026-04-22T00:00:00Z"), ZoneOffset.UTC),
        180000L,
        25000L,
        timeout -> new FailingInitialSendEmitter(timeout)
    );

    FailingInitialSendEmitter emitter = (FailingInitialSendEmitter) service.open(
        "demo",
        accessContext("user-a"),
        status("demo", "initial"),
        () -> {
        }
    );

    assertFalse(service.hasSubscribers("demo"));
    assertTrue(scheduler.lastScheduledFuture.cancelled);
    assertEquals(2, emitter.sendCount());
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

  private static class CapturingSseEmitter extends SseEmitter {

    protected final AtomicInteger sendCount = new AtomicInteger();
    private Runnable completionCallback = () -> {
    };
    private Runnable timeoutCallback = () -> {
    };
    private Consumer<Throwable> errorCallback = error -> {
    };

    private CapturingSseEmitter(Long timeout) {
      super(timeout);
    }

    @Override
    public synchronized void send(SseEventBuilder builder) throws java.io.IOException {
      sendCount.incrementAndGet();
      super.send(builder);
    }

    @Override
    public synchronized void complete() {
      super.complete();
      completionCallback.run();
    }

    @Override
    public synchronized void completeWithError(Throwable ex) {
      super.completeWithError(ex);
      errorCallback.accept(ex);
    }

    @Override
    public synchronized void onCompletion(Runnable callback) {
      super.onCompletion(callback);
      completionCallback = callback;
    }

    @Override
    public synchronized void onTimeout(Runnable callback) {
      super.onTimeout(callback);
      timeoutCallback = callback;
    }

    @Override
    public synchronized void onError(Consumer<Throwable> callback) {
      super.onError(callback);
      errorCallback = callback;
    }

    private void triggerTimeout() {
      timeoutCallback.run();
    }

    int sendCount() {
      return sendCount.get();
    }
  }

  private static final class FailingInitialSendEmitter extends CapturingSseEmitter {

    private final AtomicInteger remainingFailures = new AtomicInteger(1);

    private FailingInitialSendEmitter(Long timeout) {
      super(timeout);
    }

    @Override
    public synchronized void send(SseEventBuilder builder) throws java.io.IOException {
      if (remainingFailures.getAndDecrement() > 0) {
        sendCount.incrementAndGet();
        throw new java.io.IOException("initial send failed");
      }
      super.send(builder);
    }
  }

  private static final class ControlledScheduledExecutorService implements ScheduledExecutorService {

    private final ControlledScheduledFuture lastScheduledFuture = new ControlledScheduledFuture();

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command,
        long initialDelay,
        long period,
        TimeUnit unit
    ) {
      return lastScheduledFuture;
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
      return List.of();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.concurrent.Future<?> submit(Runnable task) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
        java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<java.util.concurrent.Future<T>> invokeAll(
        java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
        long timeout,
        TimeUnit unit
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(
        java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
        long timeout,
        TimeUnit unit
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> schedule(
        Runnable command,
        long delay,
        TimeUnit unit
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <V> java.util.concurrent.ScheduledFuture<V> schedule(
        java.util.concurrent.Callable<V> callable,
        long delay,
        TimeUnit unit
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public java.util.concurrent.ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command,
        long initialDelay,
        long delay,
        TimeUnit unit
    ) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class ControlledScheduledFuture implements ScheduledFuture<Object> {

    private boolean cancelled = false;

    @Override
    public long getDelay(TimeUnit unit) {
      return 0L;
    }

    @Override
    public int compareTo(java.util.concurrent.Delayed other) {
      return 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      cancelled = true;
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return cancelled;
    }

    @Override
    public Object get() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }
  }
}
