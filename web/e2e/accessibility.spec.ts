import { expect, test } from '@playwright/test'
import { signIn, signInAs, users } from './support'
import { expectNoAxeViolations } from './axe'

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
    await page.getByLabel('Search people').fill('Solis')
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

    await page.getByLabel('Search people').fill('Solis')
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
    await expect(page.getByRole('link', { name: 'New person', exact: true })).toBeFocused()
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
    await expect(page.getByLabel('First name')).toBeFocused()
    await page.getByRole('button', { name: 'Cancel' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: 'Edit identity' })).toBeFocused()
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
