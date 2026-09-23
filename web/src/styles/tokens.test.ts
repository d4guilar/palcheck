import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// Tests run from the web/ directory (npm scripts), where the tokens live at a fixed path.
const tokensCss = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf-8')

/** WCAG 2.x relative luminance and contrast ratio. */
function luminance(hex: string): number {
  const channel = (i: number) => {
    const value = parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) / 255
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(0) + 0.7152 * channel(1) + 0.0722 * channel(2)
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x) as [number, number]
  return (hi + 0.05) / (lo + 0.05)
}

function colorsIn(block: string): Record<string, string> {
  const colors: Record<string, string> = {}
  for (const match of block.matchAll(/--(color-[a-z-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    colors[match[1] as string] = match[2] as string
  }
  return colors
}

function block(selectorPattern: RegExp): Record<string, string> {
  const match = tokensCss.match(selectorPattern)
  if (!match) throw new Error(`Token block not found: ${selectorPattern}`)
  return colorsIn(match[1] as string)
}

const themes: Record<string, Record<string, string>> = {
  light: block(/^:root\s*\{([^}]*)\}/m),
  dark: block(/:root\[data-theme="dark"\]\s*\{([^}]*)\}/),
}

/** [foreground token, background token, minimum ratio, why] */
const pairs: Array<[string, string, number, string]> = [
  ['color-text', 'color-bg', 4.5, 'body text'],
  ['color-text', 'color-surface', 4.5, 'body text on surfaces'],
  ['color-text', 'color-bg-subtle', 4.5, 'body text on subtle backgrounds'],
  ['color-text', 'color-selected', 4.5, 'selected navigation'],
  ['color-text', 'color-elevated', 4.5, 'elevated controls'],
  ['color-text-muted', 'color-elevated', 4.5, 'elevated control hints'],
  ['color-text-secondary', 'color-surface', 4.5, 'secondary text'],
  ['color-text-secondary', 'color-bg-subtle', 4.5, 'secondary text on subtle backgrounds'],
  ['color-text-muted', 'color-surface', 4.5, 'muted text'],
  ['color-text-muted', 'color-bg', 4.5, 'muted text on the page'],
  ['color-accent', 'color-bg', 4.5, 'links'],
  ['color-accent', 'color-surface', 4.5, 'links on surfaces'],
  ['color-accent-hover', 'color-bg', 4.5, 'link hover'],
  ['color-accent', 'color-accent-subtle', 4.5, 'quiet button text'],
  ['color-accent-hover', 'color-accent-subtle', 4.5, 'quiet button hover text'],
  ['color-accent-contrast', 'color-accent', 4.5, 'primary button'],
  ['color-accent-contrast', 'color-accent-hover', 4.5, 'primary button hover'],
  ['color-success-text', 'color-success-bg', 4.5, 'success tag and notice'],
  ['color-warning-text', 'color-warning-bg', 4.5, 'warning tag and notice'],
  ['color-error-text', 'color-error-bg', 4.5, 'error notice'],
  ['color-error-text', 'color-surface', 4.5, 'field error text'],
  ['color-neutral-text', 'color-neutral-bg', 4.5, 'neutral tag and notice'],
  // Non-text contrast: form control boundaries and the focus ring must be perceivable.
  ['color-border-strong', 'color-surface', 3, 'form control boundary'],
  ['color-border-strong', 'color-bg-subtle', 3, 'search control boundary'],
  ['color-border-strong', 'color-elevated', 3, 'focused control boundary'],
  ['color-focus', 'color-bg', 3, 'focus ring on the page'],
  ['color-focus', 'color-surface', 3, 'focus ring on surfaces'],
]

describe.each(Object.entries(themes))('%s theme tokens', (_name, tokens) => {
  it.each(pairs)('%s on %s is at least %s:1 (%s)', (foreground, background, minimum) => {
    const fg = tokens[foreground]
    const bg = tokens[background]
    expect(fg, `${foreground} is defined`).toBeDefined()
    expect(bg, `${background} is defined`).toBeDefined()
    expect(contrast(fg as string, bg as string)).toBeGreaterThanOrEqual(minimum)
  })
})

describe('dark tokens', () => {
  it('are the same in the system-preference block and the explicit dark theme block', () => {
    const media = block(/@media \(prefers-color-scheme: dark\)\s*\{\s*:root:not\(\[data-theme="light"\]\)\s*\{([^}]*)\}/)
    expect(media).toEqual(themes.dark)
  })
})
