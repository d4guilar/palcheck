import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { auditEntry, directoryItem, employment, page, person, session } from '../test/fixtures'
import { mockNetwork, renderApp } from '../test/harness'

afterEach(() => {
  vi.unstubAllGlobals()
})

const routes = () => ({
  'GET /api/session': { body: session() },
  'GET /api/people': { body: page([directoryItem()], { totalItems: 60, totalPages: 3 }) },
  'GET /api/people/p-1': { body: person() },
  'GET /api/people/p-1/employments': { body: { items: [employment()] } },
  'GET /api/people/p-1/audit': { body: page([auditEntry()]) },
})

describe('Focus management', () => {
  it('leaves focus alone on first load, so the skip link is the first Tab stop', async () => {
    mockNetwork(routes())
    renderApp('/people')
    await screen.findByRole('table')
    expect(document.body).toHaveFocus()

    await userEvent.tab()
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveFocus()
  })

  it('does not move focus on the redirect from the root path', async () => {
    mockNetwork(routes())
    renderApp('/')
    await screen.findByRole('table')
    expect(document.body).toHaveFocus()
  })

  it('moves focus to the new page heading when the person navigates to a record', async () => {
    mockNetwork(routes())
    renderApp('/people')
    await userEvent.click(await screen.findByRole('link', { name: 'Mariana Vargas Mora' }))
    const heading = await screen.findByRole('heading', { level: 1, name: 'Mariana Vargas Mora' })
    await waitFor(() => expect(heading).toHaveFocus())
  })

  it('does not pull focus to the heading when paging within the list', async () => {
    mockNetwork(routes())
    renderApp('/people')
    const next = await screen.findByRole('button', { name: 'Next' })
    await userEvent.click(next)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled())
    expect(screen.getByRole('heading', { level: 1, name: 'People' })).not.toHaveFocus()
  })
})
