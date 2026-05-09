create table if not exists document_llm_session (
  session_id varchar(64) primary key,
  document_id varchar(64) not null,
  tenant_id varchar(64) not null,
  actor_user varchar(128) not null,
  title varchar(255),
  last_snapshot_text text,
  last_snapshot_is_empty boolean not null default true,
  last_heading_id varchar(128),
  last_heading_text varchar(255),
  created_time timestamp not null default current_timestamp,
  updated_time timestamp not null default current_timestamp,
  archived_time timestamp
);

create table if not exists document_llm_message (
  message_id varchar(64) primary key,
  session_id varchar(64) not null,
  document_id varchar(64) not null,
  tenant_id varchar(64) not null,
  actor_user varchar(128) not null,
  role varchar(32) not null,
  message_text text,
  assistant_text text,
  snapshot_text text,
  snapshot_is_empty boolean not null default true,
  heading_id varchar(128),
  heading_text varchar(255),
  include_heading boolean not null default false,
  status varchar(32) not null,
  retry_of_message_id varchar(64),
  provider_usage_json text,
  provider_meta_json text,
  finish_reason varchar(64),
  error_code varchar(64),
  created_time timestamp not null default current_timestamp
);

create table if not exists document_llm_request (
  request_id varchar(64) primary key,
  session_id varchar(64) not null,
  document_id varchar(64) not null,
  tenant_id varchar(64) not null,
  actor_user varchar(128) not null,
  user_message_id varchar(64) not null,
  assistant_message_id varchar(64) not null,
  provider_request_id varchar(128),
  status varchar(32) not null,
  cancel_requested boolean not null default false,
  cancel_source varchar(64),
  started_time timestamp not null,
  finished_time timestamp
);

create index if not exists idx_document_llm_session_scope
  on document_llm_session (document_id, tenant_id, actor_user, updated_time desc);

create index if not exists idx_document_llm_message_session_created
  on document_llm_message (session_id, created_time asc);

create index if not exists idx_document_llm_request_scope_status
  on document_llm_request (session_id, tenant_id, actor_user, status, started_time desc);
