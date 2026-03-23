create table if not exists access_audit_event (
  event_id varchar(64) primary key,
  document_id varchar(128) not null,
  tenant_id varchar(128) not null,
  source_system varchar(128) not null,
  actor_user varchar(128),
  actor_name varchar(255),
  event_type varchar(64) not null,
  event_time timestamp with time zone not null,
  event_source varchar(64) not null,
  event_result varchar(64) not null,
  message text
);

create index if not exists idx_access_audit_event_document_time
  on access_audit_event (document_id, event_time desc);

create index if not exists idx_access_audit_event_tenant_time
  on access_audit_event (tenant_id, event_time desc);
