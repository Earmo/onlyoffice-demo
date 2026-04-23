package com.earmo.onlyoffice.integration.service.llm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * 请求执行注册表。
 *
 * <p>用于在内存中维护“当前有哪些 AI 请求正在执行、是否已取消、终态是否已经被抢占成功”等运行态信息。
 * 数据库保存可持久化状态，这里只保存单次执行过程中的并发协调数据。
 */
@Component
public class LlmRequestExecutionRegistry {

  private final Map<String, ExecutionState> executions = new ConcurrentHashMap<>();

  /**
   * 注册一个新的运行中请求。
   */
  public void register(String requestId, SpringAiLlmProvider strategy) {
    executions.put(requestId, new ExecutionState(strategy));
  }

  /**
   * 绑定上游 provider 返回的 request id。
   *
   * <p>这样后续若 provider 支持上游取消，就可以把本地取消信号继续转发出去。
   */
  public void attachProviderRequestId(String requestId, String providerRequestId) {
    ExecutionState state = executions.get(requestId);
    if (state != null) {
      state.providerRequestId = providerRequestId;
    }
  }

  /**
   * 发起取消。
   *
   * <p>本地 `cancelled` 标记是强保证，上游取消只是 best effort。
   */
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

  /**
   * 判断请求是否已被标记为取消。
   */
  public boolean isCancelled(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.cancelled.get();
  }

  /**
   * 判断当前请求是否仍有活跃执行上下文。
   */
  public boolean hasExecution(String requestId) {
    return executions.containsKey(requestId);
  }

  /**
   * 尝试把请求终态设置为 completed。
   *
   * <p>只有第一个抢到终态的线程能成功，避免 completed / failed / cancelled 相互覆盖。
   */
  public boolean tryMarkCompleted(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.terminalStatus.compareAndSet(null, "completed");
  }

  /**
   * 尝试把请求终态设置为 failed。
   */
  public boolean tryMarkFailed(String requestId) {
    ExecutionState state = executions.get(requestId);
    return state != null && state.terminalStatus.compareAndSet(null, "failed");
  }

  /**
   * 尝试把请求终态设置为 cancelled，并在可行时向上游发送取消信号。
   */
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

  /**
   * 请求结束后清理内存态。
   */
  public void unregister(String requestId) {
    executions.remove(requestId);
  }

  /**
   * 单个请求的运行态快照。
   */
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
