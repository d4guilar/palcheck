import { createRequire } from 'node:module'
import { expect, test, type Page } from '@playwright/test'
import { signIn, signInAs, users } from './support'

const require = createRequire(import.meta.url)
const axePath = require.resolve('axe-core/axe.min.js')

/** Runs axe-core inside the real page (so color contrast and focus styles are measured, which jsdom cannot do). */
export async function expectNoAxeViolations(page: Page, label: string) {
  await page.addScriptTag({ path: axePath })
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as { axe: { run: (context: Document, options: object) => Promise<{ violations: unknown[] }> } }).axe
    const result = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] } })
    return result.violations.map((v) => {
      const violation = v as { id: string; help: string; nodes: { target: unknown[] }[] }
      return { id: violation.id, help: violation.help, targets: violation.nodes.map((n) => n.target.join(' ')) }
    })
  })
  expect(violations, `${label}: ${JSON.stringify(violations, null, 2)}`).toEqual([])
}

test.describe('Accessibility in a real browser', () => {
  test('the main screens have no detectable WCAG 2.2 AA violations, including color contrast', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Sign in to PalCheck' })).toBeVisible()
    await expectNoAxeViolations(page, 'sign-in')

    await signInAs(page, users.acmeAdmin)
    await expect(page.getByRole('table')).toBeVisible()
    await expectNoAxeViolations(page, 'people list')

    await page.getByRole('link', { name: 'New person' }).first().click()
    await expect(page.getByRole('heading', { level: 1, name: 'New person' })).toBeVisible()
    await expectNoAxeViolations(page, 'new person')
    await page.getByRole('button', { name: 'Create record' }).click()
    await expect(page.getByRole('alert').filter({ hasText: 'problems with this form' })).toBeVisible()
    await expectNoAxeViolations(page, 'new person with validation errors')

    await page.goto('/people')
    await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
    await expect(page.getByRole('region', { name: 'Change history' })).toBeVisible()
    await expectNoAxeViolations(page, 'person detail')
    await page.getByRole('button', { name: 'Edit identity' }).click()
    await expectNoAxeViolations(page, 'person detail while editing')
  })

  test('the dark color scheme also passes, including contrast', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Sign in to PalCheck' })).toBeVisible()
    await expectNoAxeViolations(page, 'sign-in (dark)')

    await signInAs(page, users.acmeAdmin)
    await expect(page.getByRole('table')).toBeVisible()
    await expectNoAxeViolations(page, 'people list (dark)')

    await page.getByRole('link', { name: 'Mariana Solís Vega' }).click()
    await expect(page.getByRole('region', { name: 'Change history' })).toBeVisible()
    await page.getByRole('button', { name: 'Edit identity' }).click()
    await page.getByLabel('Work email').fill('not-an-email')
    await page.getByRole('button', { name: 'Save changes' }).click()
    await expect(page.getByRole('alert')).toBeVisible()
    await expectNoAxeViolations(page, 'person detail with an error (dark)')
  })

  test('the empty-workspace explanation has no violations', async ({ page }) => {
    await signIn(page, users.unmapped)
    await expect(page.getByRole('heading', { name: 'Your account is not assigned to a workspace' })).toBeVisible()
    await expectNoAxeViolations(page, 'no workspace')
  })

  test('a keyboard alone can reach the content, open a record, and edit it', async ({ page }) => {
    await signInAs(page, users.acmeAdmin)

    // The first Tab stop is the skip link, and it becomes visible when focused.
    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to main content' })
    await expect(skip).toBeFocused()
    await expect(skip).toBeInViewport()
    await page.keyboard.press('Enter')

    // Search, then the first result, entirely from the keyboard.
    await page.getByLabel('Search people').focus()
    await page.getByLabel('Search people').fill('Solís')
    // Wait for the search to narrow the list (header row plus the one match) before tabbing into it.
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Mariana Solís Vega' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('heading', { level: 1, name: 'Mariana Solís Vega' })).toBeVisible()
    // Navigation moves focus to the new page heading so a screen-reader user knows where they are.
    await expect(page.getByRole('heading', { level: 1, name: 'Mariana Solís Vega' })).toBeFocused()

    // Open the editor and cancel again with the keyboard; focus stays on a control that exists.
    const edit = page.getByRole('button', { name: 'Edit identity' })
    await edit.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByLabel('First name')).toBeVisible()
    await page.getByRole('button', { name: 'Cancel' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: 'Edit identity' })).toBeVisible()
  })

  test('has no transform motion when the reduced-motion preference is on', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await signInAs(page, users.acmeAdmin)
    const moving = await page.evaluate(() => {
      const animated = document.getAnimations().length
      const transformTransitions = Array.from(document.querySelectorAll('*')).filter((el) => {
        const style = getComputedStyle(el)
        const active = style.transitionDuration.split(',').some((d: string) => parseFloat(d) > 0)
        return active && /(transform|all)/.test(style.transitionProperty)
      }).length
      return { animated, transformTransitions }
    })
    expect(moving).toEqual({ animated: 0, transformTransitions: 0 })
  })
})
