alter table document_metadata
  add column if not exists org_id varchar(64);

alter table document_metadata
  add column if not exists org_name varchar(128);

alter table document_editor_session
  add column if not exists org_id varchar(64);

alter table document_editor_session
  add column if not exists org_name varchar(128);

alter table document_llm_session
  add column if not exists org_id varchar(64);

alter table document_llm_session
  add column if not exists org_name varchar(128);

alter table document_llm_message
  add column if not exists org_id varchar(64);

alter table document_llm_message
  add column if not exists org_name varchar(128);

alter table document_llm_request
  add column if not exists org_id varchar(64);

alter table document_llm_request
  add column if not exists org_name varchar(128);

alter table document_llm_message_variant
  add column if not exists org_id varchar(64);

alter table document_llm_message_variant
  add column if not exists org_name varchar(128);

create index if not exists idx_document_metadata_tenant_org_updated
  on document_metadata (tenant_id, org_id, updated_time desc);

create index if not exists idx_document_metadata_source_org_external
  on document_metadata (tenant_id, org_id, source_system, external_document_id);

create index if not exists idx_document_editor_session_org_actor
  on document_editor_session (tenant_id, org_id, document_id, actor_user, closed_time);

create index if not exists idx_document_llm_session_org_scope
  on document_llm_session (document_id, tenant_id, org_id, actor_user, last_conversation_time desc, created_time desc);

create index if not exists idx_document_llm_message_org_scope
  on document_llm_message (session_id, document_id, tenant_id, org_id, actor_user, created_time asc);

create index if not exists idx_document_llm_request_org_status
  on document_llm_request (session_id, tenant_id, org_id, actor_user, status, started_time desc);

create index if not exists idx_document_llm_message_variant_org_scope
  on document_llm_message_variant (message_id, document_id, tenant_id, org_id, actor_user, variant_index asc);

comment on column document_metadata.org_id is '文档所属组织标识。';
comment on column document_metadata.org_name is '文档所属组织名称。';
comment on column document_editor_session.org_id is '编辑会话所属组织标识。';
comment on column document_editor_session.org_name is '编辑会话所属组织名称。';
comment on column document_llm_session.org_id is 'AI 会话所属组织标识。';
comment on column document_llm_session.org_name is 'AI 会话所属组织名称。';
comment on column document_llm_message.org_id is 'AI 消息所属组织标识。';
comment on column document_llm_message.org_name is 'AI 消息所属组织名称。';
comment on column document_llm_request.org_id is 'AI 请求所属组织标识。';
comment on column document_llm_request.org_name is 'AI 请求所属组织名称。';
comment on column document_llm_message_variant.org_id is 'AI 消息版本所属组织标识。';
comment on column document_llm_message_variant.org_name is 'AI 消息版本所属组织名称。';
