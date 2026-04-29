package com.earmo.onlyoffice.integration.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CurrentAccessContextTest {

  @AfterEach
  void tearDown() {
    CurrentAccessContext.clear();
  }

  @Test
  void shouldStoreReadAndClearCurrentAccessContext() {
    AccessContext accessContext = accessContext();

    CurrentAccessContext.set(accessContext);

    assertThat(CurrentAccessContext.get()).isSameAs(accessContext);
    assertThat(CurrentAccessContext.getRequired()).isSameAs(accessContext);

    CurrentAccessContext.clear();

    assertThat(CurrentAccessContext.get()).isNull();
  }

  @Test
  void shouldThrowBusinessExceptionWhenRequiredContextIsMissing() {
    assertThatThrownBy(CurrentAccessContext::getRequired)
        .isInstanceOf(AccessContextException.class)
        .hasMessageContaining("当前请求未绑定访问上下文");
  }

  @Test
  void shouldClearWhenSettingNullAndAllowRepeatedClear() {
    CurrentAccessContext.set(accessContext());
    CurrentAccessContext.set(null);
    CurrentAccessContext.clear();

    assertThat(CurrentAccessContext.get()).isNull();
  }

  @Test
  void shouldConvertToLegacyRequestContext() {
    CurrentAccessContext.set(accessContext());

    assertThat(CurrentAccessContext.toRequestContext())
        .extracting("tenantId", "sourceSystem", "externalUser", "displayName")
        .containsExactly("tenant-a", "native", "user-a", "Alice");
  }

  private static AccessContext accessContext() {
    return new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "test");
  }
}
