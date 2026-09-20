# PalCheck — Master Project Brief

> **Author:** Daniel Aguilar (final authority on this project).
> **Status of this file:** Daniel's original brief, preserved verbatim below the line marked
> `BEGIN ORIGINAL BRIEF`. Do not edit the original text. Changes agreed after the brief was written are
> recorded in **Amendments** at the bottom of this file and, where architectural, in an ADR under
> [docs/adr/](docs/adr/README.md). Where an Amendment or an Accepted ADR conflicts with the original text,
> the Amendment / Accepted ADR wins.

---

## Amendments

| # | Date | Amendment | Recorded in |
|---|------|-----------|-------------|
| A1 | 2026-09-19 | **Primary design driver is standalone-first.** PalCheck is designed first as the system of record for a small organization. Enterprise-overlay mode (external HRIS as system of record) is a future-compatible seam, not the primary driver. Chosen by Daniel over the reviewer's overlay-first recommendation. | [ADR-0017](docs/adr/0017-primary-product-mode.md) |
| A2 | 2026-09-19 | Repository tree in this brief is a *target*, not a Phase 0 deliverable. Only documentation exists in Phase 0; code directories are created when Phase 1 needs them. `workers/integration-worker` is deferred (same deployable, separate profile, until a reason to split exists). | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| A3 | 2026-09-19 | Module list refined from 13 to 7 modules + 2 deferred. | [docs/MODULES.md](docs/MODULES.md), [ADR-0001](docs/adr/0001-modular-monolith.md) |
| A4 | 2026-09-19 | Message broker: default is **no broker yet** (transactional outbox + in-process dispatcher behind a port), with explicit exit criteria for adopting Kafka or Redpanda. | [ADR-0008](docs/adr/0008-broker-none-yet.md) |
| A5 | 2026-09-19 | `docs/DATA_LINEAGE.md` is replaced by `docs/DATA_OWNERSHIP_AND_LINEAGE.md` (ownership and lineage are one model). The four Costa Rica topic files are merged into `docs/localization/costa-rica/RESEARCH_BACKLOG.md`. | [docs/DATA_OWNERSHIP_AND_LINEAGE.md](docs/DATA_OWNERSHIP_AND_LINEAGE.md) |

---

`BEGIN ORIGINAL BRIEF`

You are the primary architecture and product-design agent for a serious software engineering project called PalCheck.

PalCheck is an independent personal project created and owned by Daniel Aguilar.

It is NOT affiliated with Apple, Microsoft, Workday, Equifax, SAP, Oracle, ServiceNow, or any other employer, HR vendor, or technology company.

The project has two goals:

1. Build an unusually strong software engineering portfolio project demonstrating enterprise architecture, HR systems integration, backend engineering, full-stack development, event-driven systems, reliability, observability, security, localization, and product ownership.

2. Preserve the possibility that PalCheck could later evolve into a real business focused on Latin American workforce infrastructure.

THIS FIRST PHASE IS ARCHITECTURE AND PRODUCT DESIGN.

Do NOT begin implementing production features.

Do NOT scaffold the full application yet.

Your responsibility is to challenge, refine, document, and structure the system before implementation begins.


==================================================
PRODUCT NAME
==================================================

PalCheck

Name origin:

"Pal" + "Paycheck"

The name is intentionally memorable and friendly.

The product itself must feel serious, premium, reliable, and enterprise-capable.


==================================================
WORKING POSITIONING
==================================================

PalCheck — Local rules. Global-grade infrastructure.

Core product idea:

PalCheck is a Latin American workforce translation and orchestration platform.

It should be capable of operating in two modes:

1. STANDALONE WORKFORCE PLATFORM

For smaller organizations that do not already operate a large global HRIS.

PalCheck may act as the main system for:

- employees
- employment records
- organization
- lifecycle workflows
- compensation concepts
- leave
- onboarding
- offboarding
- approvals
- integrations
- audit history
- localized HR concepts

2. ENTERPRISE LOCALIZATION / ORCHESTRATION LAYER

For organizations already using systems such as:

- Workday
- SAP SuccessFactors
- Oracle HCM
- other global HR platforms

PalCheck should be able to sit alongside those systems.

It can:

- consume employee and organizational data
- normalize external HR data
- preserve the external system of record
- add Latin American country-specific context
- orchestrate localized workflows
- expose localized concepts
- trigger downstream integrations
- synchronize appropriate results
- explain local HR concepts to international users

