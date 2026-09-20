# 06. Country Policy Architecture

Where local knowledge lives, how it gets there, and the gate between facts and behavior. **No legal content appears in these diagrams.**

## Content to behavior

```mermaid
flowchart TB
  subgraph authoring["Authoring (pull requests only)"]
    files["Registry content files<br/>concepts, versions, sources, mappings"]
    ci["CI validation<br/>schema, citations, effective ranges,<br/>status transitions"]
  end

  subgraph runtime["countrypolicy module"]
    reg["Legal Concept Registry<br/>read-only projection<br/>facts, no behavior"]
    gate{"Gate:<br/>VERIFIED and<br/>PROFESSIONAL_REVIEWED?"}
    pack["Policy pack per jurisdiction<br/>manifest + capabilities"]
    jr["Jurisdiction resolver<br/>legal-entity country,<br/>employment country, effective date"]
    assess["PolicyAssessment records<br/>reproducible, reference<br/>concept + pack version"]
  end

  wf["workforce events"]
  out["Annotations, hire requirements,<br/>NOT_AVAILABLE answers"]
  ui["UI: Learn glossary<br/>native term + English explanation<br/>+ status banner"]

  files --> ci --> reg
  reg --> ui
  reg --> gate
  gate -->|"yes: binding allowed"| pack
  gate -->|"no: DOCUMENTED only"| ui
  wf --> jr --> pack --> assess --> out
```

## Registry entities

```mermaid
erDiagram
  CONCEPT ||--o{ CONCEPT_VERSION : "has effective-dated"
  CONCEPT_VERSION }o--o{ LEGAL_SOURCE : "cites"
  CONCEPT ||--o{ CONCEPT_MAPPING : "is mapped by"
  CONCEPT_VERSION ||--o{ BEHAVIOR_BINDING : "may be bound by"

  CONCEPT {
    string id
    string country
    string nativeName
    string englishName
    string category
    string orientationNote
  }
  CONCEPT_VERSION {
    string conceptId
    int versionNumber
    date effectiveFrom
    date effectiveTo
    string researchStatus
    string verificationLevel
    date lastVerifiedAt
  }
  LEGAL_SOURCE {
    string id
    string issuingBody
    string citation
    date retrievedAt
    string contentHash
    string tier
  }
  CONCEPT_MAPPING {
    string fromConcept
    string toTarget
    string equivalence
  }
  BEHAVIOR_BINDING {
    string conceptVersionId
    string packCapability
    string implementationStatus
  }
```

**How to read this.** The three layers are separate entities with separate gates: `CONCEPT` is the domain description, `CONCEPT_VERSION` holds what the law says (once verified), `BEHAVIOR_BINDING` is what PalCheck does. Software can attach behavior only through a binding to a
verified and professionally reviewed version. `CONCEPT_MAPPING.equivalence` is `EXACT`, `PARTIAL`, `NONE`, or `UNKNOWN`; `UNKNOWN` (the default) blocks automatic translation.

Details: [COUNTRY_POLICY_ARCHITECTURE.md](../localization/COUNTRY_POLICY_ARCHITECTURE.md), [LEGAL_CONCEPT_REGISTRY.md](../localization/LEGAL_CONCEPT_REGISTRY.md), [ADR-0011](../adr/0011-country-policy-architecture.md), [ADR-0012](../adr/0012-legal-concept-registry.md).
