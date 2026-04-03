package com.earmo.onlyoffice.integration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ApiLoggingAspectTest {

  private final ApiLoggingAspect apiLoggingAspect = new ApiLoggingAspect(new ObjectMapper());

  @Test
  void shouldLogApiArgumentsAndCompletionTime(CapturedOutput output) throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    CodeSignature signature = mock(CodeSignature.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents");
    request.setQueryString("query=roadmap&pageSize=20");

    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getDeclaringType()).thenReturn((Class) DocumentApiController.class);
    when(signature.getName()).thenReturn("list");
    when(signature.getParameterNames()).thenReturn(new String[]{"query", "pageSize", "request"});
    when(joinPoint.getArgs()).thenReturn(new Object[]{"roadmap", 20, request});
    when(joinPoint.proceed()).thenReturn("ok");

    apiLoggingAspect.logControllerInvocation(joinPoint);

    assertThat(output).contains("API请求开始: method=DocumentApiController.list");
    assertThat(output).contains("\"query\":\"roadmap\"");
    assertThat(output).contains("\"pageSize\":20");
    assertThat(output).contains("\"requestUri\":\"/api/documents\"");
    assertThat(output).contains("API请求结束: method=DocumentApiController.list");
    assertThat(output).contains("completedAt=");
    assertThat(output).contains("success=true");
  }

  @Test
  void shouldLogCompletionTimeWhenApiFails(CapturedOutput output) throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    CodeSignature signature = mock(CodeSignature.class);

    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getDeclaringType()).thenReturn((Class) DocumentApiController.class);
    when(signature.getName()).thenReturn("delete");
    when(signature.getParameterNames()).thenReturn(new String[]{"documentId"});
    when(joinPoint.getArgs()).thenReturn(new Object[]{"sample"});
    when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("boom"));

    try {
      apiLoggingAspect.logControllerInvocation(joinPoint);
    } catch (IllegalArgumentException ex) {
      assertThat(ex).hasMessage("boom");
    }

    assertThat(output).contains("API请求开始: method=DocumentApiController.delete");
    assertThat(output).contains("\"documentId\":\"sample\"");
    assertThat(output).contains("API请求结束: method=DocumentApiController.delete");
    assertThat(output).contains("completedAt=");
    assertThat(output).contains("success=false");
  }
}
