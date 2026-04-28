package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
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

  @Test
  void shouldRedactLlmMessageRequestSensitiveBodyFields(CapturedOutput output) throws Throwable {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    CodeSignature signature = mock(CodeSignature.class);
    SendLlmMessageRequest request = new SendLlmMessageRequest(
        "doc-llm",
        "session-llm",
        "stub-provider",
        "fake-gpt",
        "请根据这段敏感用户问题重新生成",
        new SendLlmMessageRequest.SelectionSnapshot("这是一段敏感选区正文", false),
        new SendLlmMessageRequest.HeadingContext(true, "heading-1", "敏感标题正文"),
        true,
        "assistant-1"
    );

    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getDeclaringType()).thenReturn((Class) LlmController.class);
    when(signature.getName()).thenReturn("streamMessage");
    when(signature.getParameterNames()).thenReturn(new String[]{"request"});
    when(joinPoint.getArgs()).thenReturn(new Object[]{request});
    when(joinPoint.proceed()).thenReturn("ok");

    apiLoggingAspect.logControllerInvocation(joinPoint);

    assertThat(output).contains("API请求开始: method=LlmController.streamMessage");
    assertThat(output).contains("\"documentId\":\"doc-llm\"");
    assertThat(output).contains("\"sessionId\":\"session-llm\"");
    assertThat(output).contains("\"provider\":\"stub-provider\"");
    assertThat(output).contains("\"model\":\"fake-gpt\"");
    assertThat(output).contains("\"question\":\"[REDACTED]\"");
    assertThat(output).contains("\"text\":\"[REDACTED]\"");
    assertThat(output).contains("\"headingText\":\"[REDACTED]\"");
    assertThat(output).contains("\"regenerateAssistantMessageId\":\"assistant-1\"");
    assertThat(output).doesNotContain("请根据这段敏感用户问题重新生成");
    assertThat(output).doesNotContain("这是一段敏感选区正文");
    assertThat(output).doesNotContain("敏感标题正文");
  }
}
