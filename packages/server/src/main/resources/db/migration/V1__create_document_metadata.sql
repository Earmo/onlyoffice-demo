create table if not exists document_metadata (
  document_id varchar(128) primary key,
  tenant_id varchar(128) not null,
  owner_user_id varchar(128) not null,
  source_system varchar(128) not null,
  external_document_id varchar(256),
  title varchar(512) not null,
  storage_key varchar(512) not null,
  file_type varchar(32) not null,
  document_type varchar(32) not null,
  status varchar(32) not null,
  last_callback_status integer,
  last_error_message varchar(1024),
  created_at timestamp not null,
  updated_at timestamp not null,
  last_opened_at timestamp,
  last_callback_at timestamp,
  last_saved_at timestamp
);

create index if not exists idx_document_metadata_tenant_updated
  on document_metadata (tenant_id, updated_at);

create index if not exists idx_document_metadata_source_external
  on document_metadata (source_system, external_document_id);
