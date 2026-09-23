# Phase 2 Codex review

Date: 2026-09-22 · Reviewer: Codex · Decision owner: Daniel Aguilar
Status: **Implementation and independent review complete; recommended for Daniel’s acceptance.** Phase 3 is not opened. ADR-0024/0025 remain Proposed.

## 1. Executive assessment

The External HRIS Translation slice is complete within its documented simulation scope. Claude’s baseline passed independently before changes: **187 backend tests (72 unit/architecture, 115 PostgreSQL integration)**. Review found concrete boundary, ownership, manager, concurrency and demo-readiness defects; these were corrected without introducing a broker, outbox, job platform, additional domain module or dependency. The frontend now proves the slice against the real backend and identity provider.

Final verification: **192 backend tests, 114 frontend tests, 20 browser tests**, plus typecheck, lint and production build. Recommend **ACCEPT** for Q1, Q2 and Q3. This is a bounded implementation recommendation, not a production-readiness claim or owner acceptance.

## 2. Backend review

Inspected the actual adapter/mapper, normalized contract, public workforce port, import service, repositories, migrations, controllers, permission definitions, audit and integration tests. Existing ownership refusal, stable external identity lookup, per-worker workforce/audit transaction and two-pass structure were retained.

Corrections make the real dev tenant importable, preserve locally editable identity, keep provider mappings in the adapter, repair manager accounting, isolate unexpected worker failures, prevent concurrent duplicate creation, bound pagination and strengthen tenant-qualified metadata references. A summary endpoint supports direct run URLs. The deterministic changed-title scenario is available through the real API only in dev/test.

## 3. Architecture-boundary assessment

**Sound after correction.** `integrations` calls only `workforce.api.WorkforceImportPort`; it never queries workforce tables. Workforce never depends on integrations. ArchUnit’s existing eight new integration rules pass. Provider DTOs remain package-private in `integrations.application.provider`.

Company/department maps and source-vocabulary interpretation moved from orchestration into WorkdayMapper. `NormalizedWorker` carries normalized values and opaque source-field labels for provenance. Displaying those labels is intentional metadata, not provider vocabulary driving workforce logic. Workforce receives a source display name rather than hardcoding Workday. One simulated provider demonstrates the seam; compatibility with a real second provider is not claimed as tested.

## 4. Ownership-model assessment

**Employment/Assignment ownership is sufficient for Phase 2.** Their local PATCH remains an authoritative `409 EXTERNALLY_OWNED`; frontend edit actions disappear when ownership is known, and the client understands that error separately from a stale version conflict.

Person identity is initialized on first import, stays editable locally and is preserved on re-sync. Previously, sync silently overwrote those local edits. Its initial-import provenance is retained rather than falsely refreshed as current source ownership. No per-field policy, Person ownership or ownership-transfer workflow was added. Legal-entity changes and start-date changes that invalidate an existing assignment are rejected for reconciliation rather than silently applied.

## 5. Synchronization-model assessment

Synchronous full pull, one workforce transaction per worker, then a second manager pass. Audit joins each workforce write. Integration sync items and individual lineage upserts follow in independent writes; there is no atomic workforce/receipt/lineage transaction and no durable job runner.

An unexpected worker transaction failure now becomes a safe IMPORT_FAILED item while other workers continue. A fatal orchestration failure attempts to finalize FAILED when the database is available. Database/process loss can still leave RUNNING or incomplete metadata after committed workforce changes; see section 21.

Counts are explicit: processed is source records; failed is outcomes, including additional manager failures on imported records. The six-worker baseline yields **4 created and 3 failed outcomes**, with seven item rows. The UI explains why counts can exceed processed records.

## 6. Idempotency assessment

Idempotency applies to workforce effects through durable `(tenant, source system, external worker)` identity, a unique index and field comparison. Unchanged re-sync creates no duplicate and emits no value-change audit; synchronization timestamps and owned-field provenance are refreshed.

Every POST is a new run, not a replay of a stored response. Phase 1’s request-key **guaranteed retry window of at least 24 hours** is a separate contract and is unchanged. The existing tenant graph advisory lock now also protects import transactions; concurrent identical runs create one workforce identity. Different source snapshots can interleave across workers; no source revision ordering or whole-run lock is implied.

