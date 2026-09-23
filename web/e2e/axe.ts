import { createRequire } from 'node:module'
import { expect, type Page } from '@playwright/test'

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
