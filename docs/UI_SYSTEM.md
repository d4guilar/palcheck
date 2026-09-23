# UI System

> **Phase 2 implemented surfaces (2026-09-22):** Systems, system detail, run history/detail and contextual source disclosure extend the existing native-control UI under `web/src/pages/integrations`. Compact lists, subdued text statuses, native disclosure and existing spacing/color tokens replace dashboard decoration. Read-only ownership is visible beside the affected section; source metadata loads only on demand. Desktop and 320px dark layouts, keyboard disclosure and axe checks are recorded in the [Phase 2 review](PHASE_2_CODEX_REVIEW.md). No component library or new dependency was added.

> **Reconciliation notice (2026-09-19, Phase 1 built):** the workforce foundation ships four screens: sign-in and session states, **People** (search, table, paging), **New person**, and the **person record** (identity, employment, assignment, change history, each part editable on its own). Sections 6 and 7 below are the Phase 0 proposal. What differs: no headless component layer was adopted (native form controls, table, links and buttons with a small token stylesheet were enough; the question returns when a dialog, combobox or date picker is needed); the **Hire** wizard, **Activity** and **Learn** screens are not built (they belong to later milestones); no ownership badges or masked fields yet (only synthetic data is used and everything is `PALCHECK`-owned; the modeled names/emails would be personal data in real use). Implemented from this document: semantic tokens with light, dark, high-contrast, forced-colors, reduced-motion and reduced-transparency support; native-term-first, plain-language messages in one catalog; errors that say what happened and what to do; the accessibility commitments in section 9, verified by automated checks in jsdom and a real browser (axe, WCAG 2.2 AA) plus a keyboard-only journey. Not verified: a manual screen-reader pass. See [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) sections 10 and 17.

Status: baseline **Proposed** ([ADR-0009](adr/0009-frontend-architecture.md), [ADR-0010](adr/0010-ui-principles-and-accessibility.md)); the Phase 1 UI is implemented (see the notice above). This document sets constraints and direction, not pixels.

## Implemented Phase 1 review refinements

The dedicated [UI polish pass](UI_PHASE_1_APPLE_POLISH.md) builds on those refinements. PalCheck is inspired by general modern Apple/HIG design principles, but is an original enterprise web application, not an Apple product or replica. It uses the user's native system fonts, original CSS and a simple original search icon; it bundles no proprietary font, icon library or brand asset.

The implemented direction is **clarity, restraint and hierarchy**: one directory toolbar; neutral surfaces; typography and aligned rows before borders; restrained semantic color; minimal navigation chrome. **Progressive disclosure** keeps revision metadata and initial audit details available without making them dominate ordinary record reading. **Accessibility** takes precedence over visual subtlety: native controls retain visible boundaries, focus rings, labels and associated error/help text. Controls are 36px minimum on fine-pointer desktops and 44px on narrow screens or coarse pointers. There is no custom modal, menu or keyboard model.

Current record sections use a 160px heading column beside aligned label/value rows at wide desktop sizes. Smaller widths stack the heading and body; phone widths stack individual labels and values. The directory keeps a semantic table on desktop and becomes compact rows below 640px, retaining name, role, team and status. Legal entity and email remain available in the record. Status is text plus a small redundant colored dot, rather than repeated filled pills. History shows time, action, author and before/after values; each changed field no longer needs its own shaded rectangle.

Current semantic tokens are CSS custom properties in `web/src/styles/tokens.css` (not generated JSON):

| Role | Implemented tokens / values |
|---|---|
| Surfaces | `--color-bg` for the canvas, `--color-bg-subtle` for chrome/search, `--color-surface` for content, `--color-elevated` for focused controls; `--color-selected` for navigation |
| Text | `--color-text`, `--color-text-secondary`, `--color-text-muted`; all retain AA contrast on their applicable surfaces |
| Borders | `--color-border` for separators, `--color-border-strong` for control boundaries |
| Semantic color | `--color-accent` / `--color-focus` for interaction; success, warning, error and neutral text/background pairs |
| Type | Native system stack; 32px page titles at weight 650, 20px section headings, 15px body, 13px metadata; rem sizing |
| Layout | 208px rail; 1152px directory, 992px record and 768px form maximum widths; 4px spacing base |
| Shape / motion | 8px and 10px radii, circular small initials; restrained 160ms control feedback, reduced-motion override; no page animation or translucency dependency |

Light mode uses an off-white canvas with clean content surfaces. Dark mode uses graphite/charcoal layers, a neutral selected-navigation surface and a slightly lighter focused-control surface. Increased contrast and forced colors retain visible boundaries and focus. Automated contrast coverage includes selected navigation, focused controls and search borders in both themes.

