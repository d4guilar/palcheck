# ADR-0001: Modular Monolith

**Status:** Proposed
**Date:** 2026-09-19

> **Amendment (2026-09-19, Daniel-approved refinement; see [ADR-0018](0018-module-transactions-and-persistence.md)):** rule 3, "one command = one transaction = one module", is amended. One application command owns one local transaction, and audit recording and (later) durable publication participate in it through narrow APIs. The dependency direction, including a technical `messaging` package that no domain module is blocked on, is in [PHASE_1_IMPLEMENTATION_PLAN.md](../PHASE_1_IMPLEMENTATION_PLAN.md). The original text below is preserved for its reasoning.

## Context

PalCheck has one main developer, a domain with strong transactional consistency needs (an employment change, its audit record, and its outbox event must commit together), and a stated need for strong boundaries so parts can later
be extracted. It must be cheap to run and easy to debug, and it must be explainable in an interview without hand-waving. The brief already prefers a modular monolith and asks for the module list to be challenged.

## Decision

Build **one deployable Spring Boot application** divided into **seven modules** with explicit public APIs: `workforce`, `lifecycle`, `countrypolicy`, `integrations`, `access`, `audit`, `operations`. Two more (`compensation`, `timeandleave`) are deferred.
A tiny **shared kernel** holds ids, `Money`, `Country`/`Jurisdiction`, `EffectiveRange`, `CorrelationContext`, and `TenantId`. Details in [MODULES.md](../MODULES.md).

Rules:

1. Dependencies between modules are declared and acyclic; interaction is through public APIs and events only.
2. **No cross-module foreign keys or joins**; modules reference each other by ID.
3. **One command = one transaction = one module.** Cross-module effects flow through events.
4. Each module owns a **database schema** and its Flyway migrations.
5. Boundaries are **enforced by automated architecture tests** once code exists (ArchUnit or Spring Modulith verification; choice is a Phase 1 spike).
6. Background work runs in the same artifact under a **profile**; it can be split later without code changes.

## Alternatives Considered

| Alternative | Why not now |
|---|---|
| **Microservices from the start** | Distributed transactions, per-service pipelines and observability, and cross-service debugging would consume the project before the domain is understood; one developer cannot operate that well |
| **Classic layered monolith** (controllers/services/repos across the whole app) | Cheapest, but boundaries erode; extraction later becomes a rewrite; the module discipline is part of what the project is meant to demonstrate |
| **The brief's 13 modules** | Too many APIs and dependency edges for one developer; several are not domains ([PHASE0_CRITIQUE.md](../PHASE0_CRITIQUE.md) O-01) |
| **Fewer, coarser modules (e.g., 3)** | Would hide the real boundaries (integrations vs. workforce vs. country policy) that matter for extraction and localization |
| **Separate repositories per module** | Premature; slows refactoring while boundaries are still moving |

## Consequences

**Positive**

- Simple local development, testing, deployment, and debugging; real database transactions where they matter.
- Boundaries strong enough to extract `integrations` (or others) later without rewriting callers.
- Low operational cost; a realistic path to production for a small team.

**Negative**

- Discipline is only as strong as the enforcement; without architecture tests boundaries erode.
- No cross-module joins means some reads need explicit read models or API calls, and reporting across modules costs more.
- A monolith scales as one unit until parts are extracted; noisy neighbors are possible.
- A module split can turn out wrong; moving code between modules is a refactor, not a config change.

## Reconsider When

- A module needs independent scaling, release cadence, or a different failure/security domain that a profile cannot provide (`integrations` is first).
- Team size grows past the point where one repository and one deployable slow people down.
- Architecture tests are being routinely bypassed (the boundaries are not real).
- The seven-module split shows frequent cross-module change coupling (merge or re-cut).

## Interview line

"One developer and a transactional domain: a modular monolith gives strong boundaries and real transactions without the operational tax of microservices, and I've kept extraction possible by banning cross-module joins and enforcing the rules with tests."

## Related Decisions

[ADR-0002](0002-java-spring-boot.md) · [ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0006](0006-event-driven-integration.md) · [ADR-0008](0008-broker-none-yet.md) · [ADR-0016](0016-operations-model.md)
