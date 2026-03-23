alter table document_metadata rename column owner_user_id to owner_user;
alter table document_metadata rename column created_at to created_time;
alter table document_metadata rename column updated_at to updated_time;
alter table document_metadata rename column last_opened_at to last_opened_time;
alter table document_metadata rename column last_callback_at to last_callback_time;
alter table document_metadata rename column last_saved_at to last_saved_time;

drop index if exists idx_document_metadata_tenant_updated;
create index if not exists idx_document_metadata_tenant_updated
  on document_metadata (tenant_id, updated_time);
