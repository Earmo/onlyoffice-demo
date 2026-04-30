package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.context.SkipAccessContext;
import com.earmo.onlyoffice.integration.model.request.OnlyofficeCallbackRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractRouteTest {

    @Test
    void shouldExposePrimaryDocumentPostBodyRoutes() {
        assertThat(postRoutes(DocumentApiController.class))
                .contains("/page", "/list/recent", "/detail", "/create", "/delete", "/upload", "/import-remote");
        assertThat(postRoutes(DocumentController.class))
                .contains("/editor-config", "/close/session", "/save", "/save-status");
    }

    @Test
    void shouldExposePrimaryLlmPostBodyRoutes() {
        assertThat(postRoutes(LlmController.class))
                .contains(
                        "/capability/query",
                        "/sessions/list",
                        "/sessions/create",
                        "/sessions/detail",
                        "/sessions/delete",
                        "/sessions/rename",
                        "/requests/detail",
                        "/requests/cancel"
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
                        OnlyofficeCallbackRequest.class,
                        jakarta.servlet.http.HttpServletRequest.class
                )
                .isAnnotationPresent(SkipAccessContext.class)).isTrue();
    }

    @Test
    void shouldKeepAccessContextResolutionOutOfControllers() throws Exception {
        Path controllerRoot = Path.of("src/main/java/com/earmo/onlyoffice/integration/controller");

        try (java.util.stream.Stream<Path> paths = Files.walk(controllerRoot)) {
            String controllerSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .collect(Collectors.joining("\n"));

            assertThat(controllerSources).doesNotContain("AccessContextResolver");
            assertThat(controllerSources).doesNotContain(".resolve(request)");
        }
    }

    @Test
    void shouldDocumentRemainingExplicitServiceAccessContextBoundaries() throws Exception {
        Path serviceRoot = Path.of("src/main/java/com/earmo/onlyoffice/integration/service");
        Set<String> allowedFiles = Set.of(
                "AccessAuditService.java",
                "DocumentRuntimeEventStreamService.java",
                "DocumentStatusService.java",
                "OnlyofficeConfigService.java",
                "AccessAuditServiceImpl.java",
                "DocumentRuntimeEventStreamServiceImpl.java",
                "DocumentStatusServiceImpl.java",
                "OnlyofficeConfigServiceImpl.java",
                "LlmConversationAccessGuard.java",
                "LlmConversationService.java"
        );

        try (java.util.stream.Stream<Path> paths = Files.walk(serviceRoot)) {
            Set<String> filesWithExplicitContext = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("AccessContext accessContext");
                        } catch (java.io.IOException ex) {
                            throw new IllegalStateException(ex);
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertThat(filesWithExplicitContext).isSubsetOf(allowedFiles);
        }
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