Inline editors move focus to the first field on opening and return it to the matching Edit button on close. Conflict recovery preserves the existing explicit draft-replacement contract; no overwrite, automatic merge or changed API behavior is introduced. Search help remains an accessible description; employment-type contextual help retains the accepted Q3 wording.

The paragraphs below describe the earlier review baseline; the dedicated report records current screenshots, test counts and remaining limitations.

The [Codex review](PHASE_1_CODEX_REVIEW.md) refines the original implementation: compact neutral workspace/account rail with one People destination; purposeful search toolbar and initials; grouped record rows and contextual anchors; timeline history with readable changes; aligned native forms with bounded width; explicit conflict recovery preserving drafts. At narrow widths the rail becomes a header, secondary directory columns collapse, and forms use one column. No fake destination, proprietary Apple asset, bundled font, or icon dependency was added.

Actual tokens live in `web/src/styles/tokens.css`, with layout rules in `global.css`; the JSON tooling proposal below is future direction. The font stack uses the browser's platform fonts (`-apple-system`, `BlinkMacSystemFont`, Segoe UI, Roboto, Helvetica, Arial, sans-serif). Manager lookup uses a labelled debounced search field, native select, explicit pagination, and selection preservation. Loading/empty/error feedback shares existing primitives. No modal or custom combobox was necessary.

Final automated axe checks in light/dark modes, keyboard navigation, reduced motion, and 320px reflow pass. Human screen-reader and non-Chrome verification remain outstanding. [Screenshots](../README.md) show only synthetic records.

## 1. Inspiration, not imitation

The interface draws on Apple's design philosophy and Human Interface Guidelines: clarity, restraint, hierarchy, precision, consistency, accessibility, and purposeful motion. It does **not**:

- copy Apple assets, icons, or fonts (no SF Symbols, no bundled San Francisco);
- use Apple logos, names, or trademarks in the product or its marketing;
- recreate macOS or iOS chrome pixel-for-pixel;
- imply Apple affiliation.

