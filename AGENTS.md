# AGENTS.md — Shared rules for every AI agent working on PalCheck

This file is the **single source of truth** for how agents (Claude Code, OpenAI Codex, or any other) work in this
repository. `CLAUDE.md` imports it; do not copy its content elsewhere. If a rule here is wrong, change it here and
log the change in [docs/WORKLOG.md](docs/WORKLOG.md).

## 1. What this project is

PalCheck is an independent personal project created and owned by **Daniel Aguilar**. It is a Latin America workforce
translation and orchestration platform (Costa Rica first). It is **not** affiliated with, endorsed by, or built on
knowledge of any employer or vendor (Apple, Microsoft, Workday, Equifax, SAP, Oracle, ServiceNow, or others).
Daniel is the final authority. Read [MASTER_PROJECT_BRIEF.md](MASTER_PROJECT_BRIEF.md) for the full intent and
its Amendments.

## 2. Current phase gate

**Phase 2: External HRIS Translation Foundation. Opened by Daniel on 2026-09-22.** Phase 1 (Authenticated Workforce Foundation, opened 2026-09-19) is accepted; its non-goals below still apply except where Phase 2 explicitly narrows them. Production application code is allowed only within the scope in [docs/PHASE_2_IMPLEMENTATION_PLAN.md](docs/PHASE_2_IMPLEMENTATION_PLAN.md) (Phase 1 defects and review feedback also remain in scope). The next milestone is opened by Daniel, not assumed. Build incrementally; do not generate an application in one uncontrolled pass.