This distinction is fundamental.

PalCheck should not assume it must replace the enterprise HRIS.

It may instead complement it.


==================================================
LATIN AMERICA WORKFORCE TRANSLATION VISION
==================================================

A central long-term idea is that global enterprise HR systems often represent workforce concepts through generalized global abstractions.

Latin American countries frequently share broadly similar categories of employment concepts while differing significantly in:

- terminology
- calculation rules
- eligibility
- legal basis
- deadlines
- employment practices
- payroll behavior
- social security systems
- termination rules
- leave policies

PalCheck should eventually provide a normalized global workforce model plus country-specific policy packs.

Conceptual model:

External HRIS
      |
      v
PalCheck Translation Layer
      |
      +--> Normalized Workforce Model
      |
      +--> Country Policy Pack
      |
      +--> Local Workflow Orchestration
      |
      +--> Downstream Systems

Possible countries in the future:

Costa Rica
Mexico
Colombia
Panama
Guatemala
El Salvador
Dominican Republic
Chile
Argentina
Peru
others

IMPORTANT:

Costa Rica is the INITIAL localization target.

Do NOT implement other countries yet.

Do NOT assume rules are identical between countries.

Architect for extension.

Do not prematurely build generic abstractions before real differences are understood.


==================================================
PRIMARY LANGUAGE
==================================================

PalCheck's primary product language is English.

Code, APIs, architecture documentation, developer documentation, UI labels, and technical terminology should default to English.

However, localized employment concepts should preserve authentic native-language terminology when translation would remove important meaning.

Examples from Costa Rica:

Aguinaldo
Cesantía
Preaviso
Incapacidad
Jornada laboral
CCSS

These terms should not be replaced by misleading generic English concepts.

Use:

Native term
+
English explanation

Example:

Aguinaldo
Mandatory annual bonus — Costa Rica

The system should be understandable to:

- Latin American HR professionals
- engineers
- international recruiters
- enterprise architects
- technical interviewers
- HR technology professionals
- product managers unfamiliar with local employment law


==================================================
CORE PRINCIPLE: TRANSLATE, DO NOT DISTORT
==================================================

PalCheck should distinguish between:

GLOBAL WORKFORCE PRIMITIVES

and

LOCAL EMPLOYMENT CONCEPTS.

Examples of global primitives:

Person
Employee
Employment
Position
Organization
Manager
Compensation
Leave
Termination
Payroll Event
Provisioning Event

Examples of localized concepts:

Aguinaldo
Cesantía
Preaviso
CCSS
country-specific leave
country-specific deductions
country-specific payroll obligations

PalCheck should translate between global HR representations and local concepts without pretending they are identical.

This translation layer is one of the core architectural differentiators of the product.


==================================================
LEGAL SAFETY
==================================================

NEVER invent employment law.

Do NOT invent:

- statutory percentages
- payroll formulas
- social security contribution rates
- tax rules
- severance formulas
- vacation accrual formulas
- overtime formulas
- eligibility rules
- legal deadlines
- compliance requirements

All country-specific legal behavior must eventually be researched from authoritative sources.

Until verified, mark legal concepts as:

UNVERIFIED
RESEARCH REQUIRED
NOT IMPLEMENTED

Separate:

1. DOMAIN CONCEPT

What is this concept?

2. LEGAL RULE

What does current law actually require?

3. SOFTWARE BEHAVIOR

How does PalCheck represent or process it?

Never confuse these layers.


==================================================
LEGAL CONCEPT REGISTRY
==================================================

Design PalCheck to eventually support a Legal Concept Registry.

Conceptual model:

CountryPolicyConcept

id
country
nativeName
englishName
category
summary
detailedExplanation
legalBasis
sourceReference
effectiveFrom
effectiveTo
researchStatus
implementationStatus
lastVerifiedAt
version

Potential statuses:

UNRESEARCHED
RESEARCHING
VERIFIED
DEPRECATED

Implementation:

DOCUMENTED
PARTIAL
IMPLEMENTED
DISABLED

This registry should make PalCheck educational to international users.

Example:

Aguinaldo

English:
Mandatory Annual Bonus

Country:
Costa Rica

Category:
Compensation

Explanation:
A country-specific statutory compensation concept that must not be treated as a discretionary performance bonus.

Legal Research:
Not yet verified

Implementation:
Documentation only


==================================================
COSTA RICA INITIAL DOMAIN
==================================================

