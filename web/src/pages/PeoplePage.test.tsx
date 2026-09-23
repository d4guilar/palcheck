import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ALL_PERMISSIONS, VIEWER_PERMISSIONS, directoryItem, page, session } from '../test/fixtures'
import { mockNetwork, problem, renderApp } from '../test/harness'

afterEach(() => {
  vi.unstubAllGlobals()
})

const rows = [
  directoryItem(),
  directoryItem({
    personId: 'p-2',
    displayName: 'Diego Rojas Solano',
    workEmail: 'diego.rojas@example.test',
    title: null,
    orgUnitName: null,
    legalEntityName: null,
    employmentStatus: null,
    employmentId: null,
  }),
]

describe('People list', () => {
  it('shows a loading state, then the directory, and passes an accessibility scan', async () => {
    mockNetwork({ 'GET /api/session': { body: session() }, 'GET /api/people': { body: page(rows) } })
    const { container } = renderApp('/people')

    expect(await screen.findByText('Loading people…')).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: 'People in Demo Tenant CR' })
    expect(within(table).getByRole('link', { name: 'Mariana Vargas Mora' })).toHaveAttribute('href', '/people/p-1')
    expect(within(table).getByText('Support Analyst')).toBeInTheDocument()
    expect(within(table).getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('2 people in Demo Tenant CR')).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows people who have no employment yet without pretending they have one', async () => {
    mockNetwork({ 'GET /api/session': { body: session() }, 'GET /api/people': { body: page(rows) } })
    renderApp('/people')
    const row = (await screen.findByRole('link', { name: 'Diego Rojas Solano' })).closest('tr') as HTMLElement
    expect(within(row).getByText('No employment')).toBeInTheDocument()
  })

  it('offers to create the first record when the directory is empty', async () => {
    mockNetwork({ 'GET /api/session': { body: session() }, 'GET /api/people': { body: page([]) } })
    const { container } = renderApp('/people')
    expect(await screen.findByRole('heading', { name: 'No people yet' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'New person' }).length).toBeGreaterThan(0)
    expect(await axe(container)).toHaveNoViolations()
  })

  it('does not offer creation to someone who may only view', async () => {
    mockNetwork({
      'GET /api/session': { body: session(VIEWER_PERMISSIONS) },
      'GET /api/people': { body: page([]) },
    })
    renderApp('/people')
    await screen.findByRole('heading', { name: 'No people yet' })
    expect(screen.queryByRole('link', { name: 'New person' })).not.toBeInTheDocument()
  })

  it('shows an error with a way to retry, and recovers', async () => {
    let failing = true
    mockNetwork({
      'GET /api/session': { body: session() },
      'GET /api/people': () => (failing ? problem(500, 'INTERNAL_ERROR') : { body: page(rows) }),
    })
    const { container } = renderApp('/people')

    expect(await screen.findByRole('alert')).toHaveTextContent('People could not be loaded')
    expect(await axe(container)).toHaveNoViolations()
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('searches after typing pauses and shows a specific message when nothing matches', async () => {
    const network = mockNetwork({
      'GET /api/session': { body: session() },
      'GET /api/people': (call) => (call.path.includes('q=zzz') ? { body: page([]) } : { body: page(rows) }),
    })
    renderApp('/people')
    await screen.findByRole('table')

    await userEvent.type(screen.getByLabelText('Search people'), 'zzz')
    expect(await screen.findByText('No people match “zzz”.')).toBeInTheDocument()

    const searches = network.callsTo('GET', '/api/people').filter((c) => c.path.includes('q='))
    // Typed as one burst, so it is one request, not one per keystroke.
    expect(searches).toHaveLength(1)
    expect(searches[0]?.path).toContain('q=zzz')
  })

  it('pages through a long directory', async () => {
    const network = mockNetwork({
      'GET /api/session': { body: session() },
      'GET /api/people': (call) =>
        call.path.includes('page=1')
          ? { body: page([directoryItem({ personId: 'p-30', displayName: 'Lucía Mora Jiménez' })], { page: 1, totalItems: 26, totalPages: 2 }) }
          : { body: page(rows, { totalItems: 26, totalPages: 2 }) },
    })
    renderApp('/people')
    await screen.findByText('Showing 1–25 of 26')
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Lucía Mora Jiménez')).toBeInTheDocument()
    expect(screen.getByText('Showing 26–26 of 26')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    await waitFor(() => expect(network.callsTo('GET', '/api/people?page=1')).toHaveLength(1))
  })

  it('lets a keyboard user move through the search and create toolbar into the records', async () => {
    mockNetwork({ 'GET /api/session': { body: session(ALL_PERMISSIONS) }, 'GET /api/people': { body: page(rows) } })
    renderApp('/people')
    await screen.findByRole('table')
    screen.getByLabelText('Search people').focus()
    await userEvent.tab()
    expect(screen.getByRole('link', { name: 'New person' })).toHaveFocus()
    await userEvent.tab()
    expect(screen.getByRole('link', { name: 'Mariana Vargas Mora' })).toHaveFocus()
  })
})
