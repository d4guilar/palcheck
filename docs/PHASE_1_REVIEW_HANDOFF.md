# Phase 1 review handoff and next-prompt scope

Date: 2026-09-19. From: Codex. To: Daniel Aguilar and the next engineer.

## Consensus

**Current follow-up:** Daniel accepted the functional Phase 1 foundation and authorized a dedicated UI/UX pass. [UI_PHASE_1_APPLE_POLISH.md](UI_PHASE_1_APPLE_POLISH.md) records the updated interface, screenshots and verification. Q1/Q2/Q3 decisions remain as recorded below; Phase 2 is not authorized.

Claude implemented the authenticated workforce foundation: modular monolith, OIDC sessions, tenant-aware reads/writes, permissions, versioned resources, transactional audit/idempotency, and the initial accessible React application. Codex independently reviewed that implementation, exercised its tests and rendered UI, and retained its architecture.

Codex refined the UI, added accent-insensitive search and paged manager lookup, preserved edit drafts during refresh, fixed a demonstrated manager-cycle race, bounded API write bodies, hardened framework logging, and moved logout to a CSRF-protected framework redirect. README screenshots, copyright/ownership documentation, and prepared security workflows improve publication readiness. The [22-section review](PHASE_1_CODEX_REVIEW.md) contains the reasoning and evidence.

Historical review evidence (2026-09-19): **155 backend tests, 94 frontend tests, 15 Chrome E2E**, typecheck/lint/build passed; npm audit reported zero advisories; Gitleaks found no source-snapshot secrets. No `.git` directory exists here, so history could not be scanned. Docker Compose, hosted workflows/settings, full Java advisory coverage, other browsers, and human screen-reader validation remain unverified.

## Owner decision status (updated 2026-09-21)

Daniel has **ACCEPTED Q1 on 2026-09-21**, exactly as recorded in [plan section 16](PHASE_1_IMPLEMENTATION_PLAN.md#16-risks-and-open-questions): Spring JDBC over JPA, backend OIDC session over browser-held tokens, Maven with packages and ArchUnit instead of Spring Modulith, embedded PostgreSQL for tests while no container runtime is available, row-level security deferred. [ADR-0022](adr/0022-phase1-technical-baseline.md) records the scoped acceptance and implementation check; no code or test changes were required. **Q2 is ACCEPTED WITH AMENDMENT on 2026-09-21:** at least 24 hours is a guaranteed retry window for the same tenant/principal/operation/key and equivalent payload; a different payload conflicts during it. Afterward the original receipt may be unavailable, and callers must not assume another attempt is safe or equivalent. `palcheck.idempotency.ttl` defaults to `PT24H`; minimum-duration validation and completion-time retention enforce the contract. Current-resource replay is retained. [The API contract](PHASE_1_IMPLEMENTATION_PLAN.md#guaranteed-retry-window-api-contract-and-configuration) documents exact behavior and configuration. **Q3 is ACCEPTED on 2026-09-21:** `PENDING`, `ACTIVE`, `ENDED` and `FULL_TIME`, `PART_TIME` are core software/domain vocabulary, deliberately legally neutral (the accepted label "placeholders" means not legally authoritative, not temporary), and not complete or legally authoritative Costa Rican categories. They must not drive statutory calculations or imply legal eligibility/compliance. Future local classifications, mappings, metadata and terminology remain separate research. Stable values and behavior are unchanged; documentation, code comments and existing contextual form help clarify the boundary. ADR-0023 records the implemented graph-concurrency fix as Proposed. Do not change ADR acceptance based on an agent recommendation.

Daniel is PalCheck's creator and owner. No project license has been selected and no external contribution policy has been assumed. Current publication status is recorded in [README.md](../README.md).

## Suggested next implementation prompt

The following is a proposed scope for Daniel to authorize, not an instruction to start work automatically:

> Continue PalCheck with a bounded Phase 1 release-validation pass. Read AGENTS.md, the Phase 1 plan, Codex review, current worklog, and relevant ADRs. Preserve the existing foundation, completed review fixes, and Daniel's recorded Q1 and Q3 acceptance and Q2 acceptance with amendment. All three questions are resolved and Daniel has accepted the functional Phase 1 foundation. Preserve the completed UI polish; release validation remains separate. Record only owner decisions explicitly supplied in this session; recommendations do not count as approval.
>
> Run Docker Compose in a container-capable environment and verify setup from the documented instructions. Once an authorized GitHub repository is available, run build, browser, and security workflows and verify dependency graph/Java advisory coverage and hosted security settings. Inspect available Git history for secrets without rewriting it. Fix concrete failures and document actual results separately from prepared configuration.
>
> Perform another-browser checks and arrange human screen-reader validation, recording the browser/assistive-technology combination and any limits. Keep all data synthetic. Re-run only the suites affected by fixes plus required acceptance checks. Update the review, handoff, and append-only worklog with evidence.
>
> Do not publish, choose a license, change ownership, deploy an internet-facing demo, or open Phase 2 without explicit authorization. If public hosting is requested, first define and authorize the rate-limit/deployment security work. No connector, broker, payroll, legal rule, provisioning, or operations console belongs to this milestone. Stop with an acceptance report and unresolved owner decisions.

This keeps the next milestone small and reviewable. It avoids repeating completed UI/search work or treating a passing local suite as proof of production readiness.
