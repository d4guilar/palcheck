# Phase 1 UI polish

Scope: the functional Phase 1 foundation is accepted by Daniel. This pass changes presentation and interactions within that foundation; it does not open Phase 2 or change domain semantics. Q1/Q2/Q3 work, including Claude's completed Q3 resolution, is preserved.

## Initial browser review

Reviewed the running React/Spring/PostgreSQL/Keycloak application in Chrome before changing UI code, using only existing synthetic records. Captured sign-in, People, person detail/history, identity editing, conflict, creation, search-empty, workspace-empty, load-error and loading states. Checked 1440px desktop, 390px narrow layouts and dark person detail. Conflict/error/empty/loading examples use browser-local response interception; the underlying application is rendered normally and no business record is changed to stage them.

Findings:

- Directory title/action and search occupy two widely separated bands. Search help is detached at the far right. Strong input outlines and repeated green pills compete with names.
- Person detail stretches labels and values across too much width. Employment appears as a section heading and a nested heading; its status also repeats the profile summary. Revisions are visible in ordinary record reading.
- Identity editing is a large gray rectangle with controls spanning the full record width. Creation uses a different width and alignment rhythm.
- History already translates values and groups commands correctly, but every changed field has another shaded box. Time and author share one small line.
- Narrow layouts remove role, team and email, leaving a sparse name/status table. The account header occupies several rows before content starts.
- Dark mode is accessible but nearly black and visually flat. Empty/error/loading states are large outlined boxes; skeletons do not resemble directory rows.

The existing native controls, permission checks, accent-insensitive search, editable snapshots, conflict warning, focus handling and audit grouping provide a sound foundation. No framework or new dependency is needed.

## Design and component changes

- **Shell and directory:** narrowed the existing rail to 208px; neutral selected navigation; one compact title/search/create toolbar with an original search icon. Search help remains available as the input's accessible description. Native semantic table, permissions, search debounce, accent handling and paging are unchanged. Small initials and text-with-dot status cues keep names prominent.
- **Person record:** a bounded 992px content width and reusable `RecordSection` align section headings with label/value content. Identity, employment and history use that shared layout. Removed the nested duplicate Employment heading and the duplicate profile status; each employment still carries its own status. Revision numbers are accessible through native Record details disclosures. No resource or field was removed from the API.
- **Forms:** retained labelled native inputs, selects and date fields; consistent spacing, rounded controls and restrained secondary buttons; inline editors now use a bounded, clean surface. `SearchField` is reused for People and manager search. The existing manager result paging and preserved selection are unchanged. `useEditorFocus` places focus in the opening editor and restores the matching Edit button after closing it.
- **History:** time leads each entry, followed by action and author. Changed fields have aligned labels and before/after values, without a shaded box for every change. The existing complete spoken sentence remains available to screen readers. Initial create details still use a native disclosure; command grouping and history paging are unchanged.
- **Feedback:** retained saving/disabled states, inline validation, success notices and explicit conflict recovery. Empty/error states use a quiet content surface and recovery action. Loading can show row-shaped skeletons. A failed record load has one page title and a named recovery alert instead of repeating the heading.

No framework, dependency, backend behavior, API, enum value or domain semantics changed in this dedicated pass. It adds two reusable presentation components and one small focus hook; the shared browser axe helper was extracted to support the expanded E2E coverage.

## Tokens, dark mode and responsiveness

The CSS token source remains `web/src/styles/tokens.css`. Added explicit elevated/selected surfaces, title weight, record width, section/label widths, icon/dot sizes and touch targets. Light mode is off-white with clean white content; dark mode uses graphite/charcoal layers and a lighter focused-control surface. Foreground, semantic notice and focus colors retain their contrast. The automated token matrix now also covers selected navigation, elevated control text/hints, and search/focused-control boundaries.

Typography stays in the native system stack: 32px page titles, 20px section headings, 15px body and 13px metadata. No font files are bundled. Controls have a 36px desktop minimum, increasing to 44px on narrow screens or coarse pointers. Motion is limited to restrained control feedback with reduced-motion support. Surface hierarchy does not depend on blur or transparency.

Below 1120px, record headings stack above their content; below 768px, the rail becomes a compact header and forms use one column. At 640px and below, the existing table becomes compact directory rows that retain role and team information instead of hiding it. Explicit table/row/cell semantics preserve its accessible structure. Labels and values stack; long names and email values wrap. Legal entity and email are available on the full person record. No new mobile feature or navigation destination was introduced.

## Accessibility and verification

Automated WCAG 2.2 AA checks run in the actual Chrome browser, including color contrast. The keyboard journeys exercise the skip link, consolidated toolbar, record navigation, opening and cancelling all three editors, and native metadata disclosure. The new narrow dark-mode journey checks directory context, employment editing, 320px reflow, reduced motion and visible forced-color focus. Existing real stale-edit, viewer, tenant, create/edit/history, logout and validation journeys remain in the suite.

Final verification on 2026-09-22, from `web/`:

| Command | Result |
| --- | --- |
| `npm run typecheck` | Passed |
| `npm run lint` | Passed |
| `npm test -- --maxWorkers=1 --reporter=verbose` | 104 tests passed across 8 files |
| `npm run build` | TypeScript and Vite production build passed |
| `$env:PALCHECK_E2E_CHANNEL = 'chrome'`, then `npm run e2e -- --output=.playwright/polish/final-e2e` | All 17 browser tests passed in 3.5 minutes |

The component total includes 10 new contrast checks; the browser total includes two new editor-focus/disclosure and narrow dark/forced-color journeys. Existing assertions were updated for the consolidated toolbar, revision disclosure and single record-error heading. All existing critical E2E journeys remain enabled. The separate ignored browser output directory avoids Windows locks on running development-service logs in the default test-results directory.

The final screenshot review additionally scanned all 16 captured states with axe, with zero violations under its WCAG A/AA rules through 2.2. Human screen-reader testing has not been performed; passing axe is not a claim of complete accessibility conformance. No backend behavior was touched, so backend tests were not rerun for this UI task. `backend/mvnw.cmd -B -ntp -DskipTests package` succeeded to prepare the existing backend executable for the local browser review; this is packaging evidence, not a backend test run.

## Screenshots and visual review

README uses current captures of the actual application: [People](screenshots/people.png), [person](screenshots/person.png), [dark person](screenshots/person-dark.png), and [inline editing](screenshots/person-edit.png). Public captures use existing fictional seeded records. The directory's visible `acme.example` search excludes accumulated test-run records through the real API; no counts or rows are fabricated. Internal before/after captures additionally cover history disclosure, conflict, empty, loading, error and narrow layouts.

## Remaining limitations and design choices for Daniel

- Native selects and date controls retain platform-specific styling. This is intentional for keyboard behavior and maintenance; Chrome on Windows is the verified browser/platform combination.
- Narrow directory rows prioritize name, role, team and status. Full email and legal-entity detail require opening the record. At intermediate widths the secondary legal-entity table column is hidden.
- Long histories and multiple employments still create a long page; no new tabs, virtualization or pagination model was introduced. The wide heading column deliberately trades some horizontal space for consistent section scanning.
- Search help is an accessible description rather than a persistent caption. Revision metadata is one disclosure away. Daniel should review these density and disclosure choices in the running app.
- Human screen-reader and other-browser verification remain outstanding. No public deployment or production-readiness claim follows from this pass.

PalCheck is inspired by general modern Apple/HIG principles of clarity, restraint, hierarchy and accessibility. It is an original enterprise web application, not an Apple product or replica. No Apple assets, proprietary fonts, imitation OS chrome or new UI framework were used.
