package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class LlmRequestExecutionRegistry {

  private final Map<String, ExecutionState> executions = new ConcurrentHashMap<>();

  public void register(String requestId, LlmProviderStrategy strategy) {
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

  public void unregister(String requestId) {
    executions.remove(requestId);
  }

  private static final class ExecutionState {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final LlmProviderStrategy strategy;
    private volatile String providerRequestId;

    private ExecutionState(LlmProviderStrategy strategy) {
      this.strategy = strategy;
    }
  }
}