Costa Rica is the first localization target.

Potential future concepts to research:

- Aguinaldo
- Vacaciones
- Incapacidades
- Jornadas laborales
- Horas extra
- CCSS
- Deducciones
- Preaviso
- Cesantía
- Liquidaciones
- Feriados
- Contract types
- CRC compensation
- Employer structures
- Payroll concepts

Do not implement statutory behavior during Phase 0.

Only establish architectural boundaries and research documentation.


==================================================
CORE PRODUCT AREAS
==================================================

PalCheck should eventually support:

People
Organizations
Employment
Lifecycle
Compensation
Time & Leave
Workflow
Country Policies
Integrations
Identity
Provisioning
Notifications
Audit
Enterprise Systems
Administration


==================================================
EMPLOYEE LIFECYCLE
==================================================

Potential employee lifecycle:

Candidate / Pre-Hire
→ Hire
→ Onboarding
→ Active Employment
→ Job Change
→ Compensation Change
→ Leave
→ Payroll Events
→ Provisioning
→ Offboarding

Example hire workflow:

Employee hired
→ employee record created
→ payroll enrollment requested
→ identity provisioning requested
→ equipment request created
→ benefits workflow started
→ manager notified
→ audit event recorded

These downstream operations may fail independently.


==================================================
ARCHITECTURAL PHILOSOPHY
==================================================

PalCheck must NOT become a technology showcase where unnecessary tools are added to impress recruiters.

Every significant decision must answer:

1. What problem does this solve?
2. Why this approach?
3. What alternatives were considered?
4. What tradeoffs exist?
5. What would make us change the decision?
6. What operational burden does this introduce?
7. Does the complexity justify itself?
8. Can Daniel explain it in an interview?

Prefer:

clear
simple
defensible
maintainable
observable
testable
evolvable

Avoid:

premature abstraction
premature distribution
architecture for resume optics
technology accumulation
magic behavior
unexplained frameworks


==================================================
INITIAL ARCHITECTURE
==================================================

Start with a:

MODULAR MONOLITH

Do NOT default to microservices.

Reasons:

- one main developer
- easier local development
- lower operational complexity
- easier transactions
- easier debugging
- cheaper deployment
- simpler testing
- domain boundaries can still be strong
- modules can later be extracted

The code should nevertheless have strong domain boundaries.

Potential modules:

People
Organizations
Employment
Lifecycle
Compensation
TimeAndLeave
Workflow
CountryPolicies
Integrations
Identity
Notifications
Audit
EnterpriseSystems

Challenge these boundaries.

Do not accept them blindly.


==================================================
BACKEND
==================================================

Primary backend:

Java
Spring Boot

Potential technologies:

Spring Boot
Spring Web
Spring Security
Spring Data
Bean Validation
PostgreSQL
Flyway
JUnit
Testcontainers

Use only when justified.

Daniel wants PalCheck to strengthen enterprise Java/Spring engineering experience.

Preferred properties:

- explicit module boundaries
- clear dependency direction
- validation
- testability
- DTO/domain separation where useful
- understandable APIs
- error handling
- transactional consistency
- clear service responsibilities
- low accidental complexity


==================================================
FRONTEND
==================================================

Frontend:

React
TypeScript

The UI should draw inspiration from Apple's design philosophy and Human Interface Guidelines.

DO NOT:

- copy Apple assets
- use Apple logos
- recreate macOS pixel-for-pixel
- imply Apple affiliation

Use inspiration in:

- spacing
- typography
- restraint
- hierarchy
- accessibility
- clarity
- motion
- precision
- consistency

Visual personality:

premium
calm
precise
modern
minimal
human
enterprise-ready

Avoid generic AI-generated SaaS design:

- purple gradients
- glowing borders
- random glassmorphism
- giant cards
- meaningless charts
- excessive rounded rectangles
- decorative metrics
- pointless animations

The interface should look deliberately designed.


==================================================
DESIGN SYSTEM
==================================================

Create a token-based design system eventually supporting:

light mode
dark mode
responsive layouts
keyboard navigation
screen readers
semantic HTML
reduced motion
internationalization
locale-aware dates
locale-aware currency
high contrast

Use semantic color:

green = success
amber = warning
red = error
blue = system/interactivity
neutral = ordinary UI


==================================================
EXTERNAL HRIS INTEGRATION
==================================================

A central PalCheck capability is integration with existing HR systems.

