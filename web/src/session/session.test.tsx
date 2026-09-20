import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { directoryItem, page, session } from '../test/fixtures'
import { mockNetwork, problem, renderApp } from '../test/harness'

const navigateTo = vi.hoisted(() => vi.fn())
vi.mock('../lib/navigate', () => ({ navigateTo }))

beforeEach(() => {
  navigateTo.mockClear()
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('session gate', () => {
  it('sends an unauthenticated visitor to sign in, by browser navigation and never with a token in script', async () => {
    mockNetwork({ 'GET /api/session': problem(401, 'UNAUTHENTICATED') })
    const { container } = renderApp('/people')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sign in to PalCheck' })).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()

    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(navigateTo).toHaveBeenCalledWith('/oauth2/authorization/oidc')
  })

  it('explains an identity that has no workspace, and offers sign out', async () => {
    mockNetwork({ 'GET /api/session': problem(403, 'TENANT_ACCESS_DENIED') })
    document.cookie = 'XSRF-TOKEN=logout-csrf'
    let submitted: { action: string | null; method: string; token?: string } | undefined
    vi.spyOn(HTMLFormElement.prototype, 'submit').mockImplementation(function (this: HTMLFormElement) {
      submitted = { action: this.getAttribute('action'), method: this.method, token: this.querySelector('input')?.value }
    })
    renderApp('/people')

    expect(await screen.findByRole('heading', { name: 'Your account is not assigned to a workspace' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(submitted?.action).toBe('/logout')
    expect(submitted?.method).toBe('post')
    expect(submitted?.token).toBe('logout-csrf')
    expect(navigateTo).not.toHaveBeenCalled()
  })

  it('shows a retryable error when the session cannot be loaded', async () => {
    let attempts = 0
    mockNetwork({
      'GET /api/session': () => (++attempts === 1 ? { networkError: true } : { body: session() }),
      'GET /api/people': { body: page([directoryItem()]) },
    })
    renderApp('/people')

    expect(await screen.findByRole('heading', { name: 'PalCheck could not load your session' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'People' })).toBeInTheDocument()
  })

  it('returns to sign-in, with an explanation, when the session ends while the app is open', async () => {
    let expired = false
    mockNetwork({
      'GET /api/session': () => (expired ? problem(401, 'UNAUTHENTICATED') : { body: session() }),
      'GET /api/people': () => (expired ? problem(401, 'UNAUTHENTICATED') : { body: page([directoryItem()]) }),
    })
    renderApp('/people')
    await screen.findByText('Mariana Vargas Mora')

    expired = true
    await userEvent.type(screen.getByLabelText('Search people'), 'Vargas')

    expect(await screen.findByRole('heading', { level: 1, name: 'Sign in to PalCheck' })).toBeInTheDocument()
    expect(screen.getByText('Your session has ended. Sign in again to continue.')).toBeInTheDocument()
    expect(screen.queryByText('Mariana Vargas Mora')).not.toBeInTheDocument()
  })

  it('shows the workspace and the signed-in user in the frame', async () => {
    mockNetwork({ 'GET /api/session': { body: session() }, 'GET /api/people': { body: page([directoryItem()]) } })
    renderApp('/people')
    expect(await screen.findByText('Ana Quesada')).toBeInTheDocument()
    expect(screen.getByText('Demo Tenant CR', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main')
  })
})
