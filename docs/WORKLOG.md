# Worklog

Append-only log of meaningful work by every contributor (Daniel, Claude Code, OpenAI Codex). Rules are in [../AGENTS.md](../AGENTS.md) section 8 and [AI_COLLABORATION.md](AI_COLLABORATION.md) section 8:
never edit or reorder earlier entries (correct yourself with a new entry that references the old one); one entry is one contiguous section appended at the end; timestamps come from the system clock; write for a reader with no chat context.

Entry format:

```
## YYYY-MM-DD HH:MM — CLAUDE | CODEX
### Task
### Work completed
### Decisions made
### Assumptions
### Open questions
### Risks
### Suggested next step
```

---

## 2026-09-19 11:24 — CLAUDE

### Task

Phase 0 first assignment from Daniel's brief ([../MASTER_PROJECT_BRIEF.md](../MASTER_PROJECT_BRIEF.md)): read and critique the brief; propose the Phase 0 repository structure; define modules; define the HRIS translation architecture, ownership model, Latin America localization
architecture, and Legal Concept Registry; define the Enterprise Systems area; produce diagrams and initial ADRs; write Phase 0 documentation, this worklog, and `docs/CODEX_HANDOFF.md`; then stop. **Documentation and diagrams only; no implementation.**

### Work completed

Created 52 Markdown files (the folder previously held only an empty `hello.txt`, which was not touched). No code, schema, endpoints, UI, Docker, CI, dependency manifests, or `git init`.

- **Root:** [README.md](../README.md), [MASTER_PROJECT_BRIEF.md](../MASTER_PROJECT_BRIEF.md) (Daniel's brief verbatim plus an Amendments table), [AGENTS.md](../AGENTS.md) (single source of shared agent rules), [CLAUDE.md](../CLAUDE.md) (imports `AGENTS.md`, adds Claude's role).
- **Core docs (`docs/`):** [PRODUCT_VISION.md](PRODUCT_VISION.md), [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) (assignment step 2), [ARCHITECTURE.md](ARCHITECTURE.md) (includes the Phase 0 and target repository structure, step 3), [MODULES.md](MODULES.md) (step 4),
  [DOMAIN_MODEL.md](DOMAIN_MODEL.md), [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) (step 5), [DATA_OWNERSHIP_AND_LINEAGE.md](DATA_OWNERSHIP_AND_LINEAGE.md) (step 6 and lineage), [ENTERPRISE_SYSTEMS.md](ENTERPRISE_SYSTEMS.md) (step 9),
  [SECURITY.md](SECURITY.md), [UI_SYSTEM.md](UI_SYSTEM.md), [ROADMAP.md](ROADMAP.md), [AI_COLLABORATION.md](AI_COLLABORATION.md), this worklog, [CODEX_HANDOFF.md](CODEX_HANDOFF.md).
- **Localization (`docs/localization/`):** [README.md](localization/README.md), [LATAM_STRATEGY.md](localization/LATAM_STRATEGY.md), [COUNTRY_POLICY_ARCHITECTURE.md](localization/COUNTRY_POLICY_ARCHITECTURE.md) (step 7), [LEGAL_CONCEPT_REGISTRY.md](localization/LEGAL_CONCEPT_REGISTRY.md) (step 8);
  Costa Rica: [README.md](localization/costa-rica/README.md), [GLOSSARY.md](localization/costa-rica/GLOSSARY.md), [LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md), [RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md). Contain no legal rules.
- **Diagrams (`docs/diagrams/`, 8 files, 15 Mermaid blocks; step 10):** system context, container, module map, HRIS integration, event processing, country policy, data lineage, operations topology.
- **ADRs (`docs/adr/`, step 11):** [index](adr/README.md) plus ADR-0001 to ADR-0017, all **Proposed**.

Verification (documentation-only, scripted, run at the end of the task; script kept in the session scratchpad, not in the repo): see "Verification result" below.

### Decisions made

- **Daniel decided (this session): standalone-first** as the primary design driver, with the enterprise-overlay mode preserved as a seam. Recorded in [ADR-0017](adr/0017-primary-product-mode.md) and Amendment A1. The reviewer's overlay-first recommendation is recorded once in [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) section 9 and not relitigated.
- **Proposed by Claude (all Proposed, none Accepted):** modular monolith with 7 modules + 2 deferred; PostgreSQL with pooled tenancy; separation of ownership policy from provenance; Adapter/Mapper/`NormalizedWorker` pipeline with capability flags; internal vs. published events, at-least-once + idempotent processing with `KEY`/`LOOKUP`/`NONE` destinations; explicit transactional outbox;
  **no broker until measurable exit criteria** ([ADR-0008](adr/0008-broker-none-yet.md)); five-entity Legal Concept Registry with verification levels and gates, content as reviewed files; capability-based country policy packs with a second-instance rule; permission x scope authorization; computed (not stored) lineage; an Operations Console derived from real flows.
