package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.context.CurrentAccessContext;
import com.earmo.onlyoffice.integration.model.DocumentSaveStatusResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface DocumentRuntimeEventStreamService {

    default SseEmitter open(
            String documentId,
            DocumentSaveStatusResponse initialStatus,
            Runnable livenessTouch
    ) {
        AccessContext accessContext = CurrentAccessContext.getRequired();
        return open(documentId, accessContext, initialStatus, () -> CurrentAccessContext.runWith(accessContext, livenessTouch));
    }

    SseEmitter open(
            String documentId,
            AccessContext accessContext,
            DocumentSaveStatusResponse initialStatus,
            Runnable livenessTouch
    );

    void publishSaveStatus(String documentId, DocumentSaveStatusResponse status);
}
