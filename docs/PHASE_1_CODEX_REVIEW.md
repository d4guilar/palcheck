# Phase 1 — Codex engineering and product review

Date: 2026-09-19. Scope: Phase 1 review, polish, and public-repository preparation. **Daniel's acceptance and Q1–Q3 remain open; Phase 2 is not authorized.** All examples, fixtures, and browser data used in this review are synthetic. Legal rules are `NOT IMPLEMENTED`.

## 1. Executive assessment

Claude built a coherent authenticated workforce foundation. Independent inspection supports retaining its modular monolith, explicit tenant-scoped JDBC, standard OIDC sessions, application permissions, separate resource versions, and transactional audit/idempotency. Codex concentrated on product polish and specific correctness/security gaps rather than replacing that foundation.

The independently run baseline was frontend 92/92 and Chrome E2E 12/12, with typecheck, lint, and build passing. Backend was **149/150**, not the previously reported 150/150 in this environment: the unchanged logging canary detected synthetic personal fields in framework DEBUG output activated by an inherited `DEBUG` environment variable. Explicit framework logging defaults fix that regression; final results are in section 18.

Rendered sign-in, directory, record, edit, and dark-mode screens were inspected before changes. The initial assessment and implementation notes were written here before implementation: use bundled PostgreSQL unaccent, server-paged manager lookup, transactional manager-graph serialization, a bounded API body, framework logging protection, and prepared security workflows; add no business module, table, or application dependency. This final report incorporates those notes and their verification.

## 2. Architecture assessment

The five packages (`shared`, `platform`, `access`, `audit`, `workforce`) have understandable responsibilities. Domain rules are independent of Spring and provider types; application services own authorization and transaction boundaries; infrastructure owns SQL; web adapters expose DTOs. ArchUnit checks dependency direction and tenant-bearing repository contracts. Inspection complements those structural checks: a `TenantId` argument alone cannot prove that SQL uses it.

Accepted direction remains intact. No connector, broker, outbox dispatcher, policy execution, or distributed service was added. JDBC is appropriate for this small, explicit relational model. Reconsider abstractions when duplication or operational measurements justify them, not to accumulate technology. ADR-0022 remains subject to Daniel's Q1 confirmation.

## 3. Backend review

Controllers delegate to application services; response DTOs do not expose persistence objects. Create and patch share domain validation; unknown JSON fields, invalid dates/lengths, and invalid references are rejected. SQL uses bound parameters. Problem details give stable codes and request IDs without SQL, stack traces, or arbitrary exception messages. Tenant and actor values are not accepted from request bodies.

The changes remain narrow: a manager-candidate read endpoint, folded search predicates, a transaction-scoped graph lock, a body-limit filter, logging configuration, and standard OIDC logout handling. No additional application library was required. The tradeoffs are PostgreSQL-specific search/locking and serialized graph changes within a tenant; both are easier to explain than additional distributed infrastructure.

## 4. Tenant-isolation review

Tenant context comes from the authenticated server-side mapping. Directory, record, employment, assignment, history, reference, and manager queries bind it. Manager joins use tenant-qualified relationships. Composite foreign keys reject cross-tenant associations even through raw SQL. Foreign IDs receive the same not-found behavior as absent IDs; unknown client fields cannot override the tenant.

Negative tests cover reads and mutations, permissions, cross-tenant foreign keys, and the new folded-search/manager paths. The initial `(issuer, subject)` principal lookup deliberately establishes tenant context; it is not a workforce query. Expired-receipt cleanup is infrastructure maintenance, not a tenant-selectable endpoint.

**RLS recommendation: DEFER for the current synthetic local milestone.** Application enforcement remains mandatory. Before real data, review a non-owner runtime role, per-transaction tenant binding, pool reset behavior, migrations, maintenance access, and failure tests. Adding policies under an owner/superuser connection would give misleading assurance. This preserves Q1 rather than silently accepting it.

## 5. Authentication review

OIDC authorization-code login and backend sessions use Spring Security. Principal identity is issuer plus subject, not email; disabled principals and inactive tenants are denied on subsequent requests. Keycloak is development infrastructure, not a domain dependency. No alternative provider was implemented.

A review finding: logout returned an identity-provider URL containing `id_token_hint` in JSON to application JavaScript. Logout now submits a native CSRF-protected POST form; Spring's standard OIDC logout handler invalidates the session and performs the provider redirect. The integration test checks missing/wrong CSRF rejection, valid redirect, no JSON token disclosure, and an unauthenticated session afterward. Browser E2E verifies complete sign-out.

