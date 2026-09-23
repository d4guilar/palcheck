import { expect, test } from '@playwright/test'
import { createPerson, signIn, signInAs, users } from './support'

test.describe('Workforce foundation, end to end', () => {
  test('an HR administrator signs in, creates a person, edits them, and sees the history', async ({ page }) => {
    await signInAs(page, users.acmeAdmin)
    await expect(page.getByText('Rebeca Umaña Salas', { exact: true })).toBeVisible()
    // Synthetic seed data is there.
    await page.getByLabel('Search people').fill('Solis')
    await expect(page.getByRole('link', { name: 'Mariana Solís Vega' })).toBeVisible()

    const person = await createPerson(page)
    await expect(page.getByText('Record created.')).toBeVisible()
    await expect(page.getByRole('region', { name: 'Change history' }).getByText('Created record')).toBeVisible()

    // Edit identity with a reason.
    await page.getByRole('button', { name: 'Edit identity' }).click()
    const updatedEmail = person.email.replace('@', '.updated@')
    await page.getByLabel('Work email').fill(updatedEmail)
    await page.getByLabel(/Reason for the change/).fill('End-to-end test correction')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByText('Changes saved.')).toBeVisible()
    await expect(page.getByRole('region', { name: 'Identity' }).getByText(updatedEmail)).toBeVisible()

    const history = page.getByRole('region', { name: 'Change history' })
    await expect(history.getByText('Updated identity')).toBeVisible()
    await expect(history.getByText('Reason: End-to-end test correction')).toBeVisible()
    await expect(history.getByText('by Rebeca Umaña Salas').first()).toBeVisible()

    // The record is findable from the directory.
    await page.getByRole('link', { name: /Back to people/ }).click()
    await page.getByLabel('Search people').fill(person.lastName)
    await expect(page.getByRole('link', { name: person.displayName })).toBeVisible()
  })

  test('a stale edit is refused with a conflict message and nothing is overwritten', async ({ page, context }) => {
    await signInAs(page, users.acmeAdmin)
    const person = await createPerson(page)

    // A second tab of the same person opens the same record.
    const other = await context.newPage()
    await other.goto(page.url())
    await expect(other.getByRole('heading', { level: 1, name: person.displayName })).toBeVisible()

    await page.getByRole('button', { name: 'Edit identity' }).click()
    await page.getByLabel('Preferred name').fill('Emi')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByText('Changes saved.')).toBeVisible()

    // The second tab still holds the old revision.
    await other.getByRole('button', { name: 'Edit identity' }).click()
    await other.getByLabel('Preferred name').fill('Someone Else')
    await other.getByRole('button', { name: 'Save changes' }).click()
    const alert = other.getByRole('alert')
    await expect(alert).toContainText('This record changed since you opened it')
    await expect(alert).toBeFocused()

    // Loading the latest version shows what was saved first; the stale change did not overwrite it.
    await other.getByRole('button', { name: 'Load the latest version' }).click()
    await expect(other.getByLabel('Preferred name')).toHaveValue('Emi')
  })

  test('a viewer can read records but has no way to change them', async ({ page }) => {
    await signInAs(page, users.acmeViewer)
    await expect(page.getByRole('link', { name: 'New person' })).toHaveCount(0)
    await page.getByLabel('Search people').fill('Solis')
    await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Mariana Solís Vega' })).toBeVisible()
    await expect(page.getByRole('button', { name: /^Edit/ })).toHaveCount(0)

    // Going straight to the create form explains why it is unavailable instead of failing on submit.
    await page.goto('/people/new')
    await expect(page.getByRole('alert')).toContainText('You do not have permission to make this change.')
  })

  test('one tenant cannot see or open another tenant’s records', async ({ page, browser }) => {
    // Find an Acme person's record URL as the Acme administrator.
    await signInAs(page, users.acmeAdmin)
    await page.getByLabel('Search people').fill('Solis')
    await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Mariana Solís Vega' })).toBeVisible()
    const acmeRecordUrl = page.url()

    // The Globex administrator sees only Globex people, and the Acme record does not exist for them.
    const globexContext = await browser.newContext()
    const globex = await globexContext.newPage()
    await signInAs(globex, users.globexAdmin)
    await expect(globex.getByRole('link', { name: 'Mariana Solís Vega' })).toHaveCount(0)
    await globex.goto(acmeRecordUrl)
    await expect(globex.getByRole('heading', { level: 1, name: 'Record not found' })).toBeVisible()
    await globexContext.close()
  })

  test('an identity with no workspace is told so and cannot see any data', async ({ page }) => {
    await signIn(page, users.unmapped)
    await expect(page.getByRole('heading', { name: 'Your account is not assigned to a workspace' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Mariana Solís Vega' })).toHaveCount(0)
  })

  test('signing out ends the session', async ({ page }) => {
    await signInAs(page, users.acmeAdmin)
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page.getByRole('heading', { name: 'Sign in to PalCheck' })).toBeVisible()
    // The session is really gone, not just hidden.
    const response = await page.request.get('/api/people')
    expect(response.status()).toBe(401)
  })

  test('the create form reports server validation in an accessible summary', async ({ page }) => {
    await signInAs(page, users.acmeAdmin)
    await page.getByRole('link', { name: 'New person' }).first().click()
    await page.getByRole('button', { name: 'Create record' }).click()
    const summary = page.getByRole('alert').filter({ hasText: 'problems with this form' })
    await expect(summary).toBeVisible()
    await expect(summary).toBeFocused()
    // Each entry links to its control.
    await summary.getByRole('link').first().click()
    await expect(page.locator(':focus')).toHaveAttribute('id', /^new-/)
  })
})
