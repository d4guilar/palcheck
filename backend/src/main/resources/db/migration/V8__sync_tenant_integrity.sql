-- owner: integrations. Review P2-05/P2-06, docs/PHASE_2_CODEX_REVIEW.md.
-- Preserve V7 checksums on existing databases; qualify every integration reference by tenant.
ALTER TABLE integrations.sync_run ADD CONSTRAINT sync_run_tenant_id_unique UNIQUE (tenant_id, id);
ALTER TABLE integrations.sync_item ADD CONSTRAINT sync_item_tenant_run_id_unique UNIQUE (tenant_id, sync_run_id, id);
ALTER TABLE integrations.sync_item ADD CONSTRAINT sync_item_tenant_run_fk
    FOREIGN KEY (tenant_id, sync_run_id) REFERENCES integrations.sync_run (tenant_id, id);
ALTER TABLE integrations.field_lineage ADD CONSTRAINT lineage_tenant_item_fk
    FOREIGN KEY (tenant_id, sync_run_id, sync_item_id) REFERENCES integrations.sync_item (tenant_id, sync_run_id, id);
ALTER TABLE integrations.sync_item DROP CONSTRAINT sync_item_failure_reason_valid;
ALTER TABLE integrations.sync_item ADD CONSTRAINT sync_item_failure_reason_valid CHECK (failure_reason IS NULL OR failure_reason IN
    ('UNKNOWN_LEGAL_ENTITY', 'MISSING_REQUIRED_FIELD', 'INVALID_MANAGER_REFERENCE', 'DUPLICATE_EXTERNAL_RECORD',
     'TEMPORARY_PROVIDER_FAILURE', 'IMPORT_FAILED'));
