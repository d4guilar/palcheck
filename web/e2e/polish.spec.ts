import { expect, test } from '@playwright/test'
import { signInAs, users } from './support'
import { expectNoAxeViolations } from './axe'

test('accent search preserves displayed spelling and manager lookup searches employments', async ({ page }) => {
  await signInAs(page, users.acmeAdmin)
  await page.getByLabel('Search people').fill('Solis')
  await expect(page.getByRole('link', { name: 'Mariana Solís Vega' })).toBeVisible()
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
  await page.getByRole('button', { name: 'Edit assignment' }).click()
  await page.getByLabel('Find a manager by name or email').fill('Jose')
  await expect(page.getByRole('combobox', { name: /^Manager/ }).locator('option')).toContainText(['No manager', 'José Chaves Vargas'])
  // Do not save a reporting relationship merely to exercise discovery.
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
})

test('small screens reflow directory and forms without page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 })
  await signInAs(page, users.acmeAdmin)
  await expect(page.getByRole('table')).toBeVisible()
  const firstPerson = page.getByRole('row').filter({ hasText: 'Sofía Araya Quesada' })
  await expect(firstPerson.getByText('UX Designer', { exact: true })).toBeVisible()
  await expect(firstPerson.getByText('Product', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.getByRole('link', { name: 'New person', exact: true }).click()
  await expect(page.getByLabel('First name')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('search empty state and a failed directory request have usable recovery', async ({ page }) => {
  await signInAs(page, users.acmeAdmin)
  await page.getByLabel('Search people').fill('NoSuchSyntheticPerson')
  await expect(page.getByRole('button', { name: 'Clear search' })).toBeVisible()
  await page.getByRole('button', { name: 'Clear search' }).click()
  await expect(page.getByRole('table')).toBeVisible()
  await page.route('**/api/people?*', (route) => route.fulfill({ status: 500, contentType: 'application/problem+json', body: JSON.stringify({ code: 'INTERNAL_ERROR', status: 500 }) }))
  await page.reload()
  await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible({ timeout: 15000 })
  await page.unroute('**/api/people?*')
  await page.getByRole('button', { name: 'Try again' }).click()
  await expect(page.getByRole('table')).toBeVisible()
})

test('inline editors keep keyboard focus and metadata stays available on demand', async ({ page }) => {
  await signInAs(page, users.acmeAdmin)
  await page.getByLabel('Search people').fill('Solis')
  await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
  const identity = page.getByRole('region', { name: 'Identity', exact: true })
  await expect(identity.getByText(/^Revision/)).not.toBeVisible()
  await identity.getByText('Record details', { exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(identity.getByText(/^Revision/)).toBeVisible()

  for (const [action, control] of [['Edit identity', 'First name'], ['Edit employment', 'Status'], ['Edit assignment', 'Job title']] as const) {
    const edit = page.getByRole('button', { name: action, exact: true })
    await edit.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByLabel(control, { exact: true })).toBeFocused()
    await expectNoAxeViolations(page, action)
    await page.getByRole('button', { name: 'Cancel', exact: true }).focus()
    await page.keyboard.press('Enter')
    await expect(edit).toBeFocused()
  }
})

test('narrow dark layouts and forced colors preserve usable records and controls', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 })
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await signInAs(page, users.acmeAdmin)
  await page.getByLabel('Search people').fill('Solis')
  const person = page.getByRole('row').filter({ hasText: 'Mariana Solís Vega' })
  await expect(person.getByText('VP Engineering', { exact: true })).toBeVisible()
  await expectNoAxeViolations(page, 'narrow dark directory')
  await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
  await page.getByRole('button', { name: 'Edit employment', exact: true }).click()
  await expectNoAxeViolations(page, 'narrow dark employment editor')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.emulateMedia({ forcedColors: 'active' })
  await page.getByLabel('Employment type').focus()
  expect(await page.getByLabel('Employment type').evaluate(el => getComputedStyle(el).outlineStyle)).not.toBe('none')
  await page.getByRole('button', { name: 'Cancel', exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: 'Edit employment', exact: true })).toBeFocused()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