**Still forbidden (Daniel's explicit Phase 1 and Phase 2 non-goals):** a real Workday production integration or API certification; SAP or Oracle connectors or simulators (not even empty adapters); Kafka, Redpanda or any event broker; outbox dispatch or any event publication with no use yet; payroll; CCSS, aguinaldo, vacation, cesantía or preaviso calculations; country-policy execution or any executable Costa Rican legal behavior; provisioning and equipment workflows; incident management, SLO dashboards, Prometheus, Grafana, distributed tracing platforms; microservices; a mobile app; anything real (employee data, employer data, credentials, law). Do not implement Phase 3 (later-milestone) functionality.

Phase 2 narrows Phase 1's blanket "no integrations/no Workday" rule to exactly this: one mock Workday-shaped adapter, clearly labelled a simulation, feeding a small `integrations` module through a new `workforce.api` write port — see [docs/PHASE_2_IMPLEMENTATION_PLAN.md](docs/PHASE_2_IMPLEMENTATION_PLAN.md) sections 2–4 for the exact boundary. It does not authorize a real provider, a second provider, or the outbound/outbox half of [docs/INTEGRATION_ARCHITECTURE.md](docs/INTEGRATION_ARCHITECTURE.md).

Adding a dependency, a database table outside the plan, or a new module requires a note in the plan or an ADR first. If implementation reveals an architectural problem: do not work around it silently; document it, update or propose the ADR, explain the tradeoff, and proceed only if compatible with accepted direction or Daniel approves.

If you are unsure whether something crosses the gate, it does. Ask Daniel.

## 3. Read before meaningful work

In this order:

1. [README.md](README.md)
2. [MASTER_PROJECT_BRIEF.md](MASTER_PROJECT_BRIEF.md) (including Amendments)
3. this file
4. [docs/PRODUCT_VISION.md](docs/PRODUCT_VISION.md)
5. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
6. [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md)
7. [docs/AI_COLLABORATION.md](docs/AI_COLLABORATION.md)
8. [docs/WORKLOG.md](docs/WORKLOG.md) (newest entries first matter most)
9. [docs/PHASE_1_IMPLEMENTATION_PLAN.md](docs/PHASE_1_IMPLEMENTATION_PLAN.md), [docs/CODEX_ARCHITECTURE_REVIEW.md](docs/CODEX_ARCHITECTURE_REVIEW.md), and the ADRs relevant to your task ([docs/adr/README.md](docs/adr/README.md))
10. Claude only: [CLAUDE.md](CLAUDE.md)

Also read [docs/CODEX_HANDOFF.md](docs/CODEX_HANDOFF.md) if you are picking up after another agent.

## 4. Legal safety (non-negotiable)

- **Never invent employment law.** No statutory percentages, formulas, contribution rates, tax rules, severance,
  vacation accrual, overtime, eligibility rules, deadlines, or compliance requirements. Not "approximately", not
  "typically", not "from memory".
- Every legal concept carries a status: `UNVERIFIED`, `RESEARCH REQUIRED`, or `NOT IMPLEMENTED` until verified from
  an authoritative source per [docs/localization/costa-rica/LEGAL_RESEARCH_POLICY.md](docs/localization/costa-rica/LEGAL_RESEARCH_POLICY.md).
- Keep three layers separate and label which one you are writing about:
  **DOMAIN CONCEPT** (what is it) · **LEGAL RULE** (what law requires) · **SOFTWARE BEHAVIOR** (what PalCheck does).
- Native terms are kept with an English explanation: `Aguinaldo — Mandatory annual bonus (Costa Rica)`.
- PalCheck documentation is not legal advice. Do not write it as if it were.

## 5. Independence and clean-room rules

- Never use, paste, paraphrase, or "recall" employer-confidential material: real credentials, tenant IDs, employee
  records, integration configurations, internal tooling details, or proprietary vendor documentation.
- Vendor behavior may only come from public documentation or from what Daniel explicitly provides as personal material.
  When unsure whether something is public, treat it as not usable and say so.
- Never imply partnership, certification, or endorsement by Workday or anyone else. Mock connectors are labelled as
  reference/simulated.
- Never use Apple assets, logos, or trademarks in the product. Apple-*inspired* design principles are fine
  ([docs/UI_SYSTEM.md](docs/UI_SYSTEM.md)).
- Demo and test data is **synthetic** (fictional organizations and people; no real coworkers, customers, employer data or identifiers). Synthetic national IDs must be invalid-by-construction so they can never
  collide with a real person's.
- Never commit secrets. Never put real personal data in the repo, issues, logs, or fixtures.

## 6. Documentation standard

Explain **why**, not just what. Every major component documents: purpose, responsibility, dependencies, ownership,
failure modes, security, testing, future scaling, tradeoffs.

Every significant decision goes through the eight questions in the brief (problem, why this, alternatives,
tradeoffs, what would change it, operational burden, does complexity justify itself, can Daniel explain it in an
interview). If the answer to the last one is "no", simplify.

Prefer: clear, simple, defensible, maintainable, observable, testable, evolvable.
Avoid: premature abstraction, premature distribution, architecture for resume optics, technology accumulation,
magic behavior, unexplained frameworks.

Mark uncertainty explicitly. `Assumption:`, `Unverified:`, and `Open question:` are the prefixes to use.

## 7. ADRs

- Location: `docs/adr/NNNN-kebab-title.md`. Format: see [docs/adr/README.md](docs/adr/README.md).
- New significant decisions need an ADR. Trivial choices do not.
- **Accepted** ADRs override agent preference. Only Daniel moves an ADR from Proposed to Accepted.
- To change an Accepted ADR: write a new ADR that supersedes it; never rewrite history.

## 8. Worklog protocol

After every meaningful task, **append** an entry to [docs/WORKLOG.md](docs/WORKLOG.md). Never edit or reorder
earlier entries. Use the exact format (`## YYYY-MM-DD HH:MM — CLAUDE|CODEX`, then Task / Work completed / Decisions
made / **Tests** (Phase 1 onward) / Assumptions / Open questions / Risks / Suggested next step). Timestamps are local time of the machine used;
get them from the system clock, do not guess.

To avoid merge conflicts, one entry = one contiguous section appended at the end of the file.

## 9. Collaboration rules

- Never silently undo or rewrite another agent's work.
- If you disagree: (1) document the disagreement, (2) explain the tradeoffs, (3) propose an alternative,
  (4) list the impacted ADRs, (5) ask Daniel when the disagreement materially changes architecture.
- Do not duplicate completed work; check the worklog first.
- Communicate through repository documents, not assumed chat memory. If it is not in the repo, the next agent
  does not know it.
- Handoffs go in [docs/CODEX_HANDOFF.md](docs/CODEX_HANDOFF.md) (or a dated successor); see
  [docs/AI_COLLABORATION.md](docs/AI_COLLABORATION.md).

## 10. Conventions

- Language: English for code, APIs, docs, UI labels. Native-language terms preserved per section 4.
- Status vocabularies (use exactly these):
  - Research: `UNRESEARCHED` · `RESEARCHING` · `VERIFIED` · `DEPRECATED`
  - Implementation: `DOCUMENTED` · `PARTIAL` · `IMPLEMENTED` · `DISABLED`
  - ADR: `Proposed` · `Accepted` · `Accepted in part` (with a per-decision acceptance record) · `Superseded`
  - Ownership: `PALCHECK` · `EXTERNAL` · `DERIVED`
- Terminology for the three meanings of "organization" (see [docs/DOMAIN_MODEL.md](docs/DOMAIN_MODEL.md)):
  **Tenant** (customer of PalCheck), **Legal Entity** (employer of record), **Org Unit** (department/cost center hierarchy).
  Never use bare "organization" in a design or an API name.
- Diagrams: Mermaid, in [docs/diagrams/](docs/diagrams/), linked (not copied) from other docs.
- Build and test (details in [docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md)): backend `cd backend && ./mvnw verify` (Windows: `mvnw.cmd`; needs JDK 17, no Docker); frontend `cd web && npm run typecheck && npm run lint && npm test && npm run build`; browser suite `npm run e2e` with the whole stack running. A change is not done until the relevant suites pass; report failures with their output.
- Code conventions: backend modules are Java packages and only `api` packages are visible across them (ArchUnit enforces it); every repository method takes a `TenantId`; every command runs in one transaction that its audit entry joins; new user-visible text goes in `web/src/i18n/en.ts`; new UI is checked with axe and keyboard use; personal values never go into logs. Test and seed data is fictional, with emails on reserved domains.
