# ADR-0014: Authorization: Permission × Scope

**Status:** Proposed
**Date:** 2026-09-19

## Context

The brief lists roles (Employee, Manager, HR Partner, HR Administrator, Payroll Administrator, IT Administrator, System Administrator) and explicit permissions (`VIEW_SELF_PROFILE`, `VIEW_EMPLOYEE`, `EDIT_EMPLOYEE`, `APPROVE_JOB_CHANGE`, `MANAGE_COMPENSATION`, `VIEW_AUDIT_LOG`, `VIEW_INTEGRATIONS`,
`RETRY_INTEGRATION`, `MANAGE_SYSTEMS`), and says not to finalize RBAC until domain requirements are reviewed. A flat permission list cannot express "a manager may view *their* reports" or "an HR partner may edit employees *in their legal entity*". The authorization model
must also avoid a dependency cycle: deciding whether a record is in scope requires `workforce` data, but `workforce` uses `access` for authorization.

## Decision

1. **Explicit permissions granted with a scope type**: `SELF`, `DIRECT_REPORTS`, `ORG_UNIT_SUBTREE`, `LEGAL_ENTITY`, `TENANT`. A **role** is a named bundle of `(permission, scope)` grants. **Deny by default.**
2. **Two enforcement points**: the API layer checks the permission exists at all; the **domain layer** checks the permission over *this* record. Tenant is verified first, always.
3. **No cycle**: `access` evaluates grants and returns the scope type; the **owning module** supplies the "is this target in scope?" predicate when it asks `access` to authorize ([MODULES.md](../MODULES.md) section 2).
4. **Sensitive-field access** is a separate permission per data tier (`VIEW_SENSITIVE_FIELDS`), with audited reads for T3 ([SECURITY.md](../SECURITY.md) section 2).
5. **Separation of duties**: managing roles does not imply reading T3 data; replaying events does not imply viewing unredacted payloads.
6. **The permission set and role bundles are DRAFT** ([SECURITY.md](../SECURITY.md) section 5) and are finalized only after Phase 1 domain requirements are reviewed, as the brief requires. Additions to the brief's list are proposed there.
7. **Tests**: a permission-by-scope matrix test per endpoint, plus cross-tenant negative tests.
8. **Changes to roles and grants are audited**, and periodic access reviews are supported (workflow later).

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Flat RBAC (roles to permissions only)** | Cannot express "my reports"; leads to per-role special cases |
| **ABAC with a policy engine (e.g., OPA, Cedar)** | Powerful and decoupled but a new language and runtime; premature; evaluate if scope logic becomes unmanageable |
| **Relationship-based access control (e.g., a Zanzibar-style service)** | Natural for org hierarchies, but a separate stateful service; overkill now |
| **Ad hoc checks scattered in controllers and services** | Inconsistent and unauditable; the top cause of authorization bugs |
| **Authorization only at the API edge** | Misses object-level checks (IDOR) and internal callers |

## Consequences

**Positive**

- Expresses the real HR access patterns with a small, explainable model.
- No module cycle; the module that owns the data owns the scope decision.
- Testable as a matrix and auditable.

**Negative**

- Scope predicates must be written and kept correct in each module.
- Two enforcement points invite inconsistency without helpers and tests.
- Delegation (a manager's delegate), matrix reporting, and consultants spanning units are not modeled yet.
- The DRAFT status means Phase 1 must not hard-code role names across the codebase.

## Reconsider When

- Scope types multiply beyond a handful, delegation or time-boxed access is needed, or a customer requires external policy management (evaluate ABAC/ReBAC).
- Authorization decisions need to be shared with an extracted service.

## Interview line

"Permissions are explicit and carry a scope, so 'manager sees direct reports' is expressible; the module that owns the data decides whether a record is in scope, which avoids a dependency cycle, and everything is deny-by-default and tested as a permission-by-scope matrix."

## Related Decisions

[ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0013](0013-authentication.md) · [SECURITY.md](../SECURITY.md)
