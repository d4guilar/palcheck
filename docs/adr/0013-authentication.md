# ADR-0013: Authentication

**Status:** Proposed
**Date:** 2026-09-19

> **Implementation note (2026-09-19):** Phase 1 implements login as OpenID Connect authorization code with a **backend-managed session** rather than a bearer-token resource server, provisionally, per [ADR-0020](0020-foundation-first-delivery-plan.md) decisions 5 and 6 and [ADR-0022](0022-phase1-technical-baseline.md) decision 7. The identity-provider choice stays deferred (Keycloak is the development provider only). Original reasoning below is retained.

## Context

PalCheck holds sensitive HR data and must authenticate people and services. Building credential storage, MFA, and account recovery in-house is high risk and adds no product value. The identity-provider product depends on deployment and customers, which are not yet known.
Real overlay customers will probably want single sign-on with their own corporate identity provider. The brief lists authentication under security; choosing a product now would be premature.

## Decision

1. **The API is an OIDC resource server**: it validates tokens from a configured issuer, maps claims to a principal and a tenant, and never stores passwords.
2. **The identity provider is deferred.** Phase 1 uses a **local development provider** in the local environment. The pattern is decided; the product is not. Candidates to evaluate later (*Unverified*, status at decision time): an open-source self-hosted provider, Spring Authorization Server, or a managed service.
3. **Tenant resolution** comes from a verified claim or a trusted mapping in `access`, never from client-supplied headers or body fields.
4. **MFA** for privileged roles is enforced at the identity provider.
5. **Service accounts** for connectors are secret *references*, least-privileged, rotatable, and never shared between tenants.
6. **Short-lived tokens; revocation and logout behavior documented** when the provider is chosen.
7. **Browser session model** (SPA-held tokens with Authorization Code + PKCE vs. a same-origin session mediated by the backend) is **open** and decided with [ADR-0009](0009-frontend-architecture.md) (SQ-03 in [SECURITY.md](../SECURITY.md)).
8. **Enterprise SSO** (per-tenant federation to a customer's identity provider) is recorded as a likely requirement for overlay customers, not designed now.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Custom username/password in PalCheck** | High security risk and effort, no product value |
| **Choose a specific provider now** | Premature; depends on hosting, cost, and customer needs |
| **API keys only** | Insufficient for human users and for per-user authorization and audit |
| **Session cookies with a bespoke login and no OIDC** | Recreates identity infrastructure; harder SSO later |
| **Mutual TLS as the primary mechanism** | Suits service-to-service, not people |

## Consequences

**Positive**

- No password handling; standard, auditable flows; provider can be swapped without changing the domain.
- SSO for enterprise customers is an extension, not a rewrite.

**Negative**

- Dependency on an external identity provider and its availability; local dev requires running one.
- Multi-tenant identity (per-tenant issuers, claims mapping) is real design work later.
- Token handling in browsers needs careful choices.

## Reconsider When

- A customer requires SAML or a specific federation model.
- The chosen provider cannot express tenant mapping cleanly.
- Frontend session analysis favors the backend-mediated model over SPA-held tokens (adjust, do not abandon OIDC).

## Interview line

"PalCheck never handles passwords: it's an OIDC resource server that maps a verified token to a principal and tenant, and the identity provider is a swappable choice I've deliberately deferred."

## Related Decisions

[ADR-0009](0009-frontend-architecture.md) · [ADR-0014](0014-authorization.md) · [SECURITY.md](../SECURITY.md)
