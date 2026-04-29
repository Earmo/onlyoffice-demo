package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.SkipAccessContext;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractRouteTest {

  @Test
  void shouldExposePrimaryDocumentPostBodyRoutes() {
    assertThat(postRoutes(DocumentApiController.class))
        .contains("/page", "/list/recent", "/get", "/delete", "/upload", "/import-remote");
    assertThat(postRoutes(DocumentController.class))
        .contains("/get/editor-config", "/close/session", "/save", "/get/save-status");
  }

  @Test
  void shouldExposePrimaryLlmPostBodyRoutes() {
    assertThat(postRoutes(LlmController.class))
        .contains(
            "/get/capability",
            "/list/session",
            "/get/session",
            "/delete/session",
            "/rename/session",
            "/get/request",
            "/cancel/request"
        );
  }

  @Test
  void shouldMarkCompatibilityRoutesAsDeprecated() {
    assertThat(deprecatedRouteNames(DocumentApiController.class)).contains("list", "recent", "detail", "delete");
    assertThat(deprecatedRouteNames(DocumentController.class))
        .contains("editorConfig", "closeEditingSession", "saveDocument", "saveStatus");
    assertThat(deprecatedRouteNames(LlmController.class))
        .contains("capability", "listSessions", "getSession", "deleteSession", "renameSession", "getRequest", "cancelRequest");
  }

  @Test
  void shouldDeclareProtocolRoutesAsExplicitAccessContextSkips() throws NoSuchMethodException {
    assertThat(DocumentController.class.getMethod("file", String.class).isAnnotationPresent(SkipAccessContext.class)).isTrue();
    assertThat(DocumentController.class.getMethod("fileWithExtension", String.class, String.class).isAnnotationPresent(SkipAccessContext.class)).isTrue();
    assertThat(DocumentController.class.getMethod("proxyImage", String.class, String.class).isAnnotationPresent(SkipAccessContext.class)).isTrue();
    assertThat(DocumentController.class.getMethod(
            "callback",
            String.class,
            com.earmo.onlyoffice.integration.model.OnlyofficeCallbackRequest.class,
            jakarta.servlet.http.HttpServletRequest.class
        )
        .isAnnotationPresent(SkipAccessContext.class)).isTrue();
  }

  private Set<String> postRoutes(Class<?> controllerClass) {
    return Arrays.stream(controllerClass.getDeclaredMethods())
        .map(method -> method.getAnnotation(PostMapping.class))
        .filter(annotation -> annotation != null)
        .flatMap(annotation -> Arrays.stream(annotation.value()))
        .collect(Collectors.toSet());
  }

  private Set<String> deprecatedRouteNames(Class<?> controllerClass) {
    return Arrays.stream(controllerClass.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(GetMapping.class)
            || method.isAnnotationPresent(DeleteMapping.class)
            || method.isAnnotationPresent(PostMapping.class)
            || method.isAnnotationPresent(PutMapping.class))
        .filter(method -> method.isAnnotationPresent(Deprecated.class))
        .map(Method::getName)
        .collect(Collectors.toSet());
  }
}