Architecture:

External HRIS
      |
      v
Provider Adapter
      |
      v
Normalized PalCheck Contract
      |
      v
PalCheck Domain

External provider models must not leak throughout the domain.

Initial demonstration provider:

WORKDAY

This must be a demonstration/reference connector.

Do NOT imply official Workday partnership or certification.

Never use:

- real employer credentials
- production tenant IDs
- real employee records
- confidential Equifax integration details
- proprietary employer configuration

Use:

- mock Workday mode
- synthetic data
- public/documented API behavior
- legitimate personal sandbox only if one later exists

Potential Workday reference connector scope:

- worker import
- worker updates
- manager sync
- organization sync
- job changes
- employment status changes
- termination events
- field mapping
- sync history
- retry handling
- data ownership
- data lineage

The connector should support a MOCK MODE first.


==================================================
HRIS CONNECTOR FRAMEWORK
==================================================

Workday must NOT be hardcoded throughout PalCheck.

Design a provider-neutral connector boundary.

Possible concept:

HRISConnector

with future adapters:

WorkdayConnector
SAPSuccessFactorsConnector
OracleHCMConnector

Do not actually implement SAP or Oracle yet.

Only ensure the architecture does not prevent them later.


==================================================
SYSTEM OF RECORD OWNERSHIP
==================================================

PalCheck must understand source-of-truth ownership.

Example:

Job Title

Source of Truth:
Workday

PalCheck may display and use the value.

It should not silently overwrite externally owned data.

Fields should eventually support metadata such as:

sourceSystem
externalField
externalId
lastSynchronizedAt
ownershipMode

Potential ownership:

PALCHECK
EXTERNAL
DERIVED


==================================================
DATA LINEAGE
==================================================

A major enterprise portfolio feature should be data lineage.

Example:

Workday.BusinessTitle
      |
      v
Workday Adapter
      |
      v
Normalized Employment Contract
      |
      v
PalCheck Employment
      |
      +--> Identity
      +--> Equipment
      +--> Directory

A technical user should eventually be able to inspect:

source system
source field
transformation
destination field
last sync
downstream consumers
correlation ID


==================================================
EVENT ARCHITECTURE
==================================================

PalCheck should eventually support domain events and integration events.

Potential flow:

Business transaction
      |
      v
Domain event
      |
      v
Transactional outbox
      |
      v
Event publisher
      |
      v
Kafka or Redpanda
      |
      v
Consumers

Do not implement this yet.

Do not automatically choose Kafka.

Document whether Kafka or Redpanda is justified.

Potential events:

EmployeeCreated
EmployeeHired
EmploymentStarted
JobChanged
CompensationChanged
LeaveRequested
LeaveApproved
EmployeeTerminated
ProvisioningRequested
ProvisioningCompleted
IntegrationFailed
ExternalWorkerUpdated

Event metadata:

eventId
eventType
schemaVersion
aggregateId
occurredAt
correlationId
causationId
source
payload


==================================================
EVENT RELIABILITY
==================================================

Plan for:

at-least-once delivery
duplicate events
idempotent consumers
retry policies
exponential backoff
dead-letter queues
schema evolution
replay
consumer failure
ordering where relevant
service restart behavior

Never claim exactly-once processing without evidence.


==================================================
TRANSACTIONAL OUTBOX
==================================================

Preferred conceptual pattern:

Database transaction:
business state
+
outbox event

Then:

Outbox Publisher
→ Broker

Reason:

Prevent DB-write-success/event-publication-failure inconsistency.

Document this decision.


==================================================
INTEGRATION SIMULATOR
==================================================

One of the strongest portfolio features will be an enterprise integration simulator.

Simulated downstream systems:

Payroll
Identity Provider
Equipment Management
Benefits
Notifications

Possible statuses:

PENDING
PROCESSING
SUCCEEDED
FAILED
RETRYING
DEAD_LETTERED
CANCELLED

Expose:

attempt count
latency
payload metadata
failure reason
retry schedule
correlation ID
event history
consumer
destination

Allow controlled failure simulation.

Example:

EmployeeHired

Payroll
SUCCESS

Identity
SUCCESS

Equipment
FAILED

Retry

Equipment
SUCCESS

This should make resilience visible.


==================================================
ENTERPRISE SYSTEMS AREA
==================================================

PalCheck should contain an Enterprise Systems area inspired by general enterprise IS&T practices.