The ID-token hint still travels in the standard browser navigation to the identity provider; this is not a claim that it never reaches the browser. Access/refresh tokens are not handed to the SPA. Sessions remain in-memory, single-instance, with a 30-minute idle timeout. Secure cookies outside `dev`, HttpOnly session cookies, SameSite, session fixation protection, and CSRF remain in place.

## 6. Authorization review

Application services enforce permissions independently of hidden UI controls. `HR_VIEWER` can read permitted workforce information but cannot create/edit or read audit history. `HR_ADMIN` receives the required mutation/history permissions. Principal permissions are resolved server-side on requests. New manager lookup requires both people and employment read permissions.

Current authorization scope is tenant-wide. Own-record, direct-report, and Org Unit policies are future work; neither the UI nor this report claims they exist. Existing viewer and endpoint-negative tests remain intact.

## 7. Audit review

Workforce writes, audit inserts, and idempotency receipts share the service transaction. The audit writer requires an existing transaction. Tests force an actual audit persistence failure and verify that business rows and receipts do not commit. Database triggers reject audit update, delete, and truncate; an owner with DDL privileges can still alter controls, so this is not cryptographic tamper evidence.

History uses field labels, actor/time, and old/new values in grouped timeline entries. Audit contains changed personal fields when needed to explain a change; it is not PII-free. Idempotency receipts hold result references rather than copies of those fields. Retention, erasure, encryption, and auditing history reads remain separate future decisions before real workforce use.

## 8. Concurrency review

Person, Employment, and Assignment each have an independent optimistic version. Conditional updates produce deterministic 409 conflicts; no-op behavior and actual simultaneous same-version requests are covered. A replay does not create another change or audit entry.

A new regression reproduced a distinct race: concurrent changes to different assignments could each pass the manager-cycle check and jointly create a cycle. With a temporary delay trigger in real PostgreSQL, both originally succeeded. Workforce create/assignment mutations now acquire a tenant-scoped transaction advisory lock before reading the reporting graph. The regression now permits one edge and rejects the edge that would complete the cycle.

The fix is recorded in Proposed [ADR-0023](adr/0023-manager-graph-concurrency.md); implementation does not imply owner acceptance. The lock is coarse by design: graph mutations within one tenant serialize until commit. A hash collision can reduce concurrency across tenants, not expose data. Existing row versions still protect stale edits. Measure contention before designing a more complex graph-lock strategy.

Background query refresh previously could remount a version-keyed form and discard unsaved input. Editors now retain the original record/version snapshot until explicit cancel, save, or reload. Conflict text explains that reload replaces the draft; failed reload preserves it. A component regression checks background refresh and the submitted original version; the real stale-tab E2E still passes.

## 9. Idempotency review

Receipts are namespaced by tenant, authenticated principal, operation, and key. Fingerprints use parsed, canonical fields with explicit boundaries and distinguish absent/null where relevant. Same-key materially different input produces deterministic 422 rather than an unrelated saved result. Required create keys and optional patch keys retain their existing contract. Unique reservation and business/audit/receipt writes share the transaction; parallel duplicate tests exercise PostgreSQL locking rather than only mocks.

A replay resolves the stored result reference with current authorization and returns the **current resource**, not a frozen historical response body. It does not repeat the mutation. Receipt expiry is 24 hours; this is bounded retry protection, not permanent business-effect uniqueness. After expiry the operation can run again subject to current domain constraints. The UI keeps a retry key for the same form submission, not durable recovery across tab loss.

Q2 recommends clarifying this contract while retaining the implementation. Future provisioning/event effects need their own stable operation identity under the accepted integration direction; Phase 1 receipts must not be advertised as that solution.

## 10. Database review

Flyway remains forward-only. V6 installs PostgreSQL's bundled trusted `unaccent` extension in `workforce`; existing migrations were not rewritten. If an existing database has that extension in another schema, V6 fails explicitly instead of relocating a shared extension. Provision the expected extension/schema deliberately before migration. PostgreSQL must include its contrib extension files and the migration role needs the required CREATE privileges. Fresh integration databases and the existing local development database both migrated successfully.

