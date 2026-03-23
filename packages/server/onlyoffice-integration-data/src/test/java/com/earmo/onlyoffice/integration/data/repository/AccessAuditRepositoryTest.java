package com.earmo.onlyoffice.integration.data.repository;

import com.earmo.onlyoffice.integration.data.DataModuleTestApplication;
import com.earmo.onlyoffice.integration.data.entity.AccessAuditEventEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = DataModuleTestApplication.class)
class AccessAuditRepositoryTest {

  @Autowired
  private AccessAuditEventRepository accessAuditEventRepository;

  @Test
  void shouldSaveAndQueryByDocumentId() {
    accessAuditEventRepository.save(event("evt-1", "doc-a", "tenant-a", Instant.parse("2026-03-23T08:00:00Z")));
    accessAuditEventRepository.save(event("evt-2", "doc-a", "tenant-a", Instant.parse("2026-03-23T09:00:00Z")));

    List<AccessAuditEventEntity> events = accessAuditEventRepository.listByDocumentId("doc-a");

    assertEquals(2, events.size());
    assertEquals("evt-2", events.get(0).getEventId());
    assertEquals("evt-1", events.get(1).getEventId());
  }

  @Test
  void shouldQueryByTenantId() {
    accessAuditEventRepository.save(event("evt-3", "doc-b", "tenant-b", Instant.parse("2026-03-23T10:00:00Z")));

    List<AccessAuditEventEntity> events = accessAuditEventRepository.listByTenantId("tenant-b");

    assertEquals(1, events.size());
    assertEquals("evt-3", events.get(0).getEventId());
  }

  private AccessAuditEventEntity event(String eventId, String documentId, String tenantId, Instant eventTime) {
    AccessAuditEventEntity entity = new AccessAuditEventEntity();
    entity.setEventId(eventId);
    entity.setDocumentId(documentId);
    entity.setTenantId(tenantId);
    entity.setSourceSystem("native");
    entity.setActorUser("user-a");
    entity.setActorName("Alice");
    entity.setEventType("document_created");
    entity.setEventTime(eventTime);
    entity.setEventSource("header");
    entity.setEventResult("success");
    entity.setMessage("测试事件");
    return entity;
  }
}
