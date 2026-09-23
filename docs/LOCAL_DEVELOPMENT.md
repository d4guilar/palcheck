# Local development

How to run PalCheck's Phase 1 and Phase 2 application on your machine, sign in as the synthetic demo users, run every test suite, and fix the usual problems. Everything here uses **fictional data and development-only placeholder credentials**; nothing in this guide is a real secret.

## What runs where

| Piece | Port | Started by |
|---|---|---|
| Web app (Vite dev server; **open this one in the browser**) | 5173 | `npm run dev` in `web/` |
| Backend (Spring Boot, `dev` profile) | 8080 | `./mvnw spring-boot:run` in `backend/` |
| Keycloak (development identity provider) | 8180 | Docker Compose, or a local Keycloak (see below) |
| PostgreSQL 17 | 5432 | Docker Compose, or a local server |

Nothing else is needed: no broker, no dashboards, no simulator.

**Why the browser uses port 5173, not 8080.** The browser must see one origin so the backend's session cookie stays first-party and no token is ever held by JavaScript. The Vite dev server proxies `/api`, `/oauth2`, `/login` and `/logout` to the backend ([web/vite.config.ts](../web/vite.config.ts)). Open `http://localhost:5173`.

## Prerequisites

- JDK 17 or newer (`JAVA_HOME` set). Maven is provided by the wrapper (`mvnw`, `mvnw.cmd`).
- Node.js 24 (the tested development/CI version) and npm.
- Either **Docker** (with Compose v2) for PostgreSQL and Keycloak, or the do-it-yourself alternative below.

The backend's tests do **not** need Docker: they start real PostgreSQL binaries in-process ([ADR-0022](adr/0022-phase1-technical-baseline.md) decision 10).

## Quick start with Docker Compose

> **Verification status.** `docker-compose.yml` and the Keycloak realm import were written on a machine without a container runtime. The realm file was verified by importing it into a locally unpacked Keycloak 26.7.4, and the full sign-in and browser test flow was run against that. **The Compose file itself has not been executed yet.** If it does not work, please tell us; the "Without Docker" section below is the path that was actually run.

```bash
# 1. Create the untracked environment file from the committed placeholder template.
cp .env.example .env

# 2. Start PostgreSQL and Keycloak. Keycloak imports the "palcheck" realm on first start (about a minute).
docker compose up -d

# 3. Start the backend (dev profile: reads ../.env, seeds synthetic demo data, readable logs).
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev        # Windows: .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 4. In another terminal starting at the repository root, start the web app.
cd web
npm ci
npm run dev
```

Open <http://localhost:5173> and choose **Sign in**.

The backend starts only if it can reach PostgreSQL **and** discover the identity provider at `PALCHECK_OIDC_ISSUER_URI`, so start Compose first and wait for Keycloak to finish importing. Check with `curl http://localhost:8080/actuator/health/readiness` (expect `{"status":"UP"}`).

## Demo users

All demo users share the password in `PALCHECK_DEMO_USER_PASSWORD` (default `change-me-local-dev-user` from `.env.example`). Users exist in Keycloak; what each may do in PalCheck comes from the backend's own principal mapping, seeded on first start.

| User | Workspace | Role | What to expect |
|---|---|---|---|
| `hr.admin@acme.example` | Acme Latin America | HR_ADMIN | Everything: list, create, edit, employment, history |
| `hr.viewer@acme.example` | Acme Latin America | HR_VIEWER | Read people and employment; no create or edit controls; no change history |
| `hr.admin@globex.example` | Globex Andina | HR_ADMIN | A different, smaller tenant; cannot see or open Acme records |
| `unmapped@acme.example` | none | none | Signs in at the identity provider but has no PalCheck workspace: "Your account is not assigned to a workspace" |

The seeded people, organizations and emails are fictional (emails use the reserved `.example` domain). Employment statuses and types are core software vocabulary, deliberately legally neutral (**not** legal classifications); PalCheck implements no Costa Rican legal behavior in Phase 1.

## Without Docker

This is how the stack was actually run and verified during development.

