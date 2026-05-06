package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.model.RequestContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 负责生成 provider-neutral 的稳定对象键。
 *
 * <p>对象键不暴露本地路径或具体 bucket 结构，只表达业务维度：租户、来源系统和文档标识。
 * 这样同一个文档在 local、MinIO、COS 等 provider 间迁移时，可以保持统一定位方式。
 */
@Component
public class StorageKeyFactory {

    /**
     * 根据请求上下文、文档 ID 和扩展名生成标准对象键。
     *
     * <p>生成格式固定为 {@code tenant/sourceSystem/documentId.extension}。
     * 所有路径片段都会经过安全字符清洗，避免 provider 实现处理非法路径字符或路径穿越片段。
     *
     * @param requestContext 当前请求上下文，可为空；为空时租户和来源系统使用 {@code native}
     * @param documentId 文档业务 ID；为空或清洗后为空时使用 {@code document}
     * @param extension 文件扩展名，可带或不带前导点；为空或清洗后为空时使用 {@code docx}
     * @return provider-neutral 的稳定对象键
     */
    public String build(RequestContext requestContext, String documentId, String extension) {
        String tenant = sanitizeSegment(requestContext == null ? null : requestContext.tenantId(), "native");
        String sourceSystem = sanitizeSegment(requestContext == null ? null : requestContext.sourceSystem(), "native");
        String normalizedDocumentId = sanitizeSegment(documentId, "document");
        String normalizedExtension = sanitizeExtension(extension);
        return tenant + "/" + sourceSystem + "/" + normalizedDocumentId + "." + normalizedExtension;
    }

    /**
     * 清洗对象键中的路径片段。
     *
     * <p>路径片段只保留大小写字母、数字、下划线和中划线，其他字符统一替换为中划线。
     * 该规则同时兼容本地文件系统路径和对象存储 key。
     *
     * @param rawValue 原始片段值
     * @param fallback 原始值为空或清洗后为空时使用的兜底值
     * @return 可安全拼入对象键的路径片段
     */
    private String sanitizeSegment(String rawValue, String fallback) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim() : fallback;
        String sanitized = value.replaceAll("[^a-zA-Z0-9_-]", "-");
        return StringUtils.hasText(sanitized) ? sanitized : fallback;
    }

    /**
     * 清洗文件扩展名。
     *
     * <p>扩展名会去掉前导点、转换为小写，并只保留小写字母和数字，避免生成
     * {@code .docx/../x} 这类会污染对象键或本地路径的后缀。
     *
     * @param extension 原始扩展名
     * @return 标准化后的扩展名，不包含前导点
     */
    private String sanitizeExtension(String extension) {
        String raw = StringUtils.hasText(extension) ? extension.trim() : "docx";
        String normalized = raw.startsWith(".") ? raw.substring(1) : raw;
        normalized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return StringUtils.hasText(normalized) ? normalized : "docx";
    }
}
