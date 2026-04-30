package com.earmo.onlyoffice.integration.storage;

import com.earmo.onlyoffice.integration.model.RequestContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 负责生成 provider-neutral 的稳定对象键。
 */
@Component
public class StorageKeyFactory {

    public String build(RequestContext requestContext, String documentId, String extension) {
        String tenant = sanitizeSegment(requestContext == null ? null : requestContext.tenantId(), "native");
        String sourceSystem = sanitizeSegment(requestContext == null ? null : requestContext.sourceSystem(), "native");
        String normalizedDocumentId = sanitizeSegment(documentId, "document");
        String normalizedExtension = sanitizeExtension(extension);
        return tenant + "/" + sourceSystem + "/" + normalizedDocumentId + "." + normalizedExtension;
    }

    private String sanitizeSegment(String rawValue, String fallback) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim() : fallback;
        String sanitized = value.replaceAll("[^a-zA-Z0-9_-]", "-");
        return StringUtils.hasText(sanitized) ? sanitized : fallback;
    }

    private String sanitizeExtension(String extension) {
        String raw = StringUtils.hasText(extension) ? extension.trim() : "docx";
        String normalized = raw.startsWith(".") ? raw.substring(1) : raw;
        normalized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return StringUtils.hasText(normalized) ? normalized : "docx";
    }
}
