package com.earmo.onlyoffice.integration.web;

import com.earmo.onlyoffice.integration.model.llm.SendLlmMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiLoggingAspect {

  private static final String REDACTED = "[REDACTED]";

  private final ObjectMapper objectMapper;

  /**
   * 记录所有 REST Controller 调用的开始、结束和脱敏参数。
   *
   * @param joinPoint 当前被拦截的 controller 方法调用点。
   * @return 原 controller 方法返回值。
   * @throws Throwable controller 方法执行时抛出的原始异常。
   */
  @Around("within(@org.springframework.web.bind.annotation.RestController *)")
  public Object logControllerInvocation(ProceedingJoinPoint joinPoint) throws Throwable {
    Instant startedAt = Instant.now();
    String methodName = resolveMethodName(joinPoint);
    String arguments = serializeArguments(joinPoint);

    log.info("=============================>>>");
    log.info("API请求开始: method={}, args={}", methodName, arguments);

    boolean succeeded = false;
    try {
      Object result = joinPoint.proceed();
      succeeded = true;
      return result;
    } finally {
      Instant completedAt = Instant.now();
      log.info(
          "API请求结束: method={}, completedAt={}, durationMs={}, success={} ",
          methodName,
          completedAt,
          Duration.between(startedAt, completedAt).toMillis(),
          succeeded
      );
      log.info("<<<=============================");
    }
  }

  /**
   * 解析 controller 方法的短名称。
   *
   * @param joinPoint 当前被拦截的 controller 方法调用点。
   * @return 类名与方法名组合成的可检索名称。
   */
  private String resolveMethodName(ProceedingJoinPoint joinPoint) {
    return joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName();
  }

  /**
   * 将 controller 参数序列化成日志可写入的 JSON 字符串。
   *
   * @param joinPoint 当前被拦截的 controller 方法调用点。
   * @return 已脱敏的参数 JSON；序列化失败时返回 Map 字符串。
   */
  private String serializeArguments(ProceedingJoinPoint joinPoint) {
    CodeSignature signature = (CodeSignature) joinPoint.getSignature();
    String[] parameterNames = signature.getParameterNames();
    Object[] args = joinPoint.getArgs();

    Map<String, Object> argumentMap = new LinkedHashMap<>();
    for (int index = 0; index < args.length; index++) {
      String parameterName = parameterNames != null && index < parameterNames.length
          ? parameterNames[index]
          : "arg" + index;
      argumentMap.put(parameterName, sanitizeValue(args[index]));
    }

    try {
      return objectMapper.writeValueAsString(argumentMap);
    } catch (JsonProcessingException ex) {
      return String.valueOf(argumentMap);
    }
  }

  /**
   * 对日志参数进行递归脱敏。
   *
   * @param value 原始参数值。
   * @return 可安全写入 info 日志的值。
   */
  private Object sanitizeValue(Object value) {
    if (value == null || isSimpleValue(value)) {
      return value;
    }
    if (value instanceof HttpServletRequest request) {
      Map<String, Object> requestInfo = new LinkedHashMap<>();
      requestInfo.put("method", request.getMethod());
      requestInfo.put("requestUri", request.getRequestURI());
      requestInfo.put("queryString", request.getQueryString());
      return requestInfo;
    }
    if (value instanceof ServletResponse response) {
      return Map.of("type", response.getClass().getSimpleName());
    }
    if (value instanceof MultipartFile multipartFile) {
      Map<String, Object> fileInfo = new LinkedHashMap<>();
      fileInfo.put("name", multipartFile.getName());
      fileInfo.put("originalFilename", multipartFile.getOriginalFilename());
      fileInfo.put("contentType", multipartFile.getContentType());
      fileInfo.put("size", multipartFile.getSize());
      return fileInfo;
    }
    if (value instanceof SendLlmMessageRequest request) {
      return sanitizeLlmMessageRequest(request);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sanitizedMap = new LinkedHashMap<>();
      map.forEach((key, mapValue) -> sanitizedMap.put(String.valueOf(key), sanitizeValue(mapValue)));
      return sanitizedMap;
    }
    if (value instanceof Iterable<?> iterable) {
      List<Object> sanitizedValues = new ArrayList<>();
      iterable.forEach(item -> sanitizedValues.add(sanitizeValue(item)));
      return sanitizedValues;
    }
    if (value.getClass().isArray()) {
      int length = Array.getLength(value);
      List<Object> sanitizedValues = new ArrayList<>(length);
      for (int index = 0; index < length; index++) {
        sanitizedValues.add(sanitizeValue(Array.get(value, index)));
      }
      return sanitizedValues;
    }
    try {
      return objectMapper.convertValue(value, Object.class);
    } catch (IllegalArgumentException ex) {
      return value.toString();
    }
  }

  /**
   * 脱敏 AI 对话请求体。
   *
   * @param request AI 对话请求体。
   * @return 隐藏 prompt、选区和标题正文后的参数映射。
   */
  private Map<String, Object> sanitizeLlmMessageRequest(SendLlmMessageRequest request) {
    Map<String, Object> requestInfo = new LinkedHashMap<>();
    requestInfo.put("documentId", request.documentId());
    requestInfo.put("sessionId", request.sessionId());
    requestInfo.put("provider", request.provider());
    requestInfo.put("model", request.model());
    requestInfo.put("question", REDACTED);
    requestInfo.put("selectionSnapshot", sanitizeSelectionSnapshot(request.selectionSnapshot()));
    requestInfo.put("headingContext", sanitizeHeadingContext(request.headingContext()));
    requestInfo.put("retryConfirmed", request.retryConfirmed());
    requestInfo.put("regenerateAssistantMessageId", request.regenerateAssistantMessageId());
    return requestInfo;
  }

  /**
   * 脱敏选区快照。
   *
   * @param selectionSnapshot 选区快照。
   * @return 隐藏选区正文后的快照映射。
   */
  private Map<String, Object> sanitizeSelectionSnapshot(SendLlmMessageRequest.SelectionSnapshot selectionSnapshot) {
    if (selectionSnapshot == null) {
      return Map.of("text", REDACTED);
    }
    Map<String, Object> snapshotInfo = new LinkedHashMap<>();
    snapshotInfo.put("text", REDACTED);
    snapshotInfo.put("emptySelection", selectionSnapshot.emptySelection());
    return snapshotInfo;
  }

  /**
   * 脱敏章节标题上下文。
   *
   * @param headingContext 章节标题上下文。
   * @return 隐藏标题正文后的上下文映射。
   */
  private Map<String, Object> sanitizeHeadingContext(SendLlmMessageRequest.HeadingContext headingContext) {
    if (headingContext == null) {
      return Map.of("headingText", REDACTED);
    }
    Map<String, Object> headingInfo = new LinkedHashMap<>();
    headingInfo.put("includeHeading", headingContext.includeHeading());
    headingInfo.put("headingId", headingContext.headingId());
    headingInfo.put("headingText", REDACTED);
    return headingInfo;
  }

  /**
   * 判断对象是否可以直接写入日志。
   *
   * @param value 待判断对象。
   * @return true 表示该对象是简单值，不需要递归脱敏。
   */
  private boolean isSimpleValue(Object value) {
    return value instanceof CharSequence
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Enum<?>
        || value instanceof Instant;
  }
}
