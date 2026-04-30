package com.earmo.onlyoffice.integration.context;

import com.earmo.onlyoffice.integration.exception.AccessContextException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessContextAspectTest {

    @AfterEach
    void tearDown() {
        CurrentAccessContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldBindContextForControllerCallAndClearAfterSuccess() throws Throwable {
        AccessContext accessContext = accessContext();
        AccessContextResolver accessContextResolver = mock(AccessContextResolver.class);
        when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(accessContext);
        AccessContextAspect aspect = new AccessContextAspect(accessContextResolver);
        ProceedingJoinPoint joinPoint = joinPoint(() -> {
            assertThat(CurrentAccessContext.getRequired()).isSameAs(accessContext);
            return "ok";
        });
        bindRequest();

        Object result = aspect.bindAccessContext(joinPoint);

        assertThat(result).isEqualTo("ok");
        assertThat(CurrentAccessContext.get()).isNull();
    }

    @Test
    void shouldClearContextAfterControllerException() {
        AccessContextResolver accessContextResolver = mock(AccessContextResolver.class);
        when(accessContextResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(accessContext());
        AccessContextAspect aspect = new AccessContextAspect(accessContextResolver);
        ProceedingJoinPoint joinPoint = joinPoint(() -> {
            throw new IllegalArgumentException("boom");
        });
        bindRequest();

        assertThatThrownBy(() -> aspect.bindAccessContext(joinPoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("boom");
        assertThat(CurrentAccessContext.get()).isNull();
    }

    @Test
    void shouldFailWhenServletRequestIsMissing() {
        AccessContextAspect aspect = new AccessContextAspect(mock(AccessContextResolver.class));
        ProceedingJoinPoint joinPoint = joinPoint(() -> "ok");

        assertThatThrownBy(() -> aspect.bindAccessContext(joinPoint))
                .isInstanceOf(AccessContextException.class)
                .hasMessageContaining("当前请求无法解析访问上下文");
    }

    private static void bindRequest() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private static ProceedingJoinPoint joinPoint(ThrowingSupplier supplier) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("TestController.method(..)");
        when(joinPoint.getSignature()).thenReturn(signature);
        try {
            when(joinPoint.proceed()).thenAnswer(invocation -> supplier.get());
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        return joinPoint;
    }

    private static AccessContext accessContext() {
        return new AccessContext("tenant-a", "native", "user-a", "Alice", Map.of(), "test");
    }

    private interface ThrowingSupplier {
        Object get() throws Throwable;
    }
}