This is NOT an Apple clone.

Do not use proprietary Apple terminology or imply affiliation.

The area demonstrates how an internal technology organization operates interconnected systems.


==================================================
SYSTEMS CATALOG
==================================================

Track connected/internal systems.

Example:

Workday
Category: HRIS
Health: Healthy
Last sync: 4 minutes ago

Identity Provider
Category: Identity
Health: Healthy

Payroll CR
Category: Payroll
Health: Degraded

Equipment
Category: IT Operations
Health: Healthy

Potential metadata:

name
type
owner
description
health
authentication type
dependencies
last successful interaction


==================================================
INTEGRATION HEALTH
==================================================

Expose:

success rate
failure rate
latency
throughput
retry count
dead-letter count
last successful sync
current health


==================================================
INTEGRATION TOPOLOGY
==================================================

Provide a visual representation:

Workday
   |
PalCheck
   |
   +--> Payroll
   +--> Identity
   +--> Equipment
   +--> Benefits

Potential statuses:

Healthy
Degraded
Offline
Unknown


==================================================
SCHEDULED JOBS
==================================================

Model enterprise scheduled/background workloads.

Examples:

Worker Synchronization
Organization Synchronization
Audit Archival
Policy Evaluation

Display:

schedule
last execution
next execution
duration
records processed
created
updated
failed
status


==================================================
INCIDENT MANAGEMENT
==================================================

Support a lightweight operations incident model.

Example:

INC-00041
Payroll Integration Failures

Severity:
SEV-2

Status:
Investigating

Affected system:
Payroll CR

Impact:
Employee payroll changes delayed.

Timeline:
Failure threshold exceeded
Incident created
Engineer acknowledged
Root cause identified
Service restored

Do NOT attempt to recreate ServiceNow.

The goal is to demonstrate operational thinking.


==================================================
SERVICE RELIABILITY
==================================================

Allow future exploration of:

SLOs
availability
latency targets
error budgets
health checks

Example:

Worker Sync

Availability target:
99.9%

Latency:
95% < 750ms

Current availability:
99.97%

Use these concepts meaningfully, not decoratively.


==================================================
EVENT EXPLORER
==================================================

Technical users should eventually be able to inspect:

event ID
event type
schema version
correlation ID
causation ID
producer
consumers
status
attempts
timestamps
payload metadata

Do not expose sensitive fields casually.


==================================================
INTEGRATION REPLAY
==================================================

Authorized technical users should eventually be able to retry failed integration attempts.

Example:

INT-192851

Destination:
Identity Provider

Error:
HTTP 503

Attempts:
3

[Retry]

Retry behavior must respect idempotency.

A retry must not accidentally create duplicate accounts or duplicate payroll actions.


==================================================
OBSERVABILITY
==================================================

Plan for:

structured logs
correlation IDs
distributed traces
metrics
health checks
integration metrics
event metrics
retry metrics
dead-letter metrics
latency metrics

Potential stack:

OpenTelemetry
Prometheus
Grafana

Do not implement yet.

Observability should answer:

What happened?
Why?
What request/event caused it?
Where did it fail?
Was it retried?
Did it recover?


==================================================
SECURITY
==================================================

PalCheck models sensitive HR information.

Security is first-class.

Plan for:

authentication
authorization
RBAC
least privilege
audit logs
safe logging
PII handling
secrets management
input validation
rate limiting
organization isolation
data retention
encryption boundaries
sensitive fields
access reviews

Never commit secrets.

Never use real employee records in demos.


==================================================
AUTHORIZATION
==================================================

Potential roles:

Employee
Manager
HR Partner
HR Administrator
Payroll Administrator
IT Administrator
System Administrator

Prefer explicit permissions.

Examples:

VIEW_SELF_PROFILE
VIEW_EMPLOYEE
EDIT_EMPLOYEE
APPROVE_JOB_CHANGE
MANAGE_COMPENSATION
VIEW_AUDIT_LOG
VIEW_INTEGRATIONS
RETRY_INTEGRATION
MANAGE_SYSTEMS

Do not finalize RBAC until domain requirements are reviewed.


==================================================
AUDITABILITY
==================================================

Important HR changes should eventually capture:

actor
timestamp
previous value
new value
reason
workflow
correlation ID
source system

Audit history should be append-oriented.


==================================================
POSSIBLE REPOSITORY STRUCTURE
==================================================

