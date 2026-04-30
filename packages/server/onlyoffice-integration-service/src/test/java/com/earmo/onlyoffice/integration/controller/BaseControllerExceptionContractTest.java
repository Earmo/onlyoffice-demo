package com.earmo.onlyoffice.integration.controller;

import com.earmo.onlyoffice.integration.exception.AccessContextException;
import com.earmo.onlyoffice.integration.exception.DocumentNotFoundException;
import com.earmo.onlyoffice.integration.exception.DocumentOperationConflictException;
import com.earmo.onlyoffice.integration.exception.LlmApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseControllerExceptionContractTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractController()).build();
    }

    @Test
    void shouldRenderAccessContextErrorAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/missing-context"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_CONTEXT_ERROR"))
                .andExpect(jsonPath("$.message").value("缺少用户上下文"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRenderDocumentNotFoundAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/document-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("文档不存在：doc-a"));
    }

    @Test
    void shouldRenderDocumentConflictAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/document-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_OPERATION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("文档仍有活跃编辑会话"));
    }

    @Test
    void shouldRenderLlmBusinessErrorAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/llm-error"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LLM_RATE_LIMIT"))
                .andExpect(jsonPath("$.message").value("LLM 请求过于频繁"));
    }

    @Test
    void shouldRenderValidationErrorAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("名称不能为空")));
    }

    @Test
    void shouldRenderMalformedJsonAsResponseDto() throws Exception {
        mockMvc.perform(post("/contract/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("请求参数格式错误")));
    }

    @RestController
    @RequestMapping("/contract")
    static class ContractController extends BaseController {

        @PostMapping("/missing-context")
        void missingContext() {
            throw new AccessContextException("缺少用户上下文");
        }

        @PostMapping("/document-not-found")
        void documentNotFound() {
            throw new DocumentNotFoundException("doc-a");
        }

        @PostMapping("/document-conflict")
        void documentConflict() {
            throw new DocumentOperationConflictException("文档仍有活跃编辑会话");
        }

        @PostMapping("/llm-error")
        void llmError() {
            throw new LlmApiException("LLM_RATE_LIMIT", HttpStatus.TOO_MANY_REQUESTS, "LLM 请求过于频繁");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ContractBody body) {
        }
    }

    record ContractBody(@NotBlank(message = "名称不能为空") String name) {
    }
}
