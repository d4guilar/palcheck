-- owner: platform (creates the schemas that each module then owns)
-- Schemas are namespaces and migration-ownership markers, not security boundaries: one application role
-- accesses all of them (docs/adr/0018-module-transactions-and-persistence.md).
CREATE SCHEMA IF NOT EXISTS access;
CREATE SCHEMA IF NOT EXISTS workforce;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS platform;
