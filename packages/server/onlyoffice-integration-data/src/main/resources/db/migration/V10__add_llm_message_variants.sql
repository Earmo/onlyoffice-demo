-- Phase 17 variant persistence migration.
-- Flyway reruns a failed versioned migration from the beginning after the failed schema-history row is repaired;
-- every DDL/DML statement below is idempotent so a partially applied local H2/PostgreSQL run can be retried safely.

alter table document_llm_message
  add column if not exists active_variant_index integer;

alter table document_llm_request
  add column if not exists variant_id varchar(64);

alter table document_llm_request
  add column if not exists variant_index integer;

create table if not exists document_llm_message_variant (
  variant_id varchar(64) primary key,
  message_id varchar(64) not null,
  session_id varchar(64) not null,
  document_id varchar(64) not null,
  tenant_id varchar(64) not null,
  actor_user varchar(128) not null,
  variant_index integer not null,
  assistant_text text,
  status varchar(32) not null,
  provider_usage_json text,
  provider_meta_json text,
  finish_reason varchar(64),
  error_code varchar(64),
  created_time timestamp not null default current_timestamp,
  updated_time timestamp not null default current_timestamp
);

create unique index if not exists uk_document_llm_message_variant_index
  on document_llm_message_variant (message_id, variant_index);

create index if not exists idx_document_llm_message_variant_scope
  on document_llm_message_variant (message_id, document_id, tenant_id, actor_user, variant_index asc);

create index if not exists idx_document_llm_message_variant_session
  on document_llm_message_variant (session_id, document_id, tenant_id, actor_user, variant_index asc);

create index if not exists idx_document_llm_request_variant
  on document_llm_request (assistant_message_id, variant_id, variant_index);

insert into document_llm_message_variant (
  variant_id,
  message_id,
  session_id,
  document_id,
  tenant_id,
  actor_user,
  variant_index,
  assistant_text,
  status,
  provider_usage_json,
  provider_meta_json,
  finish_reason,
  error_code,
  created_time,
  updated_time
)
select
  message_id,
  message_id,
  session_id,
  document_id,
  tenant_id,
  actor_user,
  0,
  assistant_text,
  status,
  provider_usage_json,
  provider_meta_json,
  finish_reason,
  error_code,
  created_time,
  created_time
from document_llm_message message
where message.role = 'assistant'
  and not exists (
    select 1
    from document_llm_message_variant variant
    where variant.message_id = message.message_id
      and variant.variant_index = 0
  );

update document_llm_message
set active_variant_index = 0
where role = 'assistant'
  and active_variant_index is null;

comment on column document_llm_message.active_variant_index is '当前 assistant message 展示和写回使用的 variantIndex；user message 保持 null。';
comment on column document_llm_request.variant_id is '本次 LLM 请求生成的 assistant variant 主键，用于审计和 SSE/回查契约。';
comment on column document_llm_request.variant_index is '本次 LLM 请求生成的 assistant variant 序号。';
comment on table document_llm_message_variant is 'assistant message 的多版本回复明细，variant_index=0 兼容历史 assistant_text。';
