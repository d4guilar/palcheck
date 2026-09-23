# ADR-0009: Frontend Architecture

**Status:** Accepted in part (Daniel Aguilar, Q1, 2026-09-21): decision 8's backend OIDC session choice is accepted; other proposals retain their prior status.
**Date:** 2026-09-19

> **Implementation note (2026-09-19):** Phase 1 built the frontend as decided in [ADR-0022](0022-phase1-technical-baseline.md) decision 11 (React, TypeScript, Vite, native controls first, CSS custom-property tokens, `react-intl`, TanStack Query, no component library) with a backend-managed session instead of browser-held tokens. Only the workforce screens exist; the operations console is not started. Original reasoning below is retained.

## Context

The brief specifies React and TypeScript with a premium, accessible, token-based interface. The product has two very different surfaces: a calm HR interface (people, hiring, learning about local concepts) and a technical operations console (delivery attempts, events, health, lineage).
Both need strong accessibility, internationalization-readiness, locale-aware formatting, and careful motion. The UI must talk only to the API and must never assume anything about jurisdiction rules ([UI_SYSTEM.md](../UI_SYSTEM.md)).

## Decision

1. **A single-page application** in `web/`: React + TypeScript (strict mode), built with a modern bundler (Vite is the default candidate). SSR/Next.js is not adopted unless a real need (SEO, first-load performance for a public site) appears; PalCheck's app is authenticated.
2. **API types generated from the OpenAPI contract**, so client and server cannot drift silently.
3. **Server state via a query/cache library** (TanStack Query is the default candidate); minimal client state; no global store unless a need is demonstrated.
4. **Accessible headless components + PalCheck tokens** instead of a styled component library. Candidates: **React Aria Components** (strong accessibility and internationalization, including locale-aware date and number handling) or **Radix Primitives**; decided by a Phase 1 spike against the checklist in [UI_SYSTEM.md](../UI_SYSTEM.md) 9. *Unverified:* current capabilities of each.
5. **Tokens as the styling runtime**: CSS custom properties generated from a token file; light, dark, high-contrast, and forced-colors supported ([UI_SYSTEM.md](../UI_SYSTEM.md) 4). A utility-CSS framework is optional, not required.
6. **Internationalization from the first component**: all user-visible strings externalized through an ICU-style message-format library (choice in Phase 1; e.g., FormatJS or i18next); `Intl` for dates, numbers, currency using the user's locale; **locale is presentation, jurisdiction is law** and they are separate inputs. Native legal terms come from the registry, not UI bundles.
7. **Motion**: state-driven CSS for simple transitions; a spring-based library only for gesture-driven interactions (sheets, reorder) where interruptibility matters; `transform`/`opacity` only; reduced-motion variants required.
8. **Authentication in the browser: ACCEPTED through Q1 on 2026-09-21.** Follow [ADR-0013](0013-authentication.md) and [ADR-0022](0022-phase1-technical-baseline.md) decision 7: a same-origin backend OIDC session keeps OAuth tokens out of application JavaScript. SQ-03 is resolved; no separate backend-for-frontend service is introduced.
9. **Testing**: unit and component tests (Vitest and Testing Library are default candidates), end-to-end tests (Playwright), and automated accessibility checks (axe-class) in CI plus manual keyboard and screen-reader passes.
10. **Contracts, not shared code**: `web/` consumes generated types; no Java/TypeScript code sharing.

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **Server-rendered UI (Thymeleaf/HTMX)** | Simplest and genuinely viable for forms and tables; the brief mandates React + TypeScript, and the console and gesture-driven interactions benefit from a rich client |
| **Next.js / SSR** | Adds a Node runtime and rendering model with no current need; authenticated app |
| **Material UI / Ant Design / a full component library** | Fast, but imposes a visual identity and a customization burden; the goal is a deliberately designed system built on tokens |
| **Tailwind-only with copy-paste components** | Workable and popular; fine as an implementation detail, but accessibility and i18n primitives still need a base layer |
| **Vue / Svelte** | Good frameworks; off the brief and less aligned with the intended audience |
| **Micro-frontends** | Solves organizational scaling PalCheck does not have |

## Consequences

**Positive**

- Accessibility and internationalization come from a tested base; visual identity comes from tokens.
- Generated types prevent contract drift; the SPA stays a thin client over a well-defined API.
- Spanish UI later is an addition, not a rewrite.

**Negative**

- More assembly than a full library; more design and component work up front.
- SPA has weaker first-load and SEO characteristics (acceptable here); token handling in the browser needs care.
- Two ecosystems (JVM and Node) to keep current and secure.

## Reconsider When

- A public marketing or documentation site is needed (use a separate static site, not this SPA).
- The token/session approach proves awkward for real SSO customers.
- Headless base choice blocks a required accessibility or i18n capability.

## Interview line

"A thin, accessible React client generated from the API contract: headless components for correctness, PalCheck's own tokens for identity, every string externalized so Spanish is an addition, and locale kept separate from jurisdiction."

## Related Decisions

[ADR-0010](0010-ui-principles-and-accessibility.md) · [ADR-0013](0013-authentication.md) · [UI_SYSTEM.md](../UI_SYSTEM.md)