palcheck/
│
├── README.md
├── MASTER_PROJECT_BRIEF.md
├── AGENTS.md
├── CLAUDE.md
│
├── apps/
│   └── web/
│
├── backend/
│   └── palcheck-api/
│
├── workers/
│   └── integration-worker/
│
├── packages/
│   ├── event-contracts/
│   ├── shared-types/
│   └── design-tokens/
│
├── infrastructure/
│   ├── docker/
│   ├── postgres/
│   ├── messaging/
│   ├── observability/
│   └── local/
│
├── docs/
│   ├── PRODUCT_VISION.md
│   ├── ARCHITECTURE.md
│   ├── DOMAIN_MODEL.md
│   ├── INTEGRATION_ARCHITECTURE.md
│   ├── ENTERPRISE_SYSTEMS.md
│   ├── DATA_LINEAGE.md
│   ├── UI_SYSTEM.md
│   ├── SECURITY.md
│   ├── ROADMAP.md
│   ├── AI_COLLABORATION.md
│   ├── WORKLOG.md
│   │
│   ├── localization/
│   │   ├── README.md
│   │   ├── LATAM_STRATEGY.md
│   │   └── costa-rica/
│   │       ├── README.md
│   │       ├── GLOSSARY.md
│   │       ├── LEGAL_RESEARCH_POLICY.md
│   │       ├── COMPENSATION.md
│   │       ├── LEAVE.md
│   │       ├── TERMINATION.md
│   │       ├── WORKING_TIME.md
│   │       └── sources/
│   │
│   ├── adr/
│   │
│   └── diagrams/
│
├── scripts/
│
├── .github/
│   └── workflows/
│
└── docker-compose.yml

You may improve this structure.

Do not create empty complexity unnecessarily.


==================================================
ARCHITECTURE DECISION RECORDS
==================================================

Significant decisions require ADRs.

Format:

# ADR-XXXX: Title

Status:
Proposed / Accepted / Superseded

Date:

Context:

Decision:

Alternatives Considered:

Consequences:

Positive:

Negative:

Reconsider When:

Related Decisions:


Initial ADR candidates:

0001 Modular Monolith
0002 Java + Spring Boot
0003 PostgreSQL
0004 HRIS Adapter Boundary
0005 Source-of-Truth Ownership
0006 Event-Driven Integration Architecture
0007 Transactional Outbox
0008 Kafka vs Redpanda vs No Broker Yet
0009 Frontend Architecture
0010 UI Design Philosophy
0011 Country Policy Architecture
0012 Legal Concept Registry
0013 Authentication
0014 Authorization
0015 Data Lineage
0016 Enterprise Operations Model

Do not create trivial ADRs.


==================================================
DOCUMENTATION STANDARD
==================================================

Explain WHY.

Bad:

"We use PostgreSQL."

Good:

"PostgreSQL fits PalCheck because employment data is highly relational and requires transactional consistency, while retaining mature operational tooling and a realistic path toward production deployment."

Every major component should explain:

purpose
responsibility
dependencies
ownership
failure modes
security
testing
future scaling
tradeoffs


==================================================
PORTFOLIO PURPOSE
==================================================

PalCheck should demonstrate that Daniel can:

- design enterprise systems
- reason about architecture
- build Java/Spring systems
- integrate external platforms
- normalize data
- operate asynchronous workflows
- understand production failures
- build observable systems
- design accessible UIs
- model complex domains
- work with HR technology
- think about internationalization
- understand system-of-record ownership
- document decisions
- make engineering tradeoffs

The project should be impressive because the reasoning is strong, not because it contains the largest possible stack.


==================================================
INTERVIEW READINESS
==================================================

The project should eventually allow Daniel to answer questions such as:

Why modular monolith?

Why not microservices?

Why Java/Spring?

Why PostgreSQL?

How does PalCheck integrate with Workday?

What does PalCheck own versus Workday?

How do you avoid external-provider coupling?

How do you normalize HR data?

How do you model local HR concepts?

How does Costa Rican localization work?

How could Colombia or Mexico be added later?

How do you avoid assuming Latin American countries are identical?

How do retries work?

How do you prevent duplicate processing?

Why transactional outbox?

How would you monitor the system?

How does data lineage work?

How do you handle system failures?

How would you scale it?

How would you extract a module into a service?

How do you protect employee data?

How do you distinguish legal rules from software behavior?


==================================================
AI COLLABORATION
==================================================

PalCheck is collaboratively developed using:

