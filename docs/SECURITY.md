# Security

> **Reconciliation notice (2026-09-19):** Phase 1 implements a subset of this document: tenant isolation enforced server-side, OIDC authentication with a backend-managed session, permission-based authorization (tenant-wide scope only), in-transaction audit, safe structured logging, and synthetic-only data. The Phase 1 permission model and the exact controls are specified in [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) sections 6 and 7 and are updated here when Phase 1 completes. [Review SEC-01 and §12](CODEX_ARCHITECTURE_REVIEW.md#12-security-concerns-and-authentication-direction) (query-scoped authorization, trusted contexts, RLS role requirements, backend sessions) is followed provisionally ([ADR-0020](adr/0020-foundation-first-delivery-plan.md) decisions 5 and 6, [ADR-0022](adr/0022-phase1-technical-baseline.md)). Row-level security is deferred and not implemented.

Status: baseline **Proposed**; Phase 1 controls implemented and listed in section 17 ([ADR-0003](adr/0003-postgresql-and-tenancy.md), [ADR-0013](adr/0013-authentication.md), [ADR-0014](adr/0014-authorization.md)).
PalCheck models employment data: personal, financial, and health-adjacent. Security is a first-class feature, not a hardening pass.

**Not a compliance claim.** PalCheck claims no certification (SOC 2, ISO 27001, or otherwise) and makes no assertion of compliance with any data-protection law.
Which data-protection regime applies to Costa Rican employee data, and what it requires, is RESEARCH REQUIRED ([localization/costa-rica/RESEARCH_BACKLOG.md](localization/costa-rica/RESEARCH_BACKLOG.md) ID-3 to ID-5).

## 1. Principles

1. **Sensitive by default.** Anything about a person is confidential until classified otherwise.
2. **Least privilege.** Permissions are explicit, scoped, and minimal.
3. **Fail closed.** Missing tenant, missing principal, or missing permission means deny.
4. **Defense in depth.** No single control (UI, API check, or database policy) is the only guard.
5. **Secure defaults.** Redacted by default, off by default for risky capabilities (replay, fault injection, write-back).
6. **Privacy by design.** Collect and retain the minimum; design for retention limits and erasure from the start.
7. **No real data, ever.** Only synthetic data in demos, tests, logs, and the repository.

## 2. Data classification

Exact legal categories (for instance, what counts as special-category data) are RESEARCH REQUIRED. These tiers are *engineering* handling tiers.

| Tier | Examples | Handling |
|---|---|---|
| **T0 Public** | Legal Concept Registry content, documentation | No restriction; still served behind auth |
| **T1 Internal** | Org structure, job titles, system configuration | Tenant-scoped; ordinary access logging |
| **T2 Confidential** | Names, contact details, dates of birth, employment dates, manager relationships | Tenant- and scope-limited; never in logs or metric labels; masked in exports |
| **T3 Restricted** | National and tax identifiers, bank details, compensation, health-related records (e.g., incapacity), termination reasons, disciplinary data | Field-level access permission; field-level encryption candidate; reads audited; never in logs, events, or lineage values; strongest retention controls |
| **S Secrets** | Credentials, tokens, keys | Secret store only; never in the database in plaintext, logs, or the repository |

Incapacity-related information is treated as T3 by default because it implies health data ([PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) SEC-02).

## 3. Threat model (summary)

Boundaries: browser to API; API to database; API to connectors/downstream; admin console and simulator; repository and CI; AI agents working on the repo.

| Threat | Where | Mitigation |
|---|---|---|
| Cross-tenant data access | Every endpoint and query | Tenant from principal; required in repository layer; row-level security backstop; automated cross-tenant negative tests on every endpoint |
| Insecure direct object references | Employee, event, and attempt IDs in URLs | Authorization on the object, not the route; unguessable IDs are not a control |
| Privilege escalation via role edits | `access` | `MANAGE_ROLES` separated; all role changes audited; no self-elevation; access reviews |
| Mass assignment / over-posting | API DTOs | Explicit DTOs; no direct entity binding; write-context on all writes |
| Injection | API, search, explorer filters | Parameterized queries; validated filters; output encoding in UI |
| SSRF via connector URLs | Connector configuration | Allow-listed schemes/hosts, block internal ranges, enforce TLS, no redirects to internal targets |
| Webhook forgery and replay | Inbound webhooks | Signature verification, timestamp and nonce checks, rate limits |
| PII in logs, metrics, events, lineage | Everywhere | Allow-list logging; redaction library; canary tests; metadata-only lineage and explorer by default |
| Replay/retry abuse causing duplicate effects | Ops console | Separate permissions; idempotency gates; audit; halt-and-confirm for non-idempotent destinations |
| Fault-injection misuse | Simulator | Admin-only, demo/dev profile only, visible labels, disabled in any production-like profile |
| Secrets in repository | Repo and CI | Secret scanning in CI; `.env.example` placeholders only; never paste secrets to agents |
| Dependency and supply-chain compromise | Build | Locked dependencies, vulnerability scanning, minimal dependencies (a stated architectural preference) |
| Data leakage via backups | Operations | Encrypted backups; erasure implications documented (section 7) |
| Untrusted content steering an AI agent | Agent workflows | Agents treat fetched or pasted external content as data, not instructions; no secrets or real data in agent context; human review of AI-authored changes ([AGENTS.md](../AGENTS.md)) |

## 4. Authentication ([ADR-0013](adr/0013-authentication.md))

- The API is an **OIDC resource server**: it validates tokens issued by an identity provider and maps them to a principal and tenant. The choice of identity provider is deferred; a local development
  provider is used in Phase 1. *Unverified:* the specific product; the pattern is what is decided.
- Multi-factor authentication is enforced at the identity provider for privileged roles.
- Service-to-service credentials for connectors are **secret references**, rotated, least-privileged, and never shared between tenants.
- Session and token lifetimes are short; refresh handling follows the frontend architecture decision ([ADR-0009](adr/0009-frontend-architecture.md)).

## 5. Authorization ([ADR-0014](adr/0014-authorization.md))

**Model: permission × scope.** A role is a named bundle of permissions; each permission is granted with a **scope type**:

`SELF` · `DIRECT_REPORTS` · `ORG_UNIT_SUBTREE` · `LEGAL_ENTITY` · `TENANT`

"Manager can view an employee" is `VIEW_EMPLOYEE` at `DIRECT_REPORTS`. The owning module (`workforce`) decides whether a target record is in scope; `access` evaluates the grant. Deny by default.
Checks happen at two points: the API layer (permission present at all) and the domain layer (this permission over *this* record). Tenant is checked first, always.

**Permissions.** From the brief: `VIEW_SELF_PROFILE`, `VIEW_EMPLOYEE`, `EDIT_EMPLOYEE`, `APPROVE_JOB_CHANGE`, `MANAGE_COMPENSATION`, `VIEW_AUDIT_LOG`, `VIEW_INTEGRATIONS`, `RETRY_INTEGRATION`, `MANAGE_SYSTEMS`.
Proposed additions: `VIEW_SENSITIVE_FIELDS` (per tier), `VIEW_EVENT_PAYLOAD`, `REPLAY_EVENTS`, `VIEW_LINEAGE`, `MANAGE_CONNECTORS`, `MANAGE_OWNERSHIP_POLICY`, `MANAGE_ROLES`, `RUN_SIMULATOR_SCENARIOS`.

**Roles (DRAFT: the brief says not to finalize RBAC until domain requirements are reviewed).**

| Role | Illustrative grants (permission @ scope) |
|---|---|
| Employee | `VIEW_SELF_PROFILE` @ SELF |
| Manager | `VIEW_EMPLOYEE` @ DIRECT_REPORTS; `APPROVE_JOB_CHANGE` @ DIRECT_REPORTS |
| HR Partner | `VIEW_EMPLOYEE`, `EDIT_EMPLOYEE` @ ORG_UNIT_SUBTREE or LEGAL_ENTITY |
| HR Administrator | `VIEW_EMPLOYEE`, `EDIT_EMPLOYEE`, `MANAGE_OWNERSHIP_POLICY` @ TENANT |
| Payroll Administrator | `MANAGE_COMPENSATION`, `VIEW_SENSITIVE_FIELDS` (limited tiers) @ LEGAL_ENTITY |
| IT Administrator | `VIEW_INTEGRATIONS`, `RETRY_INTEGRATION`, `MANAGE_CONNECTORS` @ TENANT; no HR data beyond what integrations need |
| System Administrator | `MANAGE_SYSTEMS`, `MANAGE_ROLES`, `VIEW_AUDIT_LOG`, `RUN_SIMULATOR_SCENARIOS` @ TENANT; elevated actions separately permissioned and audited |

Separation of duties to preserve: the person who manages roles is not automatically able to read T3 data; the person who can replay events is not automatically able to view unredacted payloads.

## 6. Tenant isolation ([ADR-0003](adr/0003-postgresql-and-tenancy.md))

Layers: (1) tenant derived from the authenticated principal, never from a client-supplied field; (2) tenant required by every repository method; (3) PostgreSQL row-level security as a backstop
(*proposed*; cost and pitfalls to be spiked); (4) automated negative tests: every endpoint is exercised with another tenant's principal; (5) caches and background jobs carry tenant context; (6) logs carry `tenantId`;
(7) support access to a tenant is exceptional ("break-glass"), time-boxed, and audited.

## 7. Data protection, retention, and erasure

- **In transit:** TLS everywhere. **At rest:** encrypted database storage and backups.
- **Field-level encryption** for T3 fields is a candidate, with application-managed keys and a key-management plan (decision deferred; cost: search and sort on those fields).
- **Minimization:** collect only what a concept requires; local supplemental data is research-driven ([DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 4).
- **Masking:** UI shows masked identifiers by default with an audited reveal.
- **Retention and erasure:** obligations are RESEARCH REQUIRED (ID-3, ID-4). Design constraint now: an append-only audit trail and a right to erase can conflict.
  **Candidate mechanism:** store personal values in audit records encrypted under a **per-subject key**; erasure destroys the key, leaving record structure, actor, timestamps, and
  correlation intact but values unreadable ("crypto-shredding"). Alternatives: pseudonymize with a separable lookup table; store hashes only. Trade-offs (key management, backup interplay, legitimate retention duties)
  are to be decided before any real-shaped data is stored ([PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) C-07).
- **Backups:** encrypted, access-limited, restore-tested; erasure semantics for backups documented rather than assumed.

## 8. Audit

Audited: all changes to governed fields (previous value, new value, actor, reason, workflow, correlation ID, source system), role and permission changes, ownership-policy changes, retries/replays/reprocess,
simulator control use, configuration changes to connectors, unredacted payload views, and reads of T3 fields. Audit records are written in the same transaction as the change they describe.
Access to audit is itself audited. Tamper evidence (for example, a hash chain over records) is a candidate to evaluate, not a Phase 1 commitment.

## 9. Safe logging

- Structured logs with an **allow-list** of fields; anything not on the list is dropped.
- Never log T2/T3 values, tokens, secrets, or raw payloads. IDs and correlation IDs are fine.
- Redaction is a shared library, tested with **canary values** in synthetic data: tests fail if a canary appears in any log line.
- Metric labels never contain personal data or unbounded values.

## 10. Secrets management

No secrets in the repository or agent context. Local development uses environment variables from an untracked file, with a committed placeholder-only example when code exists. CI uses the platform's secret store.
Connector credentials are referenced, not embedded. Rotation is possible without redeploying code. Secret scanning runs in CI from Phase 1.

## 11. Input validation and API hardening

Bean Validation at the edge; domain invariants inside; request-size and pagination limits; strict content types; RFC 9457 error responses that do not leak internals; rate limiting per principal and per tenant
(stricter on authentication, inbound webhooks, replay); locked-down CORS; standard security headers; no wildcard permissions in client code. Dependency and image scanning in CI.

## 12. Integration security

See [INTEGRATION_ARCHITECTURE.md](INTEGRATION_ARCHITECTURE.md) section 10: SSRF controls, webhook verification, least-privilege service accounts, TLS, payload redaction, tenant-scoped connectors, raw-landing retention and encryption.

## 13. Simulator and demo safety

Fault injection is admin-only and enabled only in demo/dev profiles. Simulated systems are labelled everywhere. Demos use only synthetic data (section 14). A production-like profile refuses to start with
simulator features enabled.

## 14. Synthetic data policy

- Every demo and test person is synthetic and generated deterministically from a seed.
- **Identifiers are invalid by construction** (a format or check that no real issuer would produce), so a fixture can never describe a real person. The specific construction depends on the identifier formats researched in ID-1.
- Email addresses use reserved non-routable domains (Phase 1 uses `.example` and `example.test`); phone numbers use reserved fictional ranges where available.
- Synthetic records carry an explicit marker so they can never be mistaken for real records or leak into a real tenant.
- No real names, no scraped data, no employer data, no screenshots of real systems.

## 15. Access reviews

Provide a periodic export of role assignments and elevated-permission holders per tenant, a review workflow that records reviewer and decision, and alerts on grants of high-impact permissions
(`MANAGE_ROLES`, `VIEW_SENSITIVE_FIELDS`, `REPLAY_EVENTS`, `VIEW_EVENT_PAYLOAD`). Full workflow after Phase 1.

## 16. Compliance posture

No claims. Recorded for a possible business: data-protection compliance program, sub-processor management, data-residency options, vendor security questionnaires, penetration testing, and possible certifications are future work and
not assertions of current state ([PHASE0_CRITIQUE.md](PHASE0_CRITIQUE.md) BIZ-03).

## 17. What appears in Phase 1

Status as built and verified on 2026-09-19 (evidence: [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 1).

| Control | Phase 1 |
|---|---|
| Tenant on every tenant-owned table and in every repository method; composite tenant-qualified foreign keys; a cross-tenant id answers exactly like a missing one; negative tests on every endpoint | **Implemented** |
| Authentication: OpenID Connect authorization-code login (Keycloak in development) with a **backend-held session**; no token reaches JavaScript. Session cookie `HttpOnly`, `SameSite=Lax`, `Secure` outside `dev`, 30-minute idle timeout; session id changes at login; CSRF token in a readable cookie echoed in a header on API writes or the native logout form parameter; no CORS (single origin); framework default security headers | **Implemented.** This answers SQ-03 provisionally (backend session rather than browser-held tokens) and replaces the "resource server" wording in section 4 for Phase 1 ([ADR-0022](adr/0022-phase1-technical-baseline.md) decision 7). A bearer-token resource server is added when a non-browser client exists |
| Tenant and roles from a server-side principal mapping on `(issuer, subject)`, resolved on every request; a valid identity with no mapping is denied everywhere; suspended tenants and disabled principals are denied on the next request | **Implemented** |
| Authorization: permission set (`VIEW_PEOPLE`, `VIEW_PERSON`, `CREATE_PERSON`, `EDIT_PERSON`, `VIEW_EMPLOYMENT`, `EDIT_EMPLOYMENT`, `VIEW_AUDIT_HISTORY`) bundled into code-defined roles (`HR_ADMIN`, `HR_VIEWER`); enforced in application services on every endpoint | **Implemented, tenant-wide scope only.** The "permission x scope" model is carried in the actor type but narrower scopes (own record, direct reports, org unit) are not built |
| Audit written in the same transaction as the change; append-only (a database trigger rejects update, delete and truncate); only permitted users read it | **Implemented.** Reads of history are not themselves audited (section 8 aspires to that) |
| Command idempotency with `Idempotency-Key`, scoped to tenant, principal and operation; request fingerprint; receipts hold no personal data | **Implemented** |
| Input validation: one domain implementation; unknown fields rejected (including a client-supplied `tenantId`); per-field length limits and paging limits; RFC 9457 errors that reveal no internals | **Implemented** (Bean Validation not used; see ADR-0022 decision 4). **64 KiB limit** on POST/PUT/PATCH `/api/` bodies, including unknown/chunked length; neutral 413 response. Global proxy/header/connection limits remain deployment work |
| Allow-list style logging: fixed context fields only, route templates instead of paths, canary test that personal values never appear | **Implemented.** Review fixed framework DEBUG leakage with explicit INFO defaults for web/security/JDBC logging; operator overrides and database/proxy logs require separate care |
| Synthetic-only data | **Implemented.** Fictional names and organizations; emails on reserved domains (`.example`, `example.test`); the Phase 1 model holds no national identifier, birth date, bank, compensation or health field, so invalid-by-construction identifiers are not yet needed |
| Secret handling | **Implemented.** `.env` untracked, `.env.example` placeholders only; no real credential in the repository |
| Row-level security backstop | **Deferred** (not spiked) ([ADR-0022](adr/0022-phase1-technical-baseline.md) decision 9) |
| Rate limiting | **NEXT SECURITY MILESTONE**, before any public hosted demo; choose trusted proxy/tenant identity and observable limits |
| Secret scanning and dependency scanning in CI | **Prepared, not executed on GitHub.** Pinned Gitleaks, npm audit, public-PR dependency review, and Maven/npm/Actions Dependabot; hosted settings and full Java dependency coverage remain unverified |
| Field-level encryption; crypto-shredding audit | Design decision before real-shaped data; later |
| Access review workflow | Later |

## 18. Open questions

- **SQ-01** Row-level security: worth the operational cost at this scale? Deferred in Phase 1: application enforcement, composite foreign keys and negative tests are in place; row-level security needs a non-superuser runtime role and per-transaction tenant binding ([ADR-0022](adr/0022-phase1-technical-baseline.md) decision 9).
- **SQ-02** Erasure mechanism (per-subject keys vs. pseudonymization) and its interaction with backups. Blocked partly by ID-3/ID-4 research.
- **SQ-03** Frontend token handling: **provisionally answered** in Phase 1 with a backend-managed session and no browser-held tokens ([ADR-0022](adr/0022-phase1-technical-baseline.md) decision 7, awaiting Daniel's confirmation).
- **SQ-04** Which fields are T3 in the Costa Rica context, pending research.

## 19. Codex review evidence and deployment boundary

The [Phase 1 review](PHASE_1_CODEX_REVIEW.md) records source inspection, meaningful negative tests, and final verification. Request-size limiting is **IMPLEMENT NOW** and complete. Rate limiting is **NEXT SECURITY MILESTONE**, required before public hosting even with synthetic data. Production retention/erasure research, backup/restore, least-privilege database roles, encryption decisions, and session operations are **PRE-PRODUCTION ONLY** work; none is implied complete. RLS remains deferred subject to Daniel's Q1 decision, without weakening application isolation.

Logout now uses a CSRF-protected native POST form and the framework's OIDC redirect. A token-bearing logout URL is no longer returned in JSON to application JavaScript. Standard provider-directed browser navigation still carries an ID-token hint; this distinction matters when describing the token boundary.

Local npm audit reported zero vulnerabilities. Gitleaks scanned publication-candidate source files with no findings; this workspace has no Git history, so historical secrets could not be checked. No real credential or employer-confidential material was identified. Development/test passwords are explicit synthetic placeholders. These checks are not a guarantee that source or dependencies contain no vulnerability.

Before publication validation, run [security.yml](../.github/workflows/security.yml) on the authorized repository, confirm the dependency graph and Java transitive coverage, and review native secret scanning/push-protection availability and settings. No hosted setting was enabled here. Dependency review applies to public pull requests in the prepared workflow; license checks remain disabled pending Daniel's decision. Gitleaks Action assumes personal repository ownership; reassess licensing if moved to an organization. A full transitive Java advisory scan remains outstanding.
