# Codex Review Handoff — 2026-09-19

From: CODEX · To: Claude Code, then Daniel Aguilar · Phase: **0, documentation only**.

The second senior review is complete in [CODEX_ARCHITECTURE_REVIEW.md](CODEX_ARCHITECTURE_REVIEW.md). The original [CODEX_HANDOFF.md](CODEX_HANDOFF.md) is preserved as Claude's first-pass record. No ADR is Accepted and no phase was opened. No code, schema, migrations, endpoints, mocks, infrastructure or dependencies were created.

## Decisions proposed, not made

| Proposal | What Claude should challenge | Daniel decision |
|---|---|---|
| [0018](adr/0018-module-transactions-and-persistence.md) | Dependency DAG; atomic audit/outbox participants; workforce owns initial hire; tenant-anchor FK exception; history/migration plan | D1 |
| [0019](adr/0019-durable-delivery-and-effect-identity.md) | Durable fan-out; event vs. effect identity; frozen request; expired sends and stale leases; replay generations and retention | D2 |
| [0020](adr/0020-foundation-first-delivery-plan.md) | Useful smaller Phase 1; session/OIDC boundary; build/persistence defaults; postponed spikes and portfolio milestone | D3 |
| [0021](adr/0021-registry-history-and-behavior-gates.md) | Historical expiry vs. withdrawal; missing coverage; display statuses without executable bindings | D4 |
| Review §7 | Page checkpoint barrier, missing-record reconciliation, source-instance identity, ownership cutover and actual lineage links | D5 |

## Review response requested

Respond accept/fix, decline/reason or escalate for TX-01, EVT-01, IMP-01, SEC-01, LOC-01, SCP-01, LIN-01, DAT-01 and REF-01. TX-01 and SEC-01 affect the first implementation; EVT-01 affects provisioning; IMP-01/LIN-01 affect overlay; LOC-01 blocks legal behavior only. Standalone-first is Daniel's existing choice, not reopened.

The main review contains the dependency inventory, proposed package/data/API boundaries, logical outbox state and crash cases, differentiated simulator retry defaults, job/observability progression, test pyramid, local Windows development plan, future CI/deployment progression and enterprise feature priorities. Avoid duplicating it here.

## Evidence and limits

Primary public sources verified PostgreSQL RLS/range/locking semantics, Spring transaction propagation and Modulith registry capabilities, OIDC login support, ArchUnit checks, Flyway migration concepts, Testcontainers requirements, Workday public SOAP documentation and selected UI/standards claims. Citations are adjacent to claims in the review. No live Workday access or employer material was used. No legal fact was verified.

No runtime or performance claim was tested. Mermaid was inspected as text, not rendered; no renderer was installed. The worklog records final documentation checks. Original file hashes and original worklog length/hash were captured in the review session to check preservation; there is no Git repository, and none was initialized.

## Next boundary

Claude responds, then Daniel accepts/amends/declines D1–D5. If accepted, reconcile the baseline documents, ADRs, diagrams and phase numbering in one documentation pass; review notices are not substitutes for that reconciliation. Only Daniel may separately open Phase 1 in [ROADMAP.md](ROADMAP.md).

Suggested implementation scope after that authorization: the authenticated workforce foundation in review §15. No implementation work is authorized by this handoff.
