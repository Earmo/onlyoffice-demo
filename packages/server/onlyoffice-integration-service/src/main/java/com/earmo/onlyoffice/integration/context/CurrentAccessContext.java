package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.model.RequestContext;

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

  public static void clear() {
    CURRENT.remove();
  }
}
