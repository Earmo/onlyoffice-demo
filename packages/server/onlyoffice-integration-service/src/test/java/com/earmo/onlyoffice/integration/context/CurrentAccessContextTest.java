package com.earmo.onlyoffice.integration.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import com.earmo.onlyoffice.integration.exception.AccessContextException;
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

  @Test
  void shouldExposeConvenienceAccessors() {
    CurrentAccessContext.set(accessContext());

    assertThat(CurrentAccessContext.tenantId()).isEqualTo("tenant-a");
    assertThat(CurrentAccessContext.sourceSystem()).isEqualTo("native");
    assertThat(CurrentAccessContext.actorUser()).isEqualTo("user-a");
    assertThat(CurrentAccessContext.actorName()).isEqualTo("Alice");
    assertThat(CurrentAccessContext.permissions()).containsEntry("edit", true);
  }

  @Test
  void shouldTemporarilyBindContextAndRestorePreviousContext() {
    AccessContext previous = accessContext();
    AccessContext temporary = new AccessContext("tenant-b", "native", "user-b", "Bob", Map.of(), "test");
    CurrentAccessContext.set(previous);

    String actor = CurrentAccessContext.callWith(temporary, CurrentAccessContext::actorUser);

    assertThat(actor).isEqualTo("user-b");
    assertThat(CurrentAccessContext.get()).isSameAs(previous);
  }

  @Test
  void shouldClearTemporaryContextWhenNoPreviousContextExists() {
    AccessContext temporary = accessContext();

    CurrentAccessContext.runWith(temporary, () -> assertThat(CurrentAccessContext.actorUser()).isEqualTo("user-a"));

    assertThat(CurrentAccessContext.get()).isNull();
  }

  @Test
  void shouldRestorePreviousContextAfterTemporaryBindingThrows() {
    AccessContext previous = accessContext();
    CurrentAccessContext.set(previous);

    assertThatThrownBy(() -> CurrentAccessContext.runWith(
        new AccessContext("tenant-b", "native", "user-b", "Bob", Map.of(), "test"),
        () -> {
          throw new IllegalStateException("boom");
        }
    )).isInstanceOf(IllegalStateException.class);

    assertThat(CurrentAccessContext.get()).isSameAs(previous);
  }

  private static AccessContext accessContext() {
    return new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of("edit", true), "test");
  }
}