- **Repository structure:** docs only in Phase 0; the brief's code-side directories are deferred with reasons ([ARCHITECTURE.md](ARCHITECTURE.md) section 11). `DATA_LINEAGE.md` became `DATA_OWNERSHIP_AND_LINEAGE.md`; the four Costa Rica topic files became one `RESEARCH_BACKLOG.md` of questions.
- **Terminology:** never use bare "organization"; use Tenant, Legal Entity, or Org Unit.
- **Non-goal made explicit:** PalCheck does not compute pay, in any mode.

### Assumptions

- Daniel's answer "Standalone-first" to the single clarifying question is a final decision for planning purposes.
- The brief's Interview Readiness list is a required coverage target; mapped in [ROADMAP.md](ROADMAP.md).
- Spanish terms *Patrono*, *Planilla*, and the CCSS role wording in the glossary are **Unverified**; they are orientation only.
- Library, standard, and vendor statements in the docs (Spring Modulith registry behavior, React Aria/Radix capabilities, Redpanda licensing, PostgreSQL features, RFC 9457) are from general knowledge and are marked `Unverified` where they appear.
- No employer-confidential knowledge was used. Vendor-related content is limited to what the brief itself states; the Workday reference HRIS is described as a Workday-shaped mock with no fidelity claim.

### Open questions

Consolidated, with owners, in [CODEX_HANDOFF.md](CODEX_HANDOFF.md) section 2. The ones that most need Daniel: acceptance of the ADRs; **BIZ-02** (review of any employer invention-assignment, outside-activity, or conflict-of-interest obligations before this becomes a business; flagged, not legal advice); **LR-01** (who will be the professional reviewer); **BIZ-05**
(trademark clearance for "PalCheck", unchecked); whether Daniel accepts the "does not compute pay" non-goal; who implements which module in Phase 1.

### Risks

- Scope creep from standalone-first toward a payroll or full-HRIS build.
- One developer carrying seven modules, a UI, and an operations plane; mitigated only by keeping Phase 1 a thin vertical slice.
- Legal content: the glossary and registry could be read as authoritative despite banners. Mitigation is the status gates and disclaimers; the residual risk is human.
- Seventeen ADRs and a five-entity registry may themselves be over-engineering; Codex is asked to challenge this.
- **Mermaid diagrams were not machine-validated** (no local Mermaid tooling; nothing installed). They were reviewed by eye and may contain syntax errors that only a renderer will reveal.
- Facts about third-party libraries and standards were not checked against current documentation.

### Suggested next step