1. **PostgreSQL 17**: create a database and a user, for example `palcheck` / `palcheck`. Set the backend's connection through environment variables (or `.env`): `PALCHECK_DB_URL=jdbc:postgresql://localhost:5432/palcheck`, `PALCHECK_DB_USER`, `PALCHECK_DB_PASSWORD`. Flyway creates the schemas (`access`, `audit`, `workforce`, `platform`) on first start.
2. **Keycloak 26.x** (JDK 17+): unpack the distribution, copy `infra/keycloak/palcheck-realm.json` to its `data/import/` directory, then start it in development mode with the realm placeholders set as environment variables:

   ```bash
   export KC_BOOTSTRAP_ADMIN_USERNAME=admin KC_BOOTSTRAP_ADMIN_PASSWORD=change-me-local-dev
   export KC_HOSTNAME=http://localhost:8180 KC_HTTP_PORT=8180
   export PALCHECK_OIDC_CLIENT_SECRET=change-me-local-dev-client-secret
   export PALCHECK_DEMO_USER_PASSWORD=change-me-local-dev-user
   bin/kc.sh start-dev --import-realm
   ```

   `KC_HOSTNAME` must be the address the **browser and the backend both** use, so the token issuer is identical for both.
3. Start the backend and web app exactly as in the Docker steps.

## Resetting

- **Docker:** `docker compose down -v` deletes the database volume and Keycloak's dev data; start again from step 2.
- **Only the application data:** drop and recreate the database. The dev seeder creates a demo tenant only if it does not exist yet, so it never overwrites data you created.

## Tests

| Suite | Command | Needs |
|---|---|---|
| Backend: unit, architecture, integration (real PostgreSQL, real OIDC login flow) | `cd backend && ./mvnw verify` | JDK 17. First run downloads the embedded PostgreSQL binaries |
| Frontend: type check, lint, component tests, production build | `cd web && npm run typecheck && npm run lint && npm test && npm run build` | Node |
| End to end (real browser against the running stack) | `cd web && npx playwright install chromium && npm run e2e` | The full stack running (all four pieces above) |

- **End-to-end details.** The tests sign in through Keycloak as the demo users, so they need the seeded data. They create records with a unique fictional name per run and never delete anything, so repeated runs against a long-lived dev database simply add fictional people. If the browser Playwright expects is not installed and you do not want to download one, use an installed browser: `PALCHECK_E2E_CHANNEL=chrome npm run e2e` (or `msedge`). To target another address: `PALCHECK_E2E_URL=http://localhost:5173`.
- **Continuous integration.** [ci.yml](../.github/workflows/ci.yml) runs the backend and frontend suites on every push and pull request. [e2e.yml](../.github/workflows/e2e.yml) runs the browser suite on demand (it needs Docker); it has not been run on GitHub yet.

## Logs

Outside local development the backend writes one JSON object per line (Elastic Common Schema) with `requestId`, `correlationId`, `tenantId`, `userId`, `operation` and `result` as fields; the `dev` profile prints readable text instead. To see JSON locally: add `--logging.structured.format.console=ecs` to the run arguments. Application logging is designed to exclude tokens, cookies, request bodies and people's names and emails ([SECURITY.md](SECURITY.md) section 9). Every API response carries `X-Request-Id`, and error bodies include it, so a problem report can be matched to a log line.

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| Backend exits at startup with an issuer or discovery error | Keycloak is not up yet, or `PALCHECK_OIDC_ISSUER_URI` is wrong. Wait for `http://localhost:8180/realms/palcheck/.well-known/openid-configuration` to answer, then start the backend |
| Sign-in loops back to the sign-in page or shows "Sign-in did not complete" | You are on `localhost:8080` instead of `localhost:5173`, or the client secret in `.env` differs from the one Keycloak imported. Use port 5173; reset Keycloak if you changed the secret after the first import |
| "Invalid parameter: redirect_uri" at Keycloak | The address in the browser is not `http://localhost:5173` or `http://localhost:8080` (the realm allows only those) |
| Signed in but "Your account is not assigned to a workspace" | Expected for `unmapped@acme.example`. For a demo user it means the backend was not started with the `dev` profile, so the principal mapping was not seeded |
| Sign-in works but every API call returns 401 in a non-dev profile over plain http | The session cookie is `Secure`; browsers only send it over https. The `dev` profile turns that off for localhost |
| `Port 5173 is in use` | Vite is configured with a fixed port because the realm's redirect URIs depend on it. Stop the other process |
| Backend cannot connect to the database | Check `PALCHECK_DB_URL`, user and password, and that PostgreSQL 17 accepts connections on that port |
| `mvnw` fails with a Java version error | Set `JAVA_HOME` to JDK 17 or newer |
| Playwright: "Executable doesn't exist" | Run `npx playwright install chromium`, or use `PALCHECK_E2E_CHANNEL=chrome` |
| Tests: first `./mvnw verify` is slow | It downloads the embedded PostgreSQL binaries once |

## Not part of Phase 1

No message broker, outbox dispatch, HRIS connector or simulator, payroll, country-policy execution, provisioning, incident management, dashboards or mobile app. See [PHASE_1_IMPLEMENTATION_PLAN.md](PHASE_1_IMPLEMENTATION_PLAN.md) section 2.

