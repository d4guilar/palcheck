import { expect, type Page } from '@playwright/test'

/** The synthetic demo users seeded by the dev profile and the Keycloak realm (infra/keycloak/palcheck-realm.json). */
export const users = {
  acmeAdmin: 'hr.admin@acme.example',
  acmeViewer: 'hr.viewer@acme.example',
  globexAdmin: 'hr.admin@globex.example',
  unmapped: 'unmapped@acme.example',
} as const

// Development-only placeholder from .env.example. It is not a secret and is not valid anywhere else.
const password = process.env.PALCHECK_DEMO_USER_PASSWORD ?? 'change-me-local-dev-user'

/** Signs in through the real identity provider: the app sends the browser to Keycloak and back. */
export async function signIn(page: Page, email: string) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await page.locator('input[name="username"]').fill(email)
  await page.locator('input[name="password"]').fill(password)
  await page.locator('#kc-login').click()
}

export async function signInAs(page: Page, email: string) {
  await signIn(page, email)
  await expect(page.getByRole('heading', { level: 1, name: 'People' })).toBeVisible()
}

/** A fictional person unique to this run, so repeated runs against a persistent dev database never collide. */
export function newFictionalPerson() {
  const suffix = Date.now().toString(36)
  return {
    firstName: 'Emilia',
    lastName: `Prueba ${suffix}`,
    displayName: `Emilia Prueba ${suffix}`,
    email: `emilia.${suffix}@example.test`,
    title: 'Quality Analyst',
  }
}

/** Creates a person through the UI and lands on their record. */
export async function createPerson(page: Page, person = newFictionalPerson()) {
  await page.getByRole('link', { name: 'New person' }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: 'New person' })).toBeVisible()
  await page.getByLabel('First name').fill(person.firstName)
  await page.getByLabel('Family name').fill(person.lastName)
  await page.getByLabel('Work email').fill(person.email)
  await page.getByLabel('Legal entity').selectOption({ index: 1 })
  await page.getByLabel('Start date').fill('2026-03-02')
  await page.getByLabel('Job title').fill(person.title)
  await page.getByLabel('Organizational unit').selectOption({ index: 1 })
  await page.getByRole('button', { name: 'Create record' }).click()
  await expect(page.getByRole('heading', { level: 1, name: person.displayName })).toBeVisible()
  return person
}
