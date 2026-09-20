-- owner: workforce
-- Search folds accents; source names and email uniqueness remain unchanged.
-- Requires the PostgreSQL contrib unaccent package and CREATE on the database.
-- Fail explicitly if already installed elsewhere; do not silently relocate a shared extension.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA workforce;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
                   WHERE e.extname = 'unaccent' AND n.nspname = 'workforce') THEN
        RAISE EXCEPTION 'PalCheck requires unaccent in the workforce schema; review the existing extension before migrating';
    END IF;
END $$;
