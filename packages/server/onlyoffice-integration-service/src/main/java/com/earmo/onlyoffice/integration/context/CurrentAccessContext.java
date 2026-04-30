package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.model.RequestContext;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 保存当前同步请求线程内的访问上下文。
 */
public final class CurrentAccessContext {

  private static final ThreadLocal<AccessContext> CURRENT = new ThreadLocal<>();

  private CurrentAccessContext() {
  }

  public static void set(AccessContext accessContext) {
    if (accessContext == null) {
      clear();
      return;
    }
    CURRENT.set(accessContext);
  }

  public static AccessContext get() {
    return CURRENT.get();
  }

  public static AccessContext getRequired() {
    AccessContext accessContext = CURRENT.get();
    if (accessContext == null) {
      throw new AccessContextException("当前请求未绑定访问上下文。");
    }
    return accessContext;
  }

  public static RequestContext toRequestContext() {
    return getRequired().toRequestContext();
  }

  /**
   * 获取当前请求租户 ID。
   *
   * @return 当前访问上下文中的租户 ID。
   */
  public static String tenantId() {
    return getRequired().tenantId();
  }

  /**
   * 获取当前请求来源系统标识。
   *
   * @return 当前访问上下文中的来源系统。
   */
  public static String sourceSystem() {
    return getRequired().sourceSystem();
  }

  /**
   * 获取当前操作者账号。
   *
   * @return 当前访问上下文中的操作者账号。
   */
  public static String actorUser() {
    return getRequired().actorUser();
  }

  /**
   * 获取当前操作者展示名。
   *
   * @return 当前访问上下文中的操作者展示名。
   */
  public static String actorName() {
    return getRequired().actorName();
  }

  /**
   * 获取当前操作者权限映射。
   *
   * @return 当前访问上下文中的权限映射。
   */
  public static Map<String, Boolean> permissions() {
    return getRequired().permissions();
  }

  /**
   * 在当前线程临时绑定指定访问上下文后执行任务。
   *
   * @param accessContext 临时访问上下文。
   * @param runnable 待执行任务。
   */
  public static void runWith(AccessContext accessContext, Runnable runnable) {
    callWith(accessContext, () -> {
      runnable.run();
      return null;
    });
  }

  /**
   * 在当前线程临时绑定指定访问上下文后执行并返回结果。
   *
   * @param accessContext 临时访问上下文。
   * @param supplier 待执行结果供应器。
   * @param <T> 返回值类型。
   * @return supplier 返回的结果。
   */
  public static <T> T callWith(AccessContext accessContext, Supplier<T> supplier) {
    AccessContext previous = get();
    set(accessContext);
    try {
      return supplier.get();
    } finally {
      restore(previous);
    }
  }

  public static void clear() {
    CURRENT.remove();
  }

  private static void restore(AccessContext accessContext) {
    if (accessContext == null) {
      clear();
      return;
    }
    set(accessContext);
  }
}
