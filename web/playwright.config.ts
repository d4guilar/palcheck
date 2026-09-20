import { defineConfig } from '@playwright/test'

/**
 * End-to-end tests drive the real stack in a real browser: the Vite app, the Spring Boot backend, PostgreSQL and a
 * Keycloak development identity provider with the synthetic demo users. Start the stack first (see README.md, "Run it
 * locally"); these tests do not start or stop anything, and they only ever create clearly labelled fictional records.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.PALCHECK_E2E_URL ?? 'http://localhost:5173',
    // Use an installed browser (for example "msedge" or "chrome") instead of the bundled one when set.
    channel: process.env.PALCHECK_E2E_CHANNEL || undefined,
    trace: 'retain-on-failure',
    locale: 'en-US',
  },
})
