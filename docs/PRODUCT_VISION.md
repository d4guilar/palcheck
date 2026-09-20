# Product Vision

Status: Phase 0 draft · Owner: Daniel Aguilar · Documentation owner: Claude

## One line

**PalCheck — Local rules. Global-grade infrastructure.**

## The problem

Organizations that employ people in Latin America hit two gaps.

1. **Global HR systems flatten local employment.** A global HRIS models "annual bonus", "termination", and "leave" as
   generalized abstractions. Local concepts such as *Aguinaldo*, *Cesantía*, *Preaviso*, and *CCSS* have specific names,
   meanings, and behaviors. When they are forced into a generic field, meaning is lost or, worse, silently distorted.
2. **Small and mid-sized local employers lack accessible, well-integrated workforce infrastructure.** They need employee
   records, lifecycle flows, and reliable hand-offs to payroll, identity, and IT, without adopting a heavyweight global suite.

## Product thesis

**Translate, do not distort.**

PalCheck keeps *global workforce primitives* (Person, Employment, Assignment, Leave, Termination) separate from *local
employment concepts* (Aguinaldo, Cesantía, Preaviso, CCSS), and records explicitly how they relate, including where they
do not map cleanly. It then orchestrates the downstream work that follows workforce events, and makes that work observable
and recoverable.

Three commitments follow:

1. **Local terms are preserved** with an English explanation, never replaced by a misleading generic term.
2. **Legal rules are never invented.** Concepts are educational and clearly labelled until verified from authoritative sources
   and reviewed by a qualified professional. Software behavior is a separate, later layer.
3. **Failure is visible.** Downstream systems fail independently; PalCheck shows what happened, why, and how it recovered.

## Two operating modes

| | **Standalone** (primary design driver) | **Enterprise overlay** (future-compatible seam) |
|---|---|---|
| Who | Small organization with no large global HRIS | Organization already on Workday, SAP SuccessFactors, Oracle HCM, or similar |
| System of record | PalCheck | The external HRIS |
| PalCheck's role | Employees, employment, org structure, lifecycle, integrations, audit, local concepts | Consume and normalize external data, add local context, orchestrate local workflows, explain local concepts |
| Field ownership | Default `PALCHECK` | Per-field `EXTERNAL` for data the HRIS owns |
| Phase | Phase 1 | Phase 2+ (reference connector proves the seam) |

Standalone-first is Daniel's decision ([ADR-0017](adr/0017-primary-product-mode.md)). The overlay seam is preserved by keeping the
core domain free of any provider's concepts and by making field ownership a per-field policy from the start.

## Who it is for

These are audiences, not permission roles (roles live in [SECURITY.md](SECURITY.md)).

- **HR administrator at a small Costa Rican employer.** Wants correct-looking, calm, low-effort records and lifecycle flows,
  and to understand what local concepts mean in the product.
- **Regional HR partner at a multinational** (future overlay). Needs local context on top of the global HRIS without
  double-keying.
- **Engineers and enterprise architects** evaluating the design: integration boundaries, ownership, resilience.
- **Recruiters, hiring managers, and technical interviewers** reading this as Daniel's portfolio. The reasoning is the product.
- **Product managers unfamiliar with local employment law**, who need the registry to explain the local concept.

## What PalCheck is not

- **Not a payroll engine.** It represents payroll concepts and orchestrates hand-offs to a payroll system; it does not compute pay.
  (The name says "paycheck"; the product does not calculate one.)
- **Not legal advice**, and not a compliance-filing service.
- **Not an applicant tracking / recruiting system.** Pre-hire is an inbound hire request, not a recruiting pipeline.
- **Not a replacement for a global HRIS** in large enterprises.
- **Not a generic workflow engine or a generic integration platform.**
- **Not Apple-affiliated**, nor affiliated with Workday or any other vendor or employer.

## Principles

1. **Translate, do not distort.** Preserve local meaning; record non-equivalence honestly.
2. **Legal safety first.** Concept, rule, and behavior are separate layers with separate statuses.
3. **Ownership is explicit.** Every externally owned value is recognisable and never silently overwritten.
4. **Resilience is visible.** Retries, idempotency, and dead letters are product features, not plumbing.
5. **Simple before clever.** A modular monolith, boring data store, and no broker until justified.
6. **Security is a first-class feature.** Employee data is sensitive by default.
7. **Calm, precise, accessible interface.** Premium through restraint, not decoration ([UI_SYSTEM.md](UI_SYSTEM.md)).
8. **Explain why.** Every significant decision has an ADR that Daniel can defend in an interview.

## Language policy

The product language is **English**: code, APIs, documentation, UI labels. Native legal and employment terms keep their
original name plus an English explanation. Every user-visible string is externalized from the start so a Spanish UI can be
added without a rewrite; a Spanish UI is likely to matter for the business option (see [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) W-06).

## Two goals, one project

1. **Portfolio.** Demonstrate enterprise architecture, Java/Spring depth, integration engineering, event-driven reliability,
   observability, security, accessible UI, and localization thinking. Success is the *quality of reasoning*, not the size of the stack.
2. **Business option.** Keep the door open to a real Latin American workforce-infrastructure business. This does not change what
   is built now; it changes what is *not foreclosed*: tenancy, legal-entity modeling, professional review of legal content,
   data-protection posture, and a Spanish-capable UI. See [PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) section 11.

These goals mostly agree. Where they conflict (a big stack impresses, a small one is right), the brief already decides: the
right-sized design wins.

## Independence and clean-room statement

PalCheck is an independent personal project. It uses no employer-confidential information, credentials, tenant identifiers,
configurations, or data. Vendor-shaped behavior is built only from public information or material Daniel explicitly provides as
his own, over synthetic data, and is labelled as simulated. Rules are in [AGENTS.md](../AGENTS.md) section 5.

## Success criteria

| Horizon | Success looks like |
|---|---|
| Phase 0 | Architecture and domain reviewed by Daniel and Codex; ADRs ready to accept; open questions named. |
| Phase 1 | One vertical slice (hire to provision) runs end to end with independent downstream failure, retry, and idempotency, visible in the UI, with a read-only Costa Rica concept glossary. |
| Phase 2 | A Workday-shaped reference connector flips field ownership on real fields **without changing the core domain**. |
| Portfolio | Daniel can answer every question in the brief's "Interview Readiness" list from the docs, with tradeoffs. |
| Business option | A verified, professionally reviewed Costa Rica concept set and a defensible data-protection posture exist before any customer data does. |

## Non-goals for now

Other countries, any statutory calculation, recruiting, payroll computation, a message broker, a generic workflow engine,
two-way sync with external HRIS, mobile apps, marketplaces, and any real employee data.
