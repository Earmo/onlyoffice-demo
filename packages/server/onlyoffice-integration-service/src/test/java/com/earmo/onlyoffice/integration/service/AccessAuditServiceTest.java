package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.context.AccessContext;
import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import com.earmo.onlyoffice.integration.data.repository.AccessAuditEventRepository;
import com.earmo.onlyoffice.integration.service.impl.AccessAuditServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccessAuditServiceTest {

    @Test
    void shouldRecordDocumentCreatedWithActorInfo() {
        AccessAuditEventRepository repository = mock(AccessAuditEventRepository.class);
        AccessAuditService service = new AccessAuditServiceImpl(repository);

        service.recordDocumentCreated(
                "doc-1",
                new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of(), "header")
        );

        ArgumentCaptor<AccessAuditEventEntity> captor = ArgumentCaptor.forClass(AccessAuditEventEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("doc-1", captor.getValue().getDocumentId());
        assertEquals("tenant-a", captor.getValue().getTenantId());
        assertEquals("user-a", captor.getValue().getActorUser());
        assertEquals("document_created", captor.getValue().getEventType());
        assertEquals("header", captor.getValue().getEventSource());
    }

    @Test
    void shouldRecordCallbackAsSystemEvent() {
        AccessAuditEventRepository repository = mock(AccessAuditEventRepository.class);
        AccessAuditService service = new AccessAuditServiceImpl(repository);

        service.recordCallbackReceived("doc-2", 2);

        ArgumentCaptor<AccessAuditEventEntity> captor = ArgumentCaptor.forClass(AccessAuditEventEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("callback_received", captor.getValue().getEventType());
        assertEquals("system", captor.getValue().getEventSource());
        assertNull(captor.getValue().getActorUser());
        assertTrue(captor.getValue().getMessage().contains("status=2"));
    }

    @Test
    void shouldRecordDocumentArchivedWithActorInfo() {
        AccessAuditEventRepository repository = mock(AccessAuditEventRepository.class);
        AccessAuditService service = new AccessAuditServiceImpl(repository);

        service.recordDocumentArchived(
                "doc-3",
                new AccessContext("tenant-a", "native", "user-a", "Alice", java.util.Map.of(), "header")
        );

        ArgumentCaptor<AccessAuditEventEntity> captor = ArgumentCaptor.forClass(AccessAuditEventEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("document_archived", captor.getValue().getEventType());
        assertEquals("user-a", captor.getValue().getActorUser());
        assertEquals("doc-3", captor.getValue().getDocumentId());
    }
}
