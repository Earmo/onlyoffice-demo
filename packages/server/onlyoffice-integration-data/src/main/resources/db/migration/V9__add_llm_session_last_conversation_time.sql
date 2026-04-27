alter table document_llm_session
  add column if not exists last_conversation_time timestamp;

update document_llm_session
set last_conversation_time = updated_time
where last_conversation_time is null;

drop index if exists idx_document_llm_session_scope;

create index if not exists idx_document_llm_session_scope
  on document_llm_session (document_id, tenant_id, actor_user, last_conversation_time desc, created_time desc);

comment on column document_llm_session.last_conversation_time is '会话最近一次用户发起对话的时间，用于会话列表排序。';