## Phase 1 review additions

**PostgreSQL extension prerequisite.** V6 installs the bundled trusted `unaccent` extension into the `workforce` schema. Your PostgreSQL 17 distribution must supply its contrib files, and the migration role needs the CREATE privileges required to install it. If unaccent already exists in another schema, migration fails explicitly; do not relocate a shared extension blindly. Use a dedicated PalCheck database or deliberately provision the expected schema with the database owner. Both fresh integration databases and the existing local development database passed V6. See [PostgreSQL documentation](https://www.postgresql.org/docs/17/unaccent.html). Runtime least-privilege credentials separate from migration credentials remain a deployment milestone.

People and manager search ignore accents and case while preserving the stored names. Manager lookup pages employments server-side; it does not load every person. Search is a folded substring scan within the tenant, without a measured large-tenant latency guarantee.

API POST/PUT/PATCH bodies under `/api/` are limited to 64 KiB. Logout uses a CSRF-protected native form and a provider redirect. Framework web/security/JDBC logging defaults explicitly to INFO even if a generic DEBUG environment flag is inherited; do not override these with personal data in use. Infrastructure and database logging need their own controls.

[security.yml](../.github/workflows/security.yml) prepares secret scanning, npm audit, and public-PR dependency review; [Dependabot](../.github/dependabot.yml) covers Maven/npm/Actions updates. These workflows have not executed on GitHub. Dependency graph, native secret scanning/push protection, and complete Java advisory coverage require hosted verification. See [the final review](PHASE_1_CODEX_REVIEW.md) for exact test results and limitations.

## Idempotent write retries

The **guaranteed retry window** defaults to 24 hours through `palcheck.idempotency.ttl: PT24H` in backend application.yml (environment override `PALCHECK_IDEMPOTENCY_TTL`, or command argument `--palcheck.idempotency.ttl=PT48H`). The compatible property name means the retry guarantee, not permission to repeat an expired operation. Values below 24 hours fail startup; longer durations apply to newly completed writes without changing existing receipt deadlines. The duration is a product choice and can evolve through an explicit contract change.

Within the window, an authorized retry with the same tenant/principal/operation/key and equivalent payload reconstructs the current representation of the prior logical result without repeating the write. A materially different payload conflicts. After the window, the original receipt may be deleted while its business effect remains. Reconcile the outcome before another action; do not infer safe or equivalent repetition. Exact boundary, status/header, normalization and purge semantics are in the [API contract](PHASE_1_IMPLEMENTATION_PLAN.md#guaranteed-retry-window-api-contract-and-configuration).

## Phase 2 simulation walkthrough

Start the backend with the `dev` profile and sign in as the synthetic Acme HR administrator. The dev seeder idempotently adds the fictional Northwind reference legal entities and Engineering/Support units required by the simulation, including in an existing dev tenant. No existing Person is modified by this seed step.

1. Open **Enterprise Systems**, then **Workday Simulation**. This is an in-process reference simulation, with no live provider connection or credentials.
2. Select **Baseline workers** and **Run sync**. A fresh tenant gets 6 processed records, 4 created workers and 3 failed outcomes: two worker imports and one manager link on an otherwise imported worker. The seven outcome rows are intentional; the page explains the distinction.
3. Inspect a failed item, then open worker **W-1002**. Employment/Assignment say **Managed by Workday Simulation** and have no local edit action. Identity remains editable. Open Assignment **View source** to see `Business_Title` mapped to `assignment.title`, the worker reference, mapping version and recorded run.
4. Return to the system and choose the changed-title scenario. Run sync: one title updates to **Senior Support Analyst**, with history attributed to **Workday Simulation**. Repeating that scenario produces four unchanged workers and no duplicates. Choosing baseline again intentionally restores the original title.
5. Sign in as HR Viewer to verify Systems navigation and source disclosure are unavailable. Server permissions also reject direct integration requests.

Scenario selection is enabled by the backend only for `dev`/`test`; the normal baseline POST remains available to an authorized administrator. It selects a fixture for that one pull, not a global provider mutation. Each POST creates a new run. Sync effect idempotency is independent of Phase 1's request-key receipt window.

The backend runs the small pull synchronously; there is no background job or percentage progress. If a response is lost, inspect history before another run. Workforce/audit commits are per worker, followed by separate outcome/lineage writes; process/database failure can leave incomplete run evidence. See the [Phase 2 review](PHASE_2_CODEX_REVIEW.md) for exact limits and final test evidence.