After baseline, TITLE_CHANGE updates only the synthetic analyst’s assignment title. Repeating it yields **0 created, 0 updated, 4 unchanged, 3 failed outcomes**. Selecting baseline again intentionally restores the original external title.

## 7. Manager-resolution assessment

Pass 2 now clears a link when the source manager is null, accepts references only to workers successfully imported in the same pull, and promotes an otherwise UNCHANGED worker to UPDATED for a manager-only change. Created/updated counts are not doubled.

An unresolved reference produces a separate INVALID_MANAGER_REFERENCE outcome and preserves the worker and prior link. Existing self/cycle checks and tenant graph locking remain active. Tests prove manager removal through the port, restoration/counting through re-sync, current-pull linking, unresolved references and unchanged re-sync. Missing workers never imply termination.

## 8. Tenant-isolation assessment

Tenant comes from the authenticated server principal. Systems, runs, items, lineage and workforce lookups are tenant-qualified; cross-tenant run IDs return 404 and lineage returns no matching rows. V8 adds composite run/item/lineage integrity constraints without changing V7 or adding a table. Lineage’s external worker reference is projected from its tenant-qualified sync item.

Existing cross-tenant tests plus the new summary endpoint negative case pass. Optional employment/assignment lineage selectors are independent tenant-scoped selectors, not a new per-person authorization boundary; current permissions remain tenant-wide. PostgreSQL RLS is still deferred under the accepted Phase 1 decision.

## 9. Permission assessment

`VIEW_SYSTEMS`, `VIEW_SYNCS`, `RUN_SYNC`, `VIEW_LINEAGE` remain distinct. HR_ADMIN contains all four; HR_VIEWER contains none. The import port now requires RUN_SYNC rather than unrelated local-create/edit permissions. Backend authorization is authoritative and CSRF remains enforced on POST.

The UI checks permissions separately: hidden navigation for unavailable systems, protected direct URLs, no Run sync without RUN_SYNC, no history without VIEW_SYNCS, and no source fetch/disclosure without VIEW_LINEAGE. Component tests exercise independent combinations; backend tests cover denied reads/writes; browser tests prove the real viewer receives 403. No new roles were created.

## 10. Audit/lineage assessment

System writes use SYSTEM attribution with the actual source display name. Person history renders “Record imported” / “Record synchronized” and “Source: Workday Simulation,” with readable assignment and manager labels. It does not attribute external changes to the triggering HR administrator.

Stored lineage covers six fields: initial Person firstName, lastName, workEmail; synchronized Employment status, employmentType; Assignment title. Employment/Assignment disclosure shows source system, external worker, source/destination field, mapping version, timestamp, ownership and the recorded run link. It loads only on demand. No payload is stored/displayed. Audit is atomic with workforce changes, but has no explicit sync-run foreign key; lineage has the exact run/item reference. This is not full per-field historical causation.

## 11. Q1 recommendation

