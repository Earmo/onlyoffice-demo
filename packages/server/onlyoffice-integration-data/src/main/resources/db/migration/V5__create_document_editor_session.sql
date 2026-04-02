create table if not exists document_editor_session (
  session_id varchar(64) primary key,
  document_id varchar(64) not null,
  tenant_id varchar(64) not null,
  actor_user varchar(128) not null,
  actor_name varchar(255),
  opened_time timestamp not null,
  last_seen_time timestamp not null,
  closed_time timestamp,
  created_time timestamp not null default current_timestamp,
  updated_time timestamp not null default current_timestamp
);

create index if not exists idx_document_editor_session_document_active
  on document_editor_session (document_id, closed_time, last_seen_time desc);

create index if not exists idx_document_editor_session_actor_active
  on document_editor_session (actor_user, closed_time, updated_time desc);
