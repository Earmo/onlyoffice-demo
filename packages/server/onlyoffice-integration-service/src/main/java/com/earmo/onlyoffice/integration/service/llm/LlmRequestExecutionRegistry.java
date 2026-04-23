package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class LlmRequestExecutionRegistry {

  private final Map<String, ExecutionState> executions = new ConcurrentHashMap<>();

  public void register(String requestId, SpringAiLlmProvider strategy) {
    executions.put(requestId, new ExecutionState(strategy));
  }

  public void attachProviderRequestId(String requestId, String providerRequestId) {
    ExecutionState state = executions.get(requestId);
    if (state != null) {
      state.providerRequestId = providerRequestId;
    }
  }

  public void cancel(String requestId) {
    ExecutionState state = executions.get(requestId);
    if (state == null) {
      return;
    }
    state.cancelled.set(true);
    if (state.providerRequestId != null && state.strategy.supportsUpstreamCancel()) {
      // 本地 cancelled 才是真正硬保证；上游取消只做 best effort。
      state.strategy.cancelRequest(state.providerRequestId);
    }
  }

  public boolean isCancelled(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.cancelled.get();
  }

  public boolean hasExecution(String requestId) {
    return executions.containsKey(requestId);
  }

  public boolean tryMarkCompleted(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.terminalStatus.compareAndSet(null, "completed");
  }

  public boolean tryMarkFailed(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.terminalStatus.compareAndSet(null, "failed");
  }

  public boolean tryMarkCancelled(String requestId) {
    ExecutionState state = executions.get(requestId);
    if (state == null) {
      return false;
    }
    if ("cancelled".equals(state.terminalStatus.get())) {
      return true;
    }
    if (!state.terminalStatus.compareAndSet(null, "cancelled")) {
      return false;
    }
    state.cancelled.set(true);
    if (state.providerRequestId != null && state.strategy.supportsUpstreamCancel()) {
      // 本地 cancelled 才是真正硬保证；上游取消只做 best effort。
      state.strategy.cancelRequest(state.providerRequestId);
    }
    return true;
  }

  public void unregister(String requestId) {
    executions.remove(requestId);
  }

  private static final class ExecutionState {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<String> terminalStatus = new AtomicReference<>(null);
    private final SpringAiLlmProvider strategy;
    private volatile String providerRequestId;

    private ExecutionState(SpringAiLlmProvider strategy) {
      this.strategy = strategy;
    }
  }
}
