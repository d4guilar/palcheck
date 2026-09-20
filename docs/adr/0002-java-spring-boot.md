# ADR-0002: Java and Spring Boot

**Status:** Proposed
**Date:** 2026-09-19

## Context

The backend needs a mature ecosystem for transactional business software: persistence, validation, security (OIDC resource server), scheduling, and testing. Daniel also states an explicit goal: use PalCheck to strengthen
enterprise Java/Spring engineering experience. That is a legitimate input to a personal portfolio project and is recorded here honestly instead of dressed up as a purely technical conclusion.

## Decision

Use **Java** with **Spring Boot** for the backend. Adopt Spring components **only when justified**: Spring Web, Spring Security (OIDC resource server), Spring Data (or a thinner data-access layer if it reads better), Bean Validation, Flyway for migrations,
JUnit and Testcontainers for tests. Version choices (current LTS Java, current Spring Boot) are made at the start of Phase 1 and verified then; nothing is pinned here.

Guidance:

- Keep domain logic **framework-light**: invariants and state machines are plain Java that can be unit-tested without the container.
- Prefer explicit code over annotation magic where behavior is important to understand (transaction boundaries, authorization checks, outbox writes).
- DTOs for the API and provider boundaries; domain objects inside modules. Do not force DTO/domain mapping where it adds nothing.
- No framework is added for résumé value. Evaluating Spring Modulith, MapStruct, or a mapping/rules library requires its own justification.

## Alternatives Considered

| Alternative | Assessment |
|---|---|
| **Kotlin on the JVM (Spring)** | Excellent fit and less boilerplate; but the stated goal is Java/Spring depth, and Java records/sealed types close much of the gap |
| **.NET (ASP.NET Core)** | Strong for enterprise and HR products; not aligned with the goal and would forgo Spring-specific learning |
| **Node.js/TypeScript full-stack** | Fastest to iterate, shared types with the frontend; weaker fit for the modular, transactional, enterprise-Java demonstration goal |
| **Quarkus / Micronaut** | Good startup/footprint; smaller ecosystem mindshare for the intended audience |
| **Go / Python** | Not aligned with the goal; Python is weaker for long-lived transactional modular systems |

## Consequences

**Positive**

- Mature, well-documented ecosystem; widely recognized by enterprise employers and interviewers.
- Strong support for transactions, security, validation, scheduling, and testing with real dependencies.
- Direct relevance to the career goal.

**Negative**

- Heavier runtime and slower startup than some alternatives; more boilerplate than Kotlin or TypeScript.
- Spring's convenience can hide behavior (proxies, lazy loading, transaction propagation) and create "magic"; this is a stated thing to avoid.
- Two-language project (Java + TypeScript) means contracts must be shared deliberately ([ARCHITECTURE.md](../ARCHITECTURE.md) repository structure).

## Reconsider When

- The Java/Spring learning goal changes or is met and a lighter stack would serve a real business better.
- Startup time or memory becomes a real constraint (serverless, tiny instances).
- A specific required library or integration is materially better supported elsewhere.

## Interview line

"Spring Boot is the mainstream enterprise Java stack and it's the depth I wanted to build; I keep domain logic framework-light and treat Spring as infrastructure so the design isn't hostage to the framework."

## Related Decisions

[ADR-0001](0001-modular-monolith.md) · [ADR-0003](0003-postgresql-and-tenancy.md) · [ADR-0009](0009-frontend-architecture.md)