Daniel Aguilar
Claude Code
OpenAI Codex

Daniel is the final authority.

Claude and Codex must communicate through repository documentation rather than assuming shared chat memory.


==================================================
MANDATORY SHARED FILES
==================================================

Before meaningful work, agents must read:

README.md
MASTER_PROJECT_BRIEF.md
AGENTS.md
docs/PRODUCT_VISION.md
docs/ARCHITECTURE.md
docs/DOMAIN_MODEL.md
docs/AI_COLLABORATION.md
docs/WORKLOG.md
relevant ADRs

Claude additionally reads CLAUDE.md.


==================================================
WORKLOG
==================================================

After every meaningful task append to:

docs/WORKLOG.md

Format:

## YYYY-MM-DD HH:MM — CLAUDE

### Task
Requested work.

### Work completed
Files created/modified.

### Decisions made
Important decisions.

### Assumptions
Unconfirmed assumptions.

### Open questions
Unresolved issues.

### Risks
Potential problems.

### Suggested next step
Recommended continuation.


==================================================
COLLABORATION RULES
==================================================

Never silently undo another agent's work.

If you disagree:

1. document the disagreement
2. explain tradeoffs
3. propose an alternative
4. identify impacted ADRs
5. ask Daniel when the disagreement materially changes architecture

Accepted ADRs override individual agent preference.

Do not duplicate completed work.


==================================================
YOUR ROLE: CLAUDE CODE
==================================================

Act primarily as:

senior software architect
enterprise architect
domain modeling reviewer
product architecture critic
documentation owner
localization architecture reviewer

You MUST challenge the specification.

Do not simply agree.

Look for:

- unnecessary complexity
- poor domain boundaries
- weak abstractions
- premature abstractions
- provider leakage
- localization mistakes
- unclear ownership
- security concerns
- architectural contradictions
- portfolio-only complexity
- product assumptions that would block future business use

Pay special attention to PalCheck's identity as:

LATIN AMERICA WORKFORCE TRANSLATION + ORCHESTRATION INFRASTRUCTURE.


==================================================
CURRENT PHASE
==================================================

PHASE 0

Architecture and product definition only.

DO NOT:

- scaffold Spring Boot
- scaffold React
- create production database schema
- build endpoints
- build UI features
- add Kafka
- implement Workday connector

You may create documentation and diagrams only.


==================================================
YOUR FIRST ASSIGNMENT
==================================================

Perform the following IN ORDER:

1. Read this entire brief carefully.

2. Critique it.

Identify:

- contradictions
- overengineering
- weak assumptions
- missing domain concepts
- unclear ownership boundaries
- future scalability concerns
- areas where the Latin America strategy is too vague
- areas where the HRIS translation model needs refinement

3. Propose the final Phase 0 repository structure.

4. Define the initial bounded contexts / modules.

For every module explain:

- responsibility
- owned data
- dependencies
- events produced
- events consumed
- whether it should remain internal or might become a service later

5. Define the external HRIS translation architecture.

Explain:

External HRIS
→ Adapter
→ Normalization
→ PalCheck domain
→ Country policy enrichment
→ downstream orchestration

6. Define the system-of-record ownership model.

7. Define the Latin America localization architecture.

Start with Costa Rica.

Do not attempt to define actual legal rules.

8. Define the Legal Concept Registry architecture.

9. Define the Enterprise Systems area conceptually.

Include:

systems catalog
health
integration topology
scheduled jobs
incidents
event explorer
data lineage
replay
SLO concepts

10. Propose architecture diagrams.

At minimum:

System Context Diagram
Container Diagram
Module/Bounded Context Diagram
External HRIS Integration Diagram
Event Processing Diagram
Country Policy Architecture Diagram
Data Lineage Diagram
Enterprise Systems Topology Diagram

Use Mermaid when practical.

11. Create initial ADR proposals.

12. Create Phase 0 documentation.

13. Create:

docs/WORKLOG.md

and record your work.

14. Create:

docs/CODEX_HANDOFF.md

This file must summarize:

- architecture decisions
- unresolved questions
- areas Codex should challenge
- implementation feasibility concerns
- risky assumptions
- proposed Phase 1 boundary

15. STOP.

Do NOT move to implementation.

At the end, provide Daniel with a concise summary of:

- what you created
- major decisions
- unresolved questions
- recommendations
- what Codex should review next.

`END ORIGINAL BRIEF`