Tenant/order indexes still narrow and order directory candidates. Folded substring search and count scan candidates within the tenant; this pass does **not** establish large-tenant latency guarantees. No unsafe IMMUTABLE wrapper or unmeasured trigram index was added. Offset pagination also has increasing deep-page cost. Measure representative tenant size/query plans before introducing a maintained search key/index or keyset pagination. See [PostgreSQL unaccent documentation](https://www.postgresql.org/docs/17/unaccent.html).

## 11. Frontend architecture review

TanStack Query owns server data, local form state owns drafts, and the existing mutation helper owns submission/validation/conflict behavior. Shared native controls, feedback, initials, and manager selection reduce actual duplication without adding a component framework. Strings remain in the English message catalog; styles use shared tokens.

The directory synchronizes its search draft with URL changes, preserving browser-back behavior. Paging waits for the current query result. Manager selection is a labelled search field plus a native select, explicit paging, loading/error feedback, and retry. An already selected candidate remains visible outside the current page or during a failed search. This avoids implementing an incomplete custom combobox.

Vitest worker concurrency is bounded to two because concurrent jsdom/axe environments exhausted this machine during verification. Assertions and timeouts were not weakened to obtain a pass.

## 12. Apple-inspired design review

**Before:** readable but repetitive bordered record cards, weak distinction between workspace and content, and a utilitarian directory. **After:** a compact neutral workspace/account rail, one real People destination, native system typography, a purposeful directory toolbar, subtle separators, aligned field rows, profile initials, contextual section links, and grouped history changes.

Forms have a restrained readable width, aligned labels/controls, help text after the field, deliberate save/error feedback, and a clear conflict recovery action. At small widths the workspace reflows into a header, forms become one column, and secondary table metadata collapses while record detail remains available. Dark mode uses layered neutral surfaces. There are no decorative metrics, invented product destinations, proprietary Apple assets/fonts/icons, or added icon dependency.

Rendered checks covered sign-in, directory, person, create/edit, history, conflict, viewer, empty/error states, dark mode, and 320px reflow. Conflict/error screenshots used controlled network responses for deterministic inspection; actual stale-write behavior is separately tested against the backend. [README screenshots](../README.md) contain only synthetic data.

## 13. Accessibility review

Semantic controls, visible labels and focus, the original skip-link/route-focus policy, meaningful landmarks, validation associations, live status messages, and reduced-motion behavior were retained. Native manager selection supports keyboard operation without custom listbox semantics. Touch controls and table reflow were checked in the real browser.

The complete component and browser axe suites pass after polish, including light/dark contrast, target-size checks, keyboard-only navigation, and reduced motion. No horizontal page overflow occurs in the tested 320px directory/create flows. These checks support the WCAG 2.2 AA target; they do not certify conformance. **No human screen-reader validation or non-Chrome browser run was performed.** Forced-colors styling remains present but has not had a human assistive-technology review.

## 14. Search/localization review

Accent-insensitive lookup is implemented for both people and manager candidates. PostgreSQL NFC normalization plus unaccent and case-insensitive matching allows `Solis`, `Garcia`, and `Jose` to match accented stored names, including decomposed Unicode input. The query is folded before wildcard escaping; `%`, `_`, and backslash remain literal search characters. Count and result predicates use the same logic and tenant boundary. Display/storage preserves accents.

This is **SOFTWARE BEHAVIOR** for finding names, not an identity-matching or legal rule. Folding can conflate distinct spellings, including n/ñ; that is acceptable for broad directory lookup, not for determining whether two people are identical. Ordering remains the existing case-folded name order, not a new locale-specific ICU collation.

`GET /api/manager-candidates?q=&page=&size=&excludingEmploymentId=` pages employments, including multiple employments for one person. Default size is 20, maximum 100; the UI debounces search by 300ms and pages 20. Tests include more than 100 people, multiple employments, exclusion, bounds, tenant isolation, and retained selection. Status/entity/title provide context. No unresearched status-based manager eligibility was invented; graph validity remains server-enforced.

## 15. Security review

| Item | Classification | Outcome / reason |
|---|---|---|
| Request size | **IMPLEMENT NOW** | 64 KiB cap on POST/PUT/PATCH `/api/` bodies; fixed neutral 413, including unknown/chunked length. Appropriate for the small JSON contract; no upload endpoint exists |
| Rate limiting | **NEXT SECURITY MILESTONE** | Before an internet-accessible demo: choose reverse-proxy and authenticated-tenant limits with trusted proxy identity, IdP protection, and observable failure behavior. An in-memory counter here would not solve deployment abuse |
| RLS | **DEFER** | Keep application isolation; assess runtime DB privileges and transaction binding before real-data use, subject to Q1 |
| Production data controls | **PRE-PRODUCTION ONLY** | Retention/erasure research, backup/restore, encryption decisions, least-privilege operational roles, and deployment/session design need their own acceptance evidence |

The logging canary found a real configuration weakness with synthetic data, not evidence of a real-data leak. Spring web/security/JDBC log levels now explicitly default to INFO even under generic DEBUG activation. Do not enable HTTP-body or SQL-parameter debug logging with real data; operators can override configuration and infrastructure/database logs need separate controls.

Logout no longer exposes a token-bearing URL in JSON to JavaScript (section 5). CSRF, request/correlation handling, no-store responses, authorization, and safe problem details remain. The body limit is after security and before API controllers; it is not a global reverse-proxy header/connection limit. Standard provider behavior follows [Spring OIDC logout](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/logout.html) and [CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## 16. Public repository readiness

README now distinguishes implemented behavior from the roadmap, links evidence, shows synthetic screenshots, and provides commands with clear working directories. Useful AI collaboration records remain. Copyright and the pending licensing decision are explicit. Generated builds, browser artifacts, dependencies, local environment files, and review tooling remain ignored.

Source inspection found only intentional development/test placeholder credentials and synthetic data on reserved email domains; no real secret or employer-confidential material was identified. A checksum-verified Gitleaks 8.30.1 binary scanned a publication-candidate source snapshot with no findings. **There is no `.git` directory in this workspace**, so no commit history could be inspected and no history-clean claim is made. Scanning is evidence, not a guarantee. No repository was published or remote setting changed.

Prepared security workflow: full-checkout Gitleaks; npm audit blocking high/critical advisories; public-PR dependency review; weekly Dependabot for Maven/npm/Actions. Actions are pinned to verified release commit SHAs. Dependency review relies on eligible hosting settings and the dependency graph; Maven coverage must be confirmed there. License enforcement is deliberately disabled pending Daniel's policy. Gitleaks Action's personal-repository licensing assumption must be revisited if repository ownership changes. See [GitHub dependency review](https://docs.github.com/en/code-security/concepts/supply-chain-security/dependency-review) and [Gitleaks Action requirements](https://github.com/gitleaks/gitleaks-action).

**Unverified:** all GitHub workflows, native secret-scanning/push-protection settings, hosted dependency graph, and a full transitive Java vulnerability scan. Local npm audit reported zero advisories. Prepared Maven dependency review/Dependabot is not evidence that the entire Java dependency tree has been scanned clean.

## 17. Open-source/IP considerations

[COPYRIGHT.md](../COPYRIGHT.md) attributes creation and ownership to Daniel Aguilar. [OPEN_SOURCE_STRATEGY.md](OPEN_SOURCE_STRATEGY.md) separates visibility, authorship, copyright, and licensing, and leaves contribution policy deliberate. No project LICENSE, license header campaign, contributor assignment, or open-source claim was added. Existing third-party notices remain intact. Daniel's ownership decisions are not replaced by AI-assisted implementation. These documents state project intent, not legal advice.

## 18. Test results

All final runs below were performed on 2026-09-19 after the relevant code changes:

| Check | Independently observed result |
|---|---|
| `backend/mvnw.cmd -B -ntp clean verify` | **155 passed**: 60 unit/architecture + 95 integration, zero failures/errors/skips; real PostgreSQL 17 and test OIDC provider |
| `npm run typecheck` | Passed |
| `npm run lint` | Passed, zero warnings/errors |
| `npm test` | **94 passed**, 8 files |
| `npm run build` | Passed |
| `PALCHECK_E2E_CHANNEL=chrome npm run e2e` | **15 passed**, real frontend/backend/Keycloak/PostgreSQL; final run 41.5 seconds |
| `npm audit --audit-level=high --json` | Zero reported vulnerabilities in all severity categories |
| Gitleaks source scan | No findings; source snapshot only, no Git history available |

New backend coverage: two bounded-body tests, two search/manager integration tests, and the manager-cycle concurrency regression. Existing OIDC integration coverage now asserts CSRF-protected redirect logout. New frontend coverage: paged/debounced manager lookup with preserved selection and background-refresh draft/version preservation. Three added E2E tests cover accent lookup/manager discovery, narrow-screen reflow, and empty/error recovery; the 500 response is injected deliberately, not an observed server failure.

Existing tests continue to cover meaningful negative behavior: foreign reads/mutations, forbidden writes, audit rollback, parallel same-key/same-version requests, mismatched idempotency payloads, real sign-out, and actual stale-tab conflict. The baseline logging failure and reproduced graph race were fixed, not hidden by removing assertions. A Windows clean-build file lock was resolved by stopping the review's running backend before `clean`; it was an environment issue, not a source failure.

## 19. Fixes made

- Preserved draft/version snapshots through background refresh and made conflict reload explicit and recoverable.
- Replaced the first-100 manager list with bounded server search/paging and retained selections.
- Added accent-insensitive, tenant-scoped, literal-safe search through forward migration V6.
- Serialized reporting-graph changes to close a demonstrated cross-row manager-cycle race.
- Bounded API write bodies, hardened framework logging defaults, and removed JavaScript exposure to the logout token hint.
- Refined workspace layout, directory, record fields, timeline, forms, loading/empty/error states, dark mode, and responsive reflow without new product destinations.
- Added focused regressions, prepared pinned security workflows/Dependabot, and documented ownership, evidence, and release limits.

## 20. Remaining limitations

Docker Compose and GitHub build/E2E/security workflows have not run. Human screen-reader testing and other browser engines remain outstanding. RLS, rate limiting, audit-of-history-read events, persistent/shared sessions, and production privacy/operations controls are absent. Search/deep paging and graph-lock contention lack representative scale measurements. A full Java advisory scan and dependency-license inventory are not completed.

The E2E suite expects seeded services and adds synthetic records per run. The 24-hour idempotency receipt is not permanent deduplication. Employment vocabulary is provisional software language, not a legal classification. Extension availability/schema/privileges must be checked in each deployment. No real data, internet deployment, or legal functionality is approved by these results.

## 21. Q1–Q3 recommendations

These reproduce the proposals from plan section 16. Classifications below are **Codex recommendations, not Daniel's approval**.

| Question / current proposal | Recommendation | Reasoning and tradeoffs |
|---|---|---|
| **Q1:** confirm ADR-0022's Spring JDBC over JPA; backend OIDC session over browser-held tokens; Maven packages + ArchUnit instead of Modulith; embedded PostgreSQL while no container runtime exists; RLS deferred | **ACCEPT** | Explicit queries and package checks fit this monolith; server sessions simplify browser token handling; real embedded PostgreSQL tests transactions without Docker. Costs are handwritten SQL, session operational constraints, container-parity validation still due, and tenant isolation without a DB policy backstop. Revisit at concrete scaling/deployment triggers |
| **Q2:** require `Idempotency-Key` on create, optional on patch, with 24-hour expiry | **AMEND** | Retain mechanics but explicitly accept bounded retry lifetime and current-resource replay semantics. Require clients to treat expiry as loss of the receipt guarantee; document that future durable business-effect deduplication is separate. Longer retention improves retry coverage but adds storage/cleanup and does not replace stable business identity. No lifetime or API requirement was silently changed |
| **Q3:** confirm placeholder employment status/type values | **ACCEPT** | `PENDING`, `ACTIVE`, `ENDED`; `FULL_TIME`, `PART_TIME` are useful synthetic software vocabulary. They carry no statutory eligibility or calculation behavior. They may need migration when researched domain requirements mature; no legal meaning is inferred |

## 22. Phase 1 completion recommendation

**Recommend accepting the functional local Phase 1 foundation, with the verification limits recorded above.** This is ready for Daniel's product/engineering acceptance, not production certification. Public-source preparation is substantially stronger, but publication itself, licensing, hosted security configuration, and first CI runs remain owner/release actions.

The consensus is to preserve Claude's foundation and the Codex corrections, then close evidence gaps before adding features. The smallest next milestone is **Phase 1 acceptance and publication validation**: Daniel resolves Q1–Q3; run Compose and GitHub checks in an appropriate environment; verify Java dependency coverage and hosted settings; perform human screen-reader and another-browser checks; fix only failures found by those checks. If a public hosted demo is planned, authorize the rate-limit/deployment security milestone before exposure. Do not open Phase 2 implicitly.

[Review handoff and next-prompt scope](PHASE_1_REVIEW_HANDOFF.md) collects this boundary for the next engineer.
