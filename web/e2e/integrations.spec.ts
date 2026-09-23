import { expect, test, type Page } from '@playwright/test'
import { signInAs, users } from './support'
import { expectNoAxeViolations } from './axe'

async function openSystem(page: Page) {
  await page.getByRole('link', { name: 'Enterprise Systems', exact: true }).click()
  await page.getByRole('link', { name: 'Workday Simulation', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Workday Simulation' })).toBeVisible()
}
async function runScenario(page: Page, scenario: string) {
  await page.getByLabel('Simulation scenario').selectOption(scenario)
  await page.getByRole('button', { name: 'Run sync', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Synchronization run' })).toBeVisible()
  await expect(page.getByText('Completed with warnings', { exact: true })).toBeVisible()
}
async function openAnalyst(page: Page) {
  const row = page.locator('.sync-items > li').filter({ has: page.getByRole('heading', { name: 'W-1002', exact: true }) })
  await row.getByRole('link', { name: 'Open workforce record' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Carlos Enrique Jiménez Solano' })).toBeVisible()
}

test('Phase 2: real sync, partial outcomes, imported worker, ownership and keyboard lineage', async ({ page }) => {
  await signInAs(page, users.acmeAdmin)
  await page.getByRole('link', { name: 'Enterprise Systems', exact: true }).click()
  await expectNoAxeViolations(page, 'systems')
  await page.getByRole('link', { name: 'Workday Simulation' }).click()
  await expect(page.getByRole('button', { name: 'Run sync' })).toBeVisible()
  await expectNoAxeViolations(page, 'system detail')
  await runScenario(page, 'BASELINE')
  await expect(page.getByText('Synchronization completed. 3 outcomes need attention.')).toBeVisible()
  const failed = page.locator('.sync-items > li').filter({ has: page.getByRole('heading', { name: 'W-1004', exact: true }) })
  await failed.getByText('View reason').click()
  await expect(failed.getByText('No matching legal entity')).toBeVisible()
  await expectNoAxeViolations(page, 'sync outcome detail')
  await openAnalyst(page)
  await expect(page.getByText('Managed by Workday Simulation')).toHaveCount(2)
  await expect(page.getByRole('button', { name: 'Edit employment' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Edit assignment' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Edit identity' })).toBeVisible()
  const assignment = page.getByRole('group', { name: 'Assignment', exact: true })
  await assignment.getByText('View source', { exact: true }).focus()
  await page.keyboard.press('Enter')
  await expect(assignment.getByText('Business_Title', { exact: true })).toBeVisible()
  await expect(assignment.getByText('assignment.title', { exact: true })).toBeVisible()
  await expect(assignment.getByText('W-1002', { exact: true })).toBeVisible()
  await expectNoAxeViolations(page, 'source disclosure light')
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 320, height: 900 })
  await expectNoAxeViolations(page, 'source disclosure narrow dark')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('Phase 2: deterministic source update and repeated sync use the real stack', async ({ page }) => {
  await signInAs(page, users.acmeAdmin); await openSystem(page); await runScenario(page, 'BASELINE')
  await page.getByRole('link', { name: 'Workday Simulation', exact: true }).click()
  await runScenario(page, 'TITLE_CHANGE')
  await expect(page.locator('.sync-summary > div').filter({ has: page.getByText('Updated', { exact: true }) }).locator('dd')).toHaveText('1')
  const changedRunUrl = page.url()
  await openAnalyst(page)
  await expect(page.getByText('Senior Support Analyst · Support', { exact: true })).toBeVisible()
  await expect(page.getByText('Source: Workday Simulation').first()).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Record synchronized' }).first()).toBeVisible()
  const personUrl = page.url()
  await page.goto(changedRunUrl)
  await page.getByRole('link', { name: 'Workday Simulation', exact: true }).click()
  await runScenario(page, 'TITLE_CHANGE')
  await expect(page.locator('.sync-summary > div').filter({ has: page.getByText('Unchanged', { exact: true }) }).locator('dd')).toHaveText('4')
  await expect(page.locator('.sync-summary > div').filter({ has: page.getByText('Created', { exact: true }) }).locator('dd')).toHaveText('0')
  await openAnalyst(page); expect(page.url()).toBe(personUrl)
})

test('Phase 2: viewer cannot navigate to systems or retrieve lineage', async ({ page }) => {
  await signInAs(page, users.acmeViewer)
  await expect(page.getByRole('link', { name: 'Enterprise Systems' })).toHaveCount(0)
  await page.goto('/systems')
  await expect(page.getByText('You do not have permission to view this area.')).toBeVisible()
  expect((await page.request.get('/api/systems')).status()).toBe(403)
  expect((await page.request.get('/api/people/00000000-0000-0000-0000-000000000001/lineage')).status()).toBe(403)
})