Exact question from [plan section 14](PHASE_2_IMPLEMENTATION_PLAN.md#14-risks-and-open-questions): “Confirm the ownership granularity choice (Employment/Assignment, not Person; not per-field) as the Phase 2 scope, with the fuller model remaining future work.”

**ACCEPT.** This proves source-of-truth enforcement at the smallest useful boundary. No concrete Phase 2 use case requires per-field policy. Preserving local Person identity on re-sync makes the implementation consistent with the selected granularity. Revisit for a real mixed-ownership use case, not theoretical flexibility.

## 12. Q2 recommendation

Exact question: “Confirm `VIEW_LINEAGE`/`VIEW_SYSTEMS`/`VIEW_SYNCS`/`RUN_SYNC` bundled into `HR_ADMIN` only (not `HR_VIEWER`) for Phase 2.”

**ACCEPT.** Separate permissions express distinct access to metadata and mutation; the existing administrator bundle is enough for the current product. Fixing the port’s unrelated permission dependency preserves that independence. No new role is justified.

## 13. Q3 recommendation

Exact question: “Confirm the systems/sync UI living inside `integrations` rather than standing up the deferred `operations` module now.”

**ACCEPT.** These screens inspect and trigger integration work. Incidents, schedules, topology, SLOs and event exploration do not exist, so an operations module would have no present responsibility. These are reviewer recommendations; only Daniel can accept the ADRs.

## 14. Frontend architecture

`web/src/pages/integrations` follows existing page conventions: typed API client, SystemsPage, SystemPage, SyncPage, small shared run views, and SourceDetails composed into EmploymentCard. Routes are `/systems`, `/systems/:code`, `/syncs/:runId`. Existing TanStack Query, session permissions, API error handling, message catalog and native controls are reused.

The client consumes PalCheck System/SyncRun/SyncItem/Lineage representations, never mock provider payloads. A summary endpoint permits a run to load directly without navigation state. History/items are paginated; lineage fetch is permission-gated and lazy. The sync mutation does not automatically retry after a lost response; the user is told to inspect history before another run.

## 15. UI/UX review

Quiet lists, restrained status text, existing typography/spacing tokens and progressive disclosure preserve the current product language. No charts, oversized metrics or new dashboard destinations. Actual counts and specific failure reasons explain partial success; a manager-link failure explicitly says the worker was imported.

The stored system health placeholder is not exposed as a live “Healthy” claim. The UI shows simulation mode, last synchronization and real run status/history. Last synchronization is not mislabeled last successful synchronization. Native scenario selection describes the deterministic fixture change. Ownership stays beside Employment/Assignment; technical metadata is disclosed only when requested.

Visual review caught and corrected a malformed change-history arrow. Desktop system/detail and narrow dark run/source screens were inspected; source data and accounts are entirely fictional.

## 16. Accessibility review

Component axe assertions and browser axe scans pass with no detected WCAG 2.2 AA violations. New browser coverage uses a keyboard to open source details, verifies forbidden edit actions are absent, and scans desktop/light plus 320px/dark/reduced-motion source disclosure. Additional visual checks scanned Systems, system/history, run and source surfaces across nine desktop/narrow light/dark states with **zero axe violations and no horizontal page overflow**.

Existing full-suite keyboard navigation, dark contrast, forced-colors focus and reduced-motion checks still pass. Semantic headings/lists/description lists, native disclosure, labeled selection, text status and live result announcements are retained. Human screen-reader testing and non-Chrome browser testing remain unverified; automated axe is not a complete accessibility certification.

## 17. E2E coverage

Three tests added in `web/e2e/integrations.spec.ts`, using real Chrome, Vite, Spring Boot, PostgreSQL and Keycloak, without replacing the integration endpoints:

1. HR administrator → Systems → Workday Simulation → baseline sync → partial summary and failure → imported worker → read-only ownership → keyboard source disclosure with actual field names/reference. Includes axe and narrow dark reflow.
2. Baseline → TITLE_CHANGE → one updated worker → changed title and system history → repeat TITLE_CHANGE → no duplicate, same person URL and four unchanged workers. Reloading the saved run URL verifies independent summary retrieval.
3. HR viewer → hidden Systems navigation → denied direct page → actual 403 systems and lineage API responses.

All 17 pre-existing browser flows also pass. The owned edit controls are absent in the real browser; the existing PostgreSQL/HTTP test proves direct PATCH cannot bypass this.

## 18. Test results

| Verification | Result |
|---|---|
| Independent baseline `mvnw.cmd -B -ntp verify` | 187 passed: 72 unit/architecture + 115 integration, before implementation changes |
| Final backend `mvnw.cmd -B -ntp verify` | **192 passed: 72 unit/architecture + 120 integration; 0 failures/errors**, real embedded PostgreSQL; executable JAR repackaged successfully |
| Frontend `npm run typecheck` | Passed |
| Frontend `npm run lint` | Passed |
| Frontend `npm test -- --run` | **114 passed, 9 files** |
| Frontend `npm run build` | Passed |
| `PALCHECK_E2E_CHANNEL=chrome npm run e2e -- --output=.playwright/phase2/e2e-final` | **20 passed**, real local stack |
| Additional visual/axe script | **9 states passed**, desktop/light and narrow/dark; no horizontal overflow |

Five backend regressions were added: preserved local identity/provenance, manager clear/relink accounting, summary/pagination/scenario/read authorization, concurrent duplicate runs, and a real PostgreSQL trigger-induced worker failure proving rollback and continued processing. The dev-seeder test now checks the added fictional reference data and repeat seeding. Ten frontend component tests were added.

During development, two backend assertions failed (a test string encoding error and expected reference-data counts); both were corrected and the complete suite rerun successfully. The first browser run passed 19/20; one existing test reported “Execution context was destroyed” while UI edits were triggering development-server navigation. The unchanged final application passed all 20 without weakening an assertion. Only documentation/Javadoc changed after final application verification. Docker/Compose and hosted CI were not executed in this task.

## 19. Bugs found/fixed

These findings and the additional migration/API scope were recorded in this review before implementation:

| Finding | Correction |
|---|---|
| P2-01: real dev seed lacked provider reference entities | Idempotent synthetic Northwind legal entities and org units in dev tenants; existing people unchanged |
| P2-02: provider maps leaked into orchestration; workforce hardcoded display name | Adapter-local maps and opaque provenance labels; generic source display name through the port |
| P2-03: re-sync overwrote locally editable Person | Initialize once, preserve on re-sync; retain initial Person provenance |
| P2-04: null manager skipped, old-run manager could resolve, manager-only updates counted unchanged | Clear nulls, require current-pull success, promote outcome to UPDATED once |
| P2-05: unexpected worker failure aborted processing; concurrent imports raced | Safe per-worker failure, failed-run finalization where possible, existing tenant transaction lock reused; crash limits documented |
| P2-06: direct run page lacked summary, pagination unbounded, metadata references lacked tenant qualification | Authorized summary API, bounded pagination, new V8 constraints; V7 unchanged |
| P2-07: RUN_SYNC depended on local-edit permissions | Require RUN_SYNC at the public import port |
| P2-08: changed-source fixture usable only in Java tests | Dev/test-gated named scenario through real API/UI; real update E2E |

Also corrected readable system audit reference labels, an unsupported reconciliation no-op, and the malformed history arrow. No unrelated Phase 1 behavior was redesigned.

## 20. Documentation status

Updated README; Phase 2 plan and Proposed ADR-0024/0025; ARCHITECTURE, MODULES, DOMAIN_MODEL, INTEGRATION_ARCHITECTURE, DATA_OWNERSHIP_AND_LINEAGE, ENTERPRISE_SYSTEMS, ROADMAP, SECURITY, UI_SYSTEM and LOCAL_DEVELOPMENT. The API contract records the new summary route, scenario gate, pagination, lineage reference and count semantics. Historical Phase 0 designs are labeled as such; current implementation notices prevent future capabilities being mistaken for shipped behavior.

The current handoff links here. WORKLOG appends the review/completion evidence without rewriting Claude’s earlier entry. No commit or push is part of this task.

## 21. Remaining Phase 2 limitations

- One in-process, synthetic simulation; no real Workday API, certification or network-failure coverage. Code-defined name mappings and one employment per external worker.
- Synchronous full pulls, no scheduling, durable recovery or source-revision ordering. Integration metadata is not atomic with workforce/audit; a crash may leave incomplete run evidence or stale/missing lineage. Later re-sync reconciles owned values/current metadata, not old run history or lost initial Person provenance.
- Entity ownership only; Person stays local. No per-field policy, ownership cutover, automatic merge, inferred termination from absence or complete legal-entity/effective-date reconciliation.
- Six lineage fields, no lineage history/raw payloads, no explicit audit-to-run foreign key. Manager/org/date/country provenance is not claimed complete.
- Tenant-wide permission scope; no finer person-level access. Existing deployment/rate-limit and pre-production security work remains as documented in SECURITY.md.
- Human screen-reader review, other browsers, Docker and hosted CI remain unverified.

These limits are documented scope boundaries, not hidden guarantees. No Phase 3 infrastructure or executable legal rules were introduced.

## 22. Phase 2 completion recommendation

**Consider Phase 2 technically complete and ready for Daniel’s acceptance**, including the documented scope clarifications above. Recommend ACCEPT Q1–Q3 and the Proposed ADRs after owner review. The smallest next milestone is a short owner acceptance and demo walkthrough of baseline → changed title → source/history, then a reviewed repository release/checkpoint. Public hosting would first require the already documented security milestone. Phase 3 needs separate authorization; none has been assumed.