Permitted: the CSS generic `system-ui` font stack (which resolves to the user's platform font on every OS), an open-license icon set, and principles such as spacing rhythm, type discipline, and spring-based motion.
The `apple-design` guidance used here comes from publicly discussed design principles, translated to the web.

## 2. Personality

**Premium, calm, precise, modern, minimal, human, enterprise-ready.** Premium through restraint and craft, not decoration. Calm because HR data is consequential and the people using PalCheck are often handling
someone's job, pay, or health. Precise because a wrong label in an HR product is a real error.

**Explicitly avoid** (from the brief): purple gradients, glowing borders, random glassmorphism, giant cards, meaningless charts, excessive rounded rectangles, decorative metrics, pointless animation.
Test for every element: *what does this help the user understand or do?* If nothing, remove it.

## 3. Principles applied

| Principle | In PalCheck |
|---|---|
| **Purpose** | Each screen has one primary job. Decide what *not* to show. |
| **Agency** | The user can always see, undo, or cancel. Confirmations only for irreversible, outward-facing actions (like replaying an event), never as a habit. |
| **Responsibility** | Sensitive fields masked by default with audited reveal; legal content always labelled with its status; simulated systems always labelled. |
| **Familiarity** | Standard patterns for tables, forms, menus, dialogs. Same thing looks and behaves the same everywhere. |
| **Flexibility** | Desktop-first density for operators, comfortable at phone width for approvals and lookups. Density and theme respect user settings. |
| **Simplicity** | Common path first; advanced detail one level deeper (for example, event payload metadata behind a disclosure). Simple is not minimal: context that prevents mistakes is added, not hidden. |
| **Craft** | Every spacing, timing, and alignment value comes from a token. No magic numbers. |
| **Delight** | Comes from things working well: instant feedback, no lost state, honest errors. |

Motion and interaction rules from the design guidance, adapted for an enterprise product:

- **Respond instantly**: pressed states appear on pointer-down; no artificial delays on the input path.
- **Never lock input during a transition**; animations are interruptible.
- **Use springs for touch-driven motion** (sheets, drawers, reorderable lists), critically damped by default; add slight bounce only when the gesture carried momentum. Prefer state-driven CSS transitions for simple hover/focus.
- **Enter and exit along the same path**; anchor popovers and menus to their trigger.
- **Animate only `transform` and `opacity`.** Never animate layout properties.
- **Materials only on chrome**: translucent sidebar/toolbar/sheets over scrolling content, never on content cards; never stack translucent surfaces.
- Motion appears only where it explains a state change. There are no looping or decorative animations.

## 4. Design tokens

A token-based system with three layers:

1. **Primitive** tokens: raw values (a neutral scale, an accent scale, a spacing scale). Not used by components directly.
2. **Semantic** tokens: meaning-based (`color.text.primary`, `color.surface.raised`, `color.status.success`, `space.inset.md`). Components use only these.
3. **Component** tokens: per-component overrides (`button.padding`), created only when needed.

Format: a tool-neutral JSON token file compiled to CSS custom properties (deliverable in `web/` at Phase 1; a separate package only if a second consumer appears). Themes swap semantic tokens: light, dark, and a
high-contrast variant; `forced-colors` (Windows high-contrast mode) is honored with system colors.

| Category | Direction |
|---|---|
| **Color** | Neutral-dominant UI. Semantic roles: **green = success**, **amber = warning**, **red = error**, **blue = system/interactivity**, **neutral = ordinary UI**. Contrast targets: text 4.5:1, large text and UI boundaries 3:1 (WCAG 2.2 AA); values verified by a contrast-check script in Phase 1, not by eye. |
| **Typography** | System font stack. Type scale in `rem` so it respects the user's text size. **Tracking is size-specific**: tighter for large display text, near zero for body, slightly open for small captions. **Leading tightens as size grows.** Weight builds hierarchy alongside size. Monospace only for IDs, correlation IDs, and code. |
| **Spacing** | 4px-based scale expressed in `rem`; layout in multiples of it. |
| **Radius** | Small and consistent. Restraint, not pill shapes everywhere. |
| **Elevation** | Three levels of subtle shadow; heavier only over busy content. |
| **Motion** | Duration and spring tokens (default: critically damped, response in the low-hundreds of milliseconds; reduced-motion variant). |
| **Density** | Comfortable and compact modes via a spacing multiplier. |
| **Z-index and breakpoints** | Named layers; breakpoints defined once; content works at ~400px width. |

Proposed starting type scale (a direction to validate in Phase 1, not a final value set):

| Role | Size | Line height | Tracking |
|---|---|---|---|
| Display | 2.25rem | 1.1 | -0.02em |
| Title | 1.5rem | 1.2 | -0.015em |
| Headline | 1.125rem | 1.3 | -0.01em |
| Body | 0.9375rem | 1.5 | 0 |
| Caption | 0.8125rem | 1.4 | +0.01em |

### 4.1 Status semantics (never color alone)

Every status has a **label and an icon** in addition to color.

| Domain | Mapping |
|---|---|
| **Delivery** | `PENDING` neutral · `PROCESSING` blue (in progress) · `SUCCEEDED` green · `FAILED` red · `RETRYING` amber · `DEAD_LETTERED` red, emphasized · `CANCELLED` neutral |
| **Health** | Healthy green · Degraded amber · Offline red · Unknown neutral (never green) |
| **Legal research** | `UNRESEARCHED` neutral outline · `RESEARCHING` neutral filled · `VERIFIED` neutral with check outline · `PROFESSIONAL_REVIEWED` green with check. Green is *withheld* until professional review so the UI never over-signals trust in unverified law. |
| **Ownership** | `PALCHECK` no badge (default) · `EXTERNAL(system)` neutral source badge, field read-only · `DERIVED` neutral "explain" badge |
| **Simulated** | A persistent, distinct "Simulated" tag on every simulated system and fault control |

## 5. Layout and responsiveness

- **App shell**: a translucent sidebar (structural region) plus a toolbar over scrolling content. Scroll-edge fade instead of hard divider lines where content passes under floating chrome.
- **Content width**: readable line lengths for prose; full-width for tables.
- **Responsive**: breakpoints for phone, tablet, desktop; side gutter of at least 16px; nothing wider than the viewport except tables and code, each in its own scroll container.
- **Tables**: sticky headers, keyboard navigation, column priority collapsing at narrow widths (secondary columns become a detail row), horizontal scroll only as a last resort.
- **Phone**: view an employee, see a hire's status, approve a change, retry a delivery. Deep admin work is desktop-first but must not break.

## 6. Core components (Phase 1)

Button, link, text input, select/combobox, locale-aware date picker, checkbox, radio, switch, table, tabs, dialog and sheet (focus trapped), popover and menu, toast (status via a polite live region), status tag,
banner (used for "unverified legal content" and "Simulated"), timeline (event and delivery history), key-value list, empty state, skeleton, breadcrumb.

Approach: build on an accessible **headless component layer** (React Aria Components or Radix Primitives; choice in [ADR-0009](adr/0009-frontend-architecture.md)) rather than on a styled component library, so visuals come from PalCheck's tokens and
accessibility, focus management, and internationalization come from a tested base. *Unverified:* exact current capabilities of each library; evaluate against the checklist in section 9.

## 7. Phase 1 screens (about four)

1. **People**: list and detail of synthetic employees. Governed fields show ownership badges (all `PALCHECK` in standalone). Sensitive fields masked with audited reveal.
2. **Hire**: a guided form creating a Person and Employment; inline validation; on submit, a status view of the resulting orchestration.
3. **Activity**: delivery attempts per event and destination with status, attempt count, latency, last error, and **Retry**, with a *Simulated* label and (admin only) simulator controls. This is the resilience showcase.
4. **Learn**: the Costa Rica concept glossary: native term + English explanation, status banners, search. No computed content.

Interaction detail for **Activity**: pressing Retry gives immediate visual response; the row moves through `RETRYING` and `PROCESSING` with an announced status change; the result appears in place without losing scroll
position; failure reasons are actionable text, not codes alone.

## 8. Content and terminology

- Voice: calm, precise, plain. No exclamation marks, no filler, no jargon where a plain word exists.
- **Native term + English explanation** for every local concept, everywhere ("Aguinaldo: Mandatory annual bonus, Costa Rica"). The English gloss never replaces the native term.
- **Specific labels** name contents ("People", "Activity", "Learn"), not vague umbrellas ("Home", "Dashboard").
- Errors say what happened, why if known, and what to do next; they never blame the user and never expose internals.
- Never use bare "organization" (see [DOMAIN_MODEL.md](DOMAIN_MODEL.md) section 3): *Tenant*, *Legal entity*, or *Org unit*, as appropriate.
- Legal content always carries its research status and the standard disclaimer ([localization/costa-rica/LEGAL_RESEARCH_POLICY.md](localization/costa-rica/LEGAL_RESEARCH_POLICY.md) section 9).

## 9. Accessibility commitments ([ADR-0010](adr/0010-ui-principles-and-accessibility.md))

Target: **WCAG 2.2 AA**, as part of the definition of done.

- Semantic HTML first; ARIA only where semantics are insufficient.
- Full keyboard operability with a visible, unobscured focus indicator; logical focus order; focus is managed for dialogs and sheets and restored on close.
- Pointer targets at least 24 CSS px, and about 44 px for primary touch controls.
- Text resizable to 200% and content reflowing at a 320px-wide viewport without two-dimensional scrolling (tables excepted in their own scroll containers).
- Color is never the only carrier of meaning (section 4.1); contrast ratios verified by script.
- Asynchronous status changes announced through live regions, politely unless urgent.
- `prefers-reduced-motion`: replace slides/springs with short cross-fades; drop overshoot; keep opacity/color changes that aid comprehension.
- `prefers-reduced-transparency`: drop blur, raise surface opacity. `prefers-contrast: more`: near-solid surfaces with defined borders. `forced-colors`: honored.
- Screen-reader checks on at least two combinations; automated checks (axe-class) in CI; manual keyboard walkthrough per screen.
- Charts and topology have text/table equivalents.

## 10. Internationalization

- The UI language is English. **Every user-visible string is externalized** from the first component (a message-format library; choice in ADR-0009), so a Spanish UI is an addition, not a rewrite.
- Dates, numbers, and currency use `Intl` APIs with the **user's locale**, independent of the **jurisdiction** that governs rules (locale is presentation; jurisdiction is law).
- Layouts tolerate text expansion; Spanish strings commonly run longer than English.
- Native legal terms come from the registry (content), not from UI string bundles.
- No right-to-left work is planned; nothing should preclude it.

## 11. Empty, loading, and error states

Designed as first-class states, not afterthoughts: empty states explain what will appear and offer the next action; skeletons match final layout to avoid jumps; errors are recoverable where possible and always
carry the correlation ID for support.

## 12. Process

- **Prototype in the browser** early; interactive prototypes beat static mockups.
- Design interaction and visuals together; motion is not a coat of paint added later.
- Review motion frame-by-frame or in slow motion before calling it done.
- A design-review checklist per screen: purpose, hierarchy, tokens only, keyboard path, screen-reader path, reduced-motion path, empty/error states, text expansion, dark and high-contrast.

## 13. Open questions

- **UQ-01** Headless base: React Aria Components vs. Radix Primitives (ADR-0009).
- **UQ-02** Open-license icon set: which, and license terms (Unverified).
- **UQ-03** Token tooling: hand-maintained JSON compiled by a small script vs. an existing token toolchain. Recommendation: the small script until pain appears.
- **UQ-04** Whether a compact density mode is needed in Phase 1 or only the comfortable default.
- **UQ-05** Data visualization: charts are added only where they answer a question (for example, health over time). Use the `dataviz` guidance when the first chart is designed.
