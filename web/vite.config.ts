import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The browser only ever talks to one origin. In development that origin is the Vite dev server, which
// proxies the API and the OIDC login endpoints to the backend, so the session cookie stays same-origin
// and no tokens reach JavaScript (docs/adr/0022-phase1-technical-baseline.md).
const backend = process.env.PALCHECK_BACKEND_URL ?? 'http://localhost:8080'
const proxied = ['/api', '/oauth2', '/login', '/logout']

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(proxied.map((path) => [path, { target: backend, changeOrigin: false }])),
  },
  preview: {
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(proxied.map((path) => [path, { target: backend, changeOrigin: false }])),
  },
  test: {
    // axe/jsdom suites are CPU-heavy; cap parallel workers so their normal timeouts stay meaningful on laptops.
    maxWorkers: 2,
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
  },
})
