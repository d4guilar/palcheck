# ADR-0010: UI Principles and Accessibility Commitments

**Status:** Proposed
**Date:** 2026-09-19

## Context

The brief asks for an Apple-inspired, premium, calm interface and lists things to avoid. Without written commitments, "premium" drifts into decoration and accessibility becomes a late audit. This ADR is deliberately short; the substance is in [UI_SYSTEM.md](../UI_SYSTEM.md).

## Decision

1. **Inspired, not imitated.** Apple's public design principles inform spacing, type, hierarchy, and motion. No Apple assets, logos, trademarks, fonts other than the platform `system-ui` stack, or pixel-for-pixel recreation; no implied affiliation.
2. **Accessibility is part of "done":** target **WCAG 2.2 AA**; keyboard operability, visible focus, semantic HTML, no color-only meaning, reflow and text resize, live-region status announcements, and reduced-motion / reduced-transparency / high-contrast / forced-colors support.
3. **Tokens only.** Components use semantic tokens; no magic numbers. Contrast is verified by script, not by eye.
4. **Restraint over decoration.** Every element must help the user understand or act. The brief's anti-pattern list (gradients, glow, gratuitous glass, giant cards, meaningless charts, decorative metrics, pointless animation) is a review checklist.
5. **Materials only on chrome**; motion only where it explains a state change; interruptible; `transform`/`opacity` only.
6. **Honest status display**: legal research status, simulated systems, ownership, and health are always labelled; "verified" green is withheld until professional review.
7. **Every visualization has a text/table equivalent.**

## Alternatives Considered

| Alternative | Why not |
|---|---|
| **No written commitments; iterate on taste** | Quality would depend on mood; accessibility would be audited at the end at high cost |
| **WCAG AAA as target** | Not achievable for all content; AA plus targeted AAA practices (e.g., generous targets) is the honest bar |
| **A styled component library's defaults** | Delegates identity and part of accessibility to a third party ([ADR-0009](0009-frontend-architecture.md)) |
| **Strict Apple HIG conformance** | Not applicable to a web enterprise product and edges toward imitation |

## Consequences

**Positive**

- Design decisions are defensible and reviewable; accessibility is built in rather than bolted on.
- Brand and legal risk from imitation is removed.

**Negative**

- Slower component development; some visually attractive patterns are ruled out.
- AA verification needs tooling and manual testing time.

## Reconsider When

A required interaction cannot meet an AA criterion (document the exception and mitigation); or a design need is blocked by the "tokens only" rule (add a token, do not bypass).

## Interview line

"I took Apple's principles, not its assets: restraint, hierarchy, and precision, with accessibility as part of done, and a rule that nothing decorative survives without answering what it helps the user do."

## Related Decisions

[ADR-0009](0009-frontend-architecture.md) · [UI_SYSTEM.md](../UI_SYSTEM.md)