1. **Codex** reviews Phase 0 per [CODEX_HANDOFF.md](CODEX_HANDOFF.md) and appends a `CODEX` worklog entry with findings (challenge priorities are in that file's section 3).
2. **Daniel** reads [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) and the ADR index, answers the open questions above, and accepts, amends, or rejects the ADRs.
3. Only then does Daniel decide whether to open Phase 1 (spikes S1 to S8, then the hire-to-provision slice per [ROADMAP.md](ROADMAP.md)). In parallel, Costa Rica **P1 research** ([localization/costa-rica/RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md)) can start at any time and needs no code.

### Verification result

Recorded after the last file was written (2026-09-19): 52 Markdown files; every relative link and heading anchor resolves (0 errors); code fences balanced; every ADR has Status, Date, Context, Decision, Alternatives, Consequences (positive and negative), Reconsider When, Related Decisions, and an interview line;
no scaffolding or code files; no `.git`; `hello.txt` unchanged (0 bytes); no percentages, currency amounts, or day/month counts in `docs/localization`; no partnership or certification claims. Mermaid syntax not machine-validated (see Risks).

## 2026-09-19 13:17 — CODEX

### Task

Perform the second senior engineering review of Claude's Phase 0 architecture for implementation, reliability, backend/platform, integration, testing and developer experience. Produce the requested review and proposals, update relevant documentation and stop before implementation.

### Work completed

- Read all 52 original Markdown documents, including the original brief and Amendments, all 17 original ADRs, localization research policy/backlog and all 15 original Mermaid blocks. Followed the documented replacement of DATA_LINEAGE.md with DATA_OWNERSHIP_AND_LINEAGE.md.
- Created [CODEX_ARCHITECTURE_REVIEW.md](CODEX_ARCHITECTURE_REVIEW.md), with the requested 16 sections, nine findings, all-original-ADR disposition, dependency inventory, module/API/data/transaction boundaries, database/history/migration plan, import checkpoint and ownership-cutover protocol, outbox/idempotency/retry/replay design, tests, local development, CI/deployment, security, localization and feature prioritization.
- Created Proposed [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md), [ADR-0020](adr/0020-foundation-first-delivery-plan.md), and [ADR-0021](adr/0021-registry-history-and-behavior-gates.md). Created [diagram 09](diagrams/09-proposed-transaction-and-delivery.md) and [review handoff](CODEX_REVIEW_HANDOFF_2026-09-19.md).
- Added review notices to architecture, modules, domain, integrations, ownership/lineage, security, enterprise systems, roadmap and the registry/country-policy architecture documents; linked the review from README and indexed the new ADRs. The original proposals remain visible pending Daniel's decisions; no original ADR or brief text was changed.
- Verified relevant public primary documentation for PostgreSQL, Spring transaction propagation/Modulith/OIDC login, ArchUnit, Flyway, Testcontainers, Workday SOAP documentation and selected UI/standards claims. Citations and limits are in the review. No legal research or live vendor access was performed.
- Documentation checks before this append: 59 Markdown files, 17 Mermaid blocks, zero broken local links/heading anchors, balanced fences, all 21 ADRs with required sections and Proposed status; no non-Markdown files in the workspace inventory. No code, schema, migrations, endpoints, mocks, dependencies, infrastructure or CI were created; no Git repository was initialized. Mermaid inspected as text only, not rendered.

### Decisions made

No architecture decision was accepted and no phase was opened. KEEP recommendations: standalone-first, modular monolith, Java/Spring and React/TypeScript, PostgreSQL, no broker, provider isolation, workforce ownership, synthetic-only data and legal gates. Proposals: explicit shared local transaction participants and technical messaging ownership; stable business-effect identity and ambiguity-aware recovery; smaller workforce foundation before provisioning; reviewed historical registry eligibility distinct from withdrawal. Full KEEP/MODIFY/DEFER/REMOVE inventory is in the review.

### Assumptions

Assumption: Daniel's standalone-first planning decision in Amendment A1 remains binding. Initial workflows can use minimal synthetic application record IDs without national-ID fields, legal eligibility claims or payroll calculation. Retry numbers in the review are proposed simulator engineering settings, not vendor limits or legal requirements. Package/data layouts are conceptual plans only.

### Open questions

Daniel decisions D1–D5 in review §16: module/transaction/persistence changes; delivery safety; smaller foundation and browser session direction; registry history gates; inbound/lineage refinements. Claude should respond accept/fix, decline/reason or escalate for TX-01, EVT-01, IMP-01, SEC-01, LOC-01, SCP-01, LIN-01, DAT-01 and REF-01. If approved, reconcile baseline documentation and phase numbering before implementing. Opening Phase 1 remains a separate explicit decision in ROADMAP.md.

### Risks

No runtime behavior, library integration, crash recovery, performance, RLS/pool safety or Mermaid rendering was tested. Proposed alternatives and original baseline coexist explicitly pending review; neither should be mistaken for an accepted implementation contract. External key/lookup capabilities limit achievable effect guarantees. Legal facts remain UNVERIFIED / RESEARCH REQUIRED and software behavior NOT IMPLEMENTED. Real-data retention/erasure remains a separate researched decision.

### Suggested next step

Claude reviews the findings and ADR-0018–0021 using [CODEX_REVIEW_HANDOFF_2026-09-19.md](CODEX_REVIEW_HANDOFF_2026-09-19.md); Daniel accepts, amends or declines D1–D5. Reconcile the documents after his decisions, then request/record a separate Phase 1 opening if desired. Stop here at documentation.

Final preservation check after the worklog append: zero link/anchor errors; original MASTER_PROJECT_BRIEF.md and all 17 original ADRs retain their baseline SHA-256 hashes; the original 8,405-byte WORKLOG.md prefix retains its baseline hash. Seven new Markdown files were added. The pre-existing empty hello.txt was present in the initial hash inventory but absent at final verification. No review edit or command targeted/deleted it; the cause is unknown. It was not restored, to avoid undoing an external workspace change.

## 2026-09-19 14:11 — CLAUDE

### Task

Open Phase 1 (Authenticated Workforce Foundation) as directed by Daniel: read the Phase 0 material and the Codex architecture review, reconcile the documentation with the six approved implementation-review refinements, write the Phase 1 implementation plan, and record the start of Phase 1. Implementation steps 1 and 2 of the plan only; no application code yet.

### Work completed

- Read all mandatory documents, the full [Codex architecture review](CODEX_ARCHITECTURE_REVIEW.md), the review handoff, ADR-0018 to ADR-0021 and diagram 09. Noted that Daniel or Codex removed the empty `hello.txt` (already recorded by Codex); the workspace has no Git repository.
- **ADRs:** added acceptance records to [ADR-0018](adr/0018-module-transactions-and-persistence.md), [ADR-0019](adr/0019-durable-delivery-and-effect-identity.md), [ADR-0020](adr/0020-foundation-first-delivery-plan.md), [ADR-0021](adr/0021-registry-history-and-behavior-gates.md) (status "Accepted in part", per-decision ACCEPTED / PROVISIONAL / Proposed); added amendment notes to ADR-0001, 0006, 0007, 0012 (original text preserved); created [ADR-0022](adr/0022-phase1-technical-baseline.md) (Proposed); updated the [ADR index](adr/README.md).
- **Reconciliation:** replaced the "Second-review notice (Proposed)" banners with reconciliation notices in ARCHITECTURE, MODULES, DOMAIN_MODEL, INTEGRATION_ARCHITECTURE, DATA_OWNERSHIP_AND_LINEAGE, SECURITY, ENTERPRISE_SYSTEMS, COUNTRY_POLICY_ARCHITECTURE and LEGAL_CONCEPT_REGISTRY; added the accepted dependency direction ([MODULES.md](MODULES.md) 2a, [diagram 03](diagrams/03-module-map.md)); marked superseded statements inline (ARCHITECTURE rule 4, INTEGRATION 5.1 and 5.4); bannered diagram 05 and updated diagram 09's status; restructured [ROADMAP.md](ROADMAP.md) (Phase 1 opened, provisioning and overlay kept as later milestones without renumbering); updated AGENTS.md (phase gate, reading list, status vocabulary, worklog format), CLAUDE.md, README status, AI_COLLABORATION division of labor, and the CODEX_HANDOFF pointer.
- **Plan:** created [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) (scope, package structure, table ownership, domain model, persistence, idempotency, authentication, authorization, API, logging, frontend, tests, dependencies, steps, risks).
- Environment findings recorded in the plan: JDK 17.0.12, Node 24, npm 11, Git 2.50, PostgreSQL 17 running as a local Windows service; **no Docker, no Maven**; network reachable to Maven Central and npm. Latest stable Spring Boot on Maven Central is 4.1.1.

### Decisions made

- Daniel's six refinements are recorded as accepted (see the acceptance records): atomic state + audit (+ future durable publication) in one local transaction; outbox is technical `messaging`, never owned by `integrations`; business operation identity separate from event identity; failure recovery is an accepted requirement for future integration work; legal applicability vs. withdrawal; smaller milestones. Only state + audit is implemented in Phase 1; no publication, outbox, broker or events.
- Phase 1 technical baseline (ADR-0022, provisional): Maven wrapper + packages + ArchUnit (no Spring Modulith); Spring Boot 4.1.x on Java 17; Spring JDBC with explicit tenant-scoped repositories (no JPA); backend-managed OIDC session with CSRF; Keycloak as a development identity provider; permission-based authorization with tenant-wide scope; version-based optimistic concurrency with `expectedVersion`; command idempotency with `Idempotency-Key` (required on create); append-only audit; row-level security deferred; embedded real PostgreSQL for tests because no container runtime exists on this machine.
- Milestone order is foundation, then provisioning, then overlay; later milestones are not renumbered.

### Tests

None yet: documentation-only steps. Link check across all Markdown files after the edits: 0 broken relative links.

### Assumptions

- Daniel's approval of the six refinements covers exactly the decisions marked ACCEPTED in each acceptance record; decisions marked PROVISIONAL are followed under his implementation direction (database ownership, Docker Compose, OIDC, Keycloak acceptable) and await confirmation.
- Employment `status` (`PENDING`/`ACTIVE`/`ENDED`) and `employmentType` (`FULL_TIME`/`PART_TIME`) are software placeholders, not legal classifications.
- `Idempotency-Key` is required on create only.

### Open questions

Q1 to Q3 in the plan (section 16): confirm ADR-0022 provisional choices; confirm required-on-create idempotency and 24-hour expiry; confirm the placeholder employment vocabularies. Also unresolved from Phase 0: BIZ-02, LR-01, BIZ-05.

### Risks

Spring Boot 4 / Spring Security 7 / Jackson 3 are newer than the APIs I know best; Compose and the Keycloak realm cannot be run on this machine (no Docker) and will be labelled unverified until Daniel runs them; documentation now contains original and amended text side by side, deliberately, with amended statements marked.

### Suggested next step

Step 3 of the plan: create the backend (Maven wrapper, Spring Boot skeleton, Flyway, health), the frontend (Vite + React + TypeScript), Docker Compose and Keycloak realm files, `.env.example`, CI skeleton, and verify the builds and health endpoint locally.

## 2026-09-19 14:58 — CLAUDE

### Task

Phase 1 implementation steps 3 to 9: backend, frontend and local-development foundation; authentication and tenant context; workforce persistence; people read experience; create and edit commands; audit; concurrency and idempotency. Frontend feature work (steps 10 to 12) is not started in this entry.

### Work completed

- **Foundation:** Maven wrapper (Maven 3.9.16) and `backend/` Spring Boot 4.1.1 on Java 17; Flyway with one ordered stream (V1 schemas, V2 access, V3 workforce, V4 audit, V5 platform idempotency); health probes (liveness, readiness including the database); `web/` Vite + React + TypeScript project with Vitest, Testing Library and axe checks; `docker-compose.yml` (PostgreSQL 17, Keycloak 26.7.4), `.env.example`, Keycloak development realm with four synthetic users, `.gitignore`, and `.github/workflows/ci.yml`.
- **Authentication and tenant context (`access`):** OIDC authorization-code login with a backend-managed session (`oauth2Login`), CSRF with the SPA cookie/header pattern, JSON logout URL for RP-initiated logout, JSON problem responses for 401/403, trusted principal-to-tenant mapping on (issuer, subject) resolved per request, permission model and roles (`HR_ADMIN`, `HR_VIEWER`), `GET /api/session`.
- **Workforce (`workforce`):** domain model (Person, Employment, Assignment, Legal Entity, Org Unit) with shared validation for create and patch, tenant-qualified composite foreign keys, JDBC repositories that all take a tenant, directory search and paging, employment views; endpoints for list, detail, employments, audit history, create, and three PATCH resources; reference lists.
- **Audit (`audit`):** append-only `audit_entry` (database trigger rejects update, delete, truncate), recorder that requires an existing transaction, tenant-scoped history read.
- **Platform:** RFC 9457 problem details with one error model, request/correlation-ID filter with strict header validation and one access-log line per request, idempotency service (receipt reserved inside the business transaction), scheduled purge.
- **Development seed:** `dev`-profile seeder creating fictional tenants Acme Latin America (13 people, 2 legal entities, 6 org units, managers, an ended and a pending employment, one edit with history) and Globex Andina (4 people), through the real services under real seeded principals.
- Plan updated with statuses; nothing outside the plan was built.

### Decisions made

- **Vendor-neutral OIDC registration id** (`oidc`), so no PalCheck path or type names Keycloak.
- **Field rules live in the domain**, not in Bean Validation; request bodies are read field by field so every problem is reported at once and unknown fields (including a client-supplied `tenantId`) are rejected.
- **Cross-tenant ids are reported as NOT_FOUND**, indistinguishable from nonexistent ids; cross-tenant references in a request body are validation errors. `TENANT_ACCESS_DENIED` is for an identity without an active PalCheck principal or tenant.
- **Audit history key is the Person**, so one screen shows person, employment and assignment changes; entries written by one command share a `change_set_id`.
- **Idempotency:** required on create, optional on patch; scope tenant + principal + operation + key; 422 for key reuse with a different request; receipts hold a result reference, not personal data; expiry 24 hours.
- **Dependencies added** (all recorded in plan section 14): Spring Boot starters, PostgreSQL driver, Flyway; test-only embedded PostgreSQL 17 binaries, ArchUnit, mock-oauth2-server; frontend: React, React Router, TanStack Query, react-intl, Vite, Vitest, Testing Library, vitest-axe, Playwright (installed, not yet used).
- **Testcontainers not used**: no container runtime on this machine; real PostgreSQL 17 binaries run in-process instead (ADR-0022 decision 10).

### Tests

Backend: **91 integration tests + 15 architecture tests, all passing** (`mvn verify`), against real PostgreSQL 17. Coverage of the plan's list: successful create/update; validation rejection; **tenant isolation on every endpoint** plus database-level cross-tenant rejection; authorization matrix (anonymous, no role, viewer, admin) for every endpoint; not found; **concurrent modification** (sequential and parallel, six rounds); **audit atomicity** (forced audit failure leaves no person, employment, assignment, audit or receipt row, for create and edit); append-only audit; **idempotent create retry**, key reuse with a different request, failed request does not consume the key, per-principal and per-tenant scope, expiry, concurrent same-key requests, concurrent same-email race; real OIDC login flow with an in-process identity provider; log canary (personal values never appear in logs while operation/result/requestId/tenantId/userId do); health probes; seed data. Frontend: one smoke test (shell renders, no axe violations); real frontend tests come with steps 10 to 12.

### Assumptions

- Java 17 is the target (installed LTS); Spring Boot 4.1.1 was chosen because it is the newest stable line on Maven Central (3.5 is out of open-source support in this timeline).
- The Keycloak realm JSON and `docker-compose.yml` are correct for Keycloak 26.7.4 and Docker; **neither has been executed** (no container runtime here). Placeholder substitution in the realm import (`${PALCHECK_OIDC_CLIENT_SECRET}`) is assumed to work and will be checked when Keycloak is run locally (plan risk R2).
- PostgreSQL server logs can contain rejected values (constraint violations echo keys); they are treated as sensitive infrastructure logs, outside the application-log guarantee.

### Open questions

Q1 to Q3 in the plan (confirm ADR-0022 provisional choices, required-on-create idempotency, placeholder employment vocabularies). New: should `Idempotency-Key` receipts expire after 24 hours or another period? Should authorization run before request-body validation (today a malformed body from a forbidden caller gets 400 before 403)?

### Risks

- Compose, the realm import and the browser end-to-end flow are unverified on this machine until Keycloak and PostgreSQL are run (next steps will try a local Keycloak distribution).
- Spring Boot 4 / Security 7 / Jackson 3 are new; tests pass but production behavior outside tested paths is unproven.
- Row-level security is deferred; isolation rests on application checks, composite foreign keys and tests.
- Directory search uses substring matching without an index; fine for demo scale, needs review before large tenants.

### Suggested next step

Steps 10 to 12: build the React application (sign-in, People list, record detail, create, edit sections, change history, conflict and error states) against this API with keyboard-accessible, accessible-by-default components, frontend tests, then run the whole stack locally (PostgreSQL, Keycloak, backend, frontend) and add the browser end-to-end flow.

## 2026-09-19 19:25 — CLAUDE

### Task

Phase 1 steps 10 to 13: frontend create, edit and detail; accessibility and error-handling pass; focused integration and end-to-end tests; documentation cleanup and Phase 1 review. Phase 1 is now fully implemented and awaits Daniel's review.

### Work completed

- **Frontend (`web/`)**: React + TypeScript + Vite application with sign-in and session states (unauthenticated, no workspace, error, expired), People (search, table, paging, loading, empty and error states), New person (identity, employment, assignment in one atomic save), and the person record (identity, employment, assignment and change history; each part editable on its own with its own revision, conflict banner with a reload action, forbidden and not-found states). Semantic design tokens (light, dark, high contrast, forced colors, reduced motion and transparency), one English message catalog, no component library.
- **Backend additions**: unit tests for the domain rules, patch semantics, request fingerprinting, role matrix and correlation-header handling (43 tests; the plan promised a unit layer that did not exist yet); ECS JSON logging as the default outside `dev` and `test`.
- **Tests**: frontend component suite through a fake network at the `fetch` boundary; Playwright suite against the real stack (real Chrome, Keycloak, PostgreSQL, backend), including an in-browser axe-core scan.
- **Infrastructure**: `.github/workflows/e2e.yml` (manual browser suite), `playwright.config.ts`.
- **Documentation**: new [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md); README rewritten for the current state; plan definition-of-done table filled with evidence, sections 9, 10, 12 to 16 updated and a new section 17 (findings and known limitations); SECURITY section 17 rewritten to what is actually implemented (and corrected: no rate limiting, no request-size limit, no CI scanning, history reads not audited); ARCHITECTURE section 11 now shows the real repository; UI_SYSTEM reconciliation notice; ADR-0022 implementation outcome; implementation notes on ADR-0003, 0009, 0013; ADR index, ROADMAP and AGENTS updated.

### Decisions made

- **Session probe is exempt from the "session ended" signal.** Otherwise a `401` from the session check re-triggers the session check forever (see Tests).
- **Focus policy**: skip link first on a fresh load; focus moves to the page heading only on a real navigation (waiting for the heading if the page is still loading), never on first load, redirects, paging or search. The rule is implemented once, in the application shell.
- **Failure summary focuses itself on mount** (no ref passed through the form hook), which also removed the lint noise that hid a real signal.
- **Playwright uses the installed Chrome** (`PALCHECK_E2E_CHANNEL=chrome`) because the bundled browser the installed Playwright expects was not present and a download changes the machine outside the repository. CI installs Chromium normally.
- **Documented limitations rather than hidden**: search is not accent-insensitive; manager picker lists the first 100 people; no manual screen-reader pass; sessions in memory (plan section 17).
- No architecture change was needed; nothing contradicts an accepted ADR.

### Tests

All run on 2026-09-19 and passing: **backend 150** (58 unit and architecture including 15 ArchUnit rules; 92 integration on real PostgreSQL 17 including a real OIDC login flow), **frontend 92** (Vitest, Testing Library, axe), **browser end to end 12** (sign in through Keycloak, create, edit, history, search, stale-tab conflict, viewer without controls, cross-tenant "Record not found", no-workspace identity, sign-out really ends the session, validation summary, real-browser axe on seven screens plus the dark scheme, keyboard-only journey, reduced motion). Frontend lint (oxlint) and type check are clean; production build succeeds. Guardrail greps: no legal terms and no country-specific branching in code (`CR` appears only as seed data); every email on a reserved domain; no credentials committed.

**Defects the tests found and I fixed** (worth knowing because they were real):
1. A `401` from the session check notified the "session ended" listener, which re-ran the session check, in an endless request loop (found because a test ran out of memory).
2. After a mid-session `401` the cached session kept the app in the signed-in view.
3. Focus never reached the heading when navigating to a page that starts loading, and moving focus on first load skipped the skip link.
4. Real-browser axe: two landmarks both named "Employment", and error-summary links below the WCAG 2.2 target size.
5. My own docs overstated the system in three places (request-size limits, request ids accepted from clients, CI scanning); corrected to match the code.

### Assumptions

- Chrome results stand in for other browsers; no Firefox or Safari run.
- The local Keycloak 26.7.4 and PostgreSQL 17 runs are representative of what Compose provides.
- The dev database used for the browser run is throwaway and lives outside the repository (a scratch cluster on port 5434, Keycloak from an unpacked distribution). Daniel's own local PostgreSQL service was not touched.

### Open questions

Q1 to Q3 in the plan are still open for Daniel (confirm ADR-0022's provisional choices, required-on-create idempotency and 24-hour expiry, placeholder employment vocabularies). New: should search become accent-insensitive before real use (PostgreSQL `unaccent`)? Should Phase 1 add dependency and secret scanning to CI, or wait for the provisioning milestone?

### Risks

- `docker-compose.yml`, `ci.yml` and `e2e.yml` have never been executed on Docker or GitHub. First run may need small fixes (plan R2).
- Accessibility is verified by tools and a keyboard-driven test, not by a person using a screen reader.
- Row-level security, rate limiting and a request-size limit are not in place; acceptable for synthetic local data, required before anything real.
- The browser suite depends on the seeded development database and adds fictional people on each run.

### Suggested next step

Daniel reviews Phase 1: run the app from LOCAL_DEVELOPMENT.md, read plan sections 1 and 17, confirm or amend ADR-0022 (Q1 to Q3), and decide whether to open the next milestone. Codex could review the frontend accessibility approach and the tenant-isolation tests. Until Daniel opens the next milestone, only Phase 1 fixes and review feedback should be built.

## 2026-09-19 21:15 — CODEX

### Task

Independently review Claude's completed Phase 1, polish the rendered product, fix Phase 1 correctness/security issues, and prepare public-repository documentation. Daniel authorized this review in the supplied handoff and instructed continuation after an environment approval interruption. Phase 2 remains closed.

### Work completed

- Inspected required project documents, implementation plan/Q1–Q3, application and test code; independently ran baseline suites and inspected rendered screens before changes.
- Completed [PHASE_1_CODEX_REVIEW.md](PHASE_1_CODEX_REVIEW.md), with all 22 requested sections, evidence, tradeoffs, recommendations, and limitations. Added [PHASE_1_REVIEW_HANDOFF.md](PHASE_1_REVIEW_HANDOFF.md) with consensus and a bounded proposed next prompt.
- Added tenant-scoped accent-insensitive directory/manager search through forward migration V6; server-paged employment candidates replace the first-100-person manager limitation. Stored spelling is preserved.
- Reproduced and fixed concurrent manager changes that could jointly create a cycle; per-tenant transaction advisory locking protects graph validation. Recorded reasoning in Proposed [ADR-0023](adr/0023-manager-graph-concurrency.md).
- Preserved edit drafts and their original versions across background query refresh; explicit conflict reload replaces the draft and handles reload failure.
- Added a 64 KiB API write-body limit; hardened web/security/JDBC logging defaults; replaced JSON logout URL exposure with a native CSRF-protected form and Spring OIDC redirect.
- Prepared pinned secret/dependency security workflows and Dependabot; updated README, synthetic screenshots, security/local-dev/UI/architecture/plan/roadmap/handoff documentation. Added COPYRIGHT.md and OPEN_SOURCE_STRATEGY.md without selecting a license or publishing anything.

### Decisions made

- Retain Claude's modular monolith, JDBC, backend OIDC sessions, module boundaries, and transactional audit/idempotency; no new business module, table, or application dependency.
- PostgreSQL unaccent is bundled infrastructure with explicit extension schema/privilege requirements. Folded substring scans remain unindexed within tenant candidates; do not add an unsafe IMMUTABLE wrapper or claim measured scale.
- Use native manager selection with debounced server search/paging and retained off-page selection; no load-all fallback or custom combobox framework.
- Request size: IMPLEMENT NOW (complete). Rate limiting: NEXT SECURITY MILESTONE before public hosting. RLS remains deferred for owner confirmation and runtime-role/transaction design.
- Q1–Q3 recommendations are ACCEPT / AMEND / ACCEPT; no owner decision was silently made. Q2 amendment clarifies bounded receipt lifetime and current-resource replay, without changing the existing contract.

### UI changes

Compact neutral workspace/account rail; one real People destination; native font stack; clearer directory/search toolbar, initials and subtle separators; grouped record rows/context links; readable timeline history; restrained form width and aligned controls; preserved draft/conflict behavior; intentional loading/empty/error states; responsive header/table/forms at 320px; light/dark and accessibility behavior retained. No Apple assets, bundled proprietary fonts, decorative dashboards, or fake functionality.

Rendered sign-in, directory, detail, create/edit, history, conflict, viewer, empty/error, dark and mobile states were inspected. Deterministic conflict/error screenshots used controlled responses; actual stale-write behavior was separately tested against the backend. README screenshots contain synthetic data only.

### Tests

Independent baseline: backend 149/150 (58 unit/architecture, 91/92 integration), frontend 92/92, Chrome E2E 12/12; typecheck/lint/build passed. LoggingCanaryIT failed under inherited DEBUG activation and was fixed through configuration without weakening the test. The new manager-cycle regression reproduced the race before the lock fix.

Final: **155/155 backend** (60 unit/architecture, 95 integration; real PostgreSQL 17 and test OIDC provider), **94/94 frontend** across 8 files, **15/15 Chrome E2E** against real local frontend/backend/Keycloak/PostgreSQL. Typecheck and lint clean; production build passed. E2E includes axe light/dark, keyboard, reduced motion, 320px reflow, accent search, manager discovery, real stale-tab conflict and full logout. New integration/unit/component tests cover graph concurrency, search/manager bounds/isolation, known/unknown-length bodies, logout CSRF, retained selection, and draft/version preservation.

Vitest workers are limited to two to avoid resource contention; assertions/timeouts were not weakened. A Windows clean-build log-file lock was resolved by stopping the review's running backend, then clean verification passed. The reviewed backend was restarted for final E2E. Docker is unavailable and no GitHub workflow was run.

### Security review

Local npm audit reported zero vulnerabilities. A checksum-verified Gitleaks 8.30.1 scan of publication-candidate source found no secrets. There is no `.git` directory, so historical commits could not be inspected. Only synthetic fixtures and intentional local/test placeholder credentials were identified; no real employee/employer data or confidential material was found. Prepared security workflows and hosted settings are distinct: no remote settings were enabled. A full transitive Java advisory scan remains outstanding.

Logout's ID-token hint is no longer returned to JavaScript in JSON; it still travels in the standard browser redirect to the provider. The audit remains transactional and append-only under normal runtime access, not tamper-proof against a database owner. No claim of production readiness or comprehensive vulnerability absence is made.

### Assumptions

Local PostgreSQL 17 and Keycloak runs are representative of the intended Compose stack, pending actual container verification. The review uses only synthetic local data. Gitleaks Action configuration assumes personal-repository ownership. Screenshot records and E2E additions remain in the scratch development database; Daniel's separate PostgreSQL service was not changed.

### Open questions

Daniel's Q1–Q3 decisions, ADR-0023 acceptance, publication timing/license/contribution policy, and whether to authorize a public hosted demo. Human screen-reader validation, another browser, Compose/GitHub runs, Java dependency coverage and hosted security settings need separate evidence.

### Risks

In-memory sessions, no RLS or rate limiting, history reads not audited, unmeasured folded-search/deep-page/graph-lock performance, and production privacy/operations work remain. Extension installation needs the documented package/schema/privileges. Idempotency expires after 24 hours and does not supply permanent business-effect deduplication. No executable legal rules or Phase 2 features exist.

### Suggested next step

Daniel reviews the app and Q1–Q3. Authorize only a bounded Phase 1 acceptance/publication-validation pass: execute Compose and GitHub checks where available, verify Java/hosted security coverage, and perform another-browser plus human screen-reader checks. Fix concrete failures; publish/license/deploy only with explicit authorization. The next prompt is prepared in PHASE_1_REVIEW_HANDOFF.md. Stop here; do not start Phase 2.

## 2026-09-20 16:41 — CODEX

### Task

Prepare PalCheck for the GitHub push explicitly requested by Daniel.

### Work completed

Initialized a local Git repository on main. Confirmed Git author configuration and available GitHub accounts through Git Credential Manager. Existing source/build exclusions remain in place. The prior Phase 1 review and its limitations remain recorded; this task changes repository delivery, not product scope.

### Decisions made

Use the configured Git author identity and preserve all project history/documents in the initial snapshot. Do not select a license or infer a destination/visibility. Daniel's repository URL or new-repository preference is pending.

### Tests

No application code changed. Prior results remain 155 backend, 94 frontend, and 15 Chrome E2E tests passing. Publication-candidate secret scanning and staged-file checks are performed before the initial commit.

### Assumptions

Daniel's request authorizes committing and pushing the reviewed project to his specified GitHub destination.

### Open questions

Destination repository and, if creating one, visibility and owning account.

### Risks

Remote authentication and GitHub workflow execution are not yet verified. Existing Phase 1 security and accessibility limitations remain unchanged.

### Suggested next step

Push the prepared commit after the destination is supplied; verify the remote branch and report the commit/link.

Publication preparation also records portable line endings in .gitattributes and marks backend/mvnw executable in Git for Linux CI. A trailing blank line flagged by the staged whitespace check was removed; no test behavior changed.

## 2026-09-20 16:50 — CODEX

### Task

Create and push PalCheck to a public GitHub repository under d4guilar, explicitly selected by Daniel after the public portfolio recommendation.

### Work completed

Verified the saved credential belongs to d4guilar. Created the public repository https://github.com/d4guilar/palcheck, configured origin, and pushed main with upstream tracking. GitHub's remote main matched initial commit f25a897d3f534aa8d54e582271795e1e2f2895fd. Updated README and the open-source strategy to reflect actual public visibility.

### Decisions made

Repository visibility is Public and owner is d4guilar, per Daniel's authorization. No license was selected; copyright and Phase 1 scope remain unchanged. Existing history was preserved; no force push was used.

### Tests

The initial publication candidate had 273 staged files, passed Gitleaks with no findings, and excluded environment secrets, generated output, dependencies, and local browser artifacts. The staged whitespace check passed and the Maven wrapper is executable in Git. Remote ref verification confirmed the first push. No application code changed; application suites were not rerun for this publication task. GitHub Actions results are separate from prior local test evidence.

### Assumptions

The chosen account and public visibility are intended for Daniel's recruiter-facing portfolio.

### Open questions

License/contribution policy and Phase 1 acceptance decisions remain open. Hosting a live application is a separate decision.

### Risks

Public source visibility does not establish production readiness. Previously recorded security/accessibility limitations remain; hosted workflow outcomes need inspection.

### Suggested next step

Review GitHub Actions results and use the repository link on the portfolio. Continue only the separately authorized Phase 1 acceptance/publication-validation scope; do not infer authorization for Phase 2 or a public application deployment.
