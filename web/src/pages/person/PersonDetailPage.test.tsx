import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ALL_PERMISSIONS, VIEWER_PERMISSIONS, auditEntry, employment, orgUnits, page, person, session } from '../../test/fixtures'
import { mockNetwork, problem, renderApp } from '../../test/harness'
import type { Permission } from '../../api/types'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

const history = page([
  auditEntry({ id: 'a2', changeSetId: 'cs-2', changes: [{ field: 'workEmail', before: 'old@example.test', after: 'mariana.vargas@example.test' }] }),
  auditEntry({
    id: 'a1',
    changeSetId: 'cs-1',
    operation: 'CREATE',
    command: 'RegisterWorker',
    occurredAt: '2026-05-01T09:00:00Z',
    reason: null,
    changes: [{ field: 'firstName', before: null, after: 'Mariana' }],
  }),
])

function routes(permissions: Permission[] = ALL_PERMISSIONS, overrides: Record<string, unknown> = {}) {
  return {
    'GET /api/session': { body: session(permissions) },
    'GET /api/people/p-1': { body: person() },
    'GET /api/people/p-1/employments': { body: { items: [employment()] } },
    'GET /api/people/p-1/audit': { body: history },
    'GET /api/org-units': { body: { items: orgUnits } },
    'GET /api/people': { body: page([]) },
    'GET /api/manager-candidates': { body: page([]) },
    ...overrides,
  } as Parameters<typeof mockNetwork>[0]
}

describe('Person detail', () => {
  it('shows identity, employment, assignment and history together, and passes an accessibility scan', async () => {
    mockNetwork(routes())
    const { container } = renderApp('/people/p-1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Mariana Vargas Mora' })).toBeInTheDocument()
    expect(screen.getByText('Support Analyst · Support')).toBeInTheDocument()
    const identity = screen.getByRole('region', { name: 'Identity' })
    expect(within(identity).getByText('mariana.vargas@example.test')).toBeInTheDocument()
    expect(within(identity).getByText('Revision 3')).not.toBeVisible()
    await userEvent.click(within(identity).getByText('Record details'))
    expect(within(identity).getByText('Revision 3')).toBeVisible()

    expect(await screen.findByRole('article', { name: /Demo Servicios/ })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Assignment' })).toHaveTextContent('Support Analyst')

    const historyRegion = await screen.findByRole('region', { name: 'Change history' })
    expect(within(historyRegion).getByText('Updated identity')).toBeInTheDocument()
    expect(within(historyRegion).getByText('Created record')).toBeInTheDocument()
    expect(within(historyRegion).getByText('Reason: Corrected a typo')).toBeInTheDocument()
    expect(within(historyRegion).getAllByText('by Ana Quesada', { exact: false })).toHaveLength(2)
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows a specific message for a record that is missing or not visible to this workspace', async () => {
    mockNetwork(routes(ALL_PERMISSIONS, { 'GET /api/people/p-1': problem(404, 'NOT_FOUND') }))
    renderApp('/people/p-1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Record not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to people/ })).toHaveAttribute('href', '/people')
  })

  it('shows a retryable error when the record cannot be loaded', async () => {
    let failing = true
    mockNetwork(routes(ALL_PERMISSIONS, { 'GET /api/people/p-1': () => (failing ? problem(500, 'INTERNAL_ERROR') : { body: person() }) }))
    renderApp('/people/p-1')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveAccessibleName('This record could not be loaded')
    expect(alert).toHaveTextContent('Check your connection and try again.')
    expect(screen.getAllByRole('heading', { name: 'This record could not be loaded' })).toHaveLength(1)
    failing = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Mariana Vargas Mora' })).toBeInTheDocument()
  })

  it('hides every edit control from someone who may only view, and hides sections they cannot see', async () => {
    mockNetwork(routes(['VIEW_PEOPLE', 'VIEW_PERSON']))
    renderApp('/people/p-1')
    await screen.findByRole('heading', { level: 1, name: 'Mariana Vargas Mora' })
    expect(screen.queryByRole('button', { name: /^Edit/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Employment' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Change history' })).not.toBeInTheDocument()
  })

  it('shows employment and history to a viewer, still without edit controls', async () => {
    const network = mockNetwork(routes(VIEWER_PERMISSIONS))
    renderApp('/people/p-1')
    await screen.findByRole('article', { name: /Demo Servicios/ })
    await screen.findByRole('region', { name: 'Change history' })
    expect(screen.queryByRole('button', { name: /^Edit/ })).not.toBeInTheDocument()
    expect(network.callsTo('PATCH', '/api')).toHaveLength(0)
  })

  it('does not ask for history the person is not allowed to see', async () => {
    const network = mockNetwork(routes(['VIEW_PEOPLE', 'VIEW_PERSON', 'VIEW_EMPLOYMENT']))
    renderApp('/people/p-1')
    await screen.findByRole('article', { name: /Demo Servicios/ })
    expect(network.callsTo('GET', '/api/people/p-1/audit')).toHaveLength(0)
  })

  describe('editing identity', () => {
    it('sends the revision the person saw, their changes and an idempotency key', async () => {
      document.cookie = 'XSRF-TOKEN=csrf-value'
      const network = mockNetwork(
        routes(ALL_PERMISSIONS, {
          'PATCH /api/people/p-1': { body: person({ version: 4, workEmail: 'mariana.v@example.test' }) },
        }),
      )
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))

      const email = screen.getByLabelText('Work email')
      await userEvent.clear(email)
      await userEvent.type(email, 'mariana.v@example.test')
      await userEvent.type(screen.getByLabelText(/Reason for the change/), 'Corrected the address')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByText('Changes saved.')).toBeInTheDocument()
      const [patch] = network.callsTo('PATCH', '/api/people/p-1')
      expect(patch?.body).toMatchObject({ expectedVersion: 3, workEmail: 'mariana.v@example.test', reason: 'Corrected the address' })
      expect(patch?.headers['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/)
      expect(patch?.headers['x-xsrf-token']).toBe('csrf-value')
      // Saving leaves edit mode.
      expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument()
    })

    it('lets the person cancel without sending anything', async () => {
      const network = mockNetwork(routes())
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))
      await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
      expect(screen.getByRole('button', { name: 'Edit identity' })).toBeInTheDocument()
      expect(network.calls.filter((c) => c.method !== 'GET')).toHaveLength(0)
    })

    it('explains a concurrent change, keeps what the person typed in view, and loads the latest version on request', async () => {
      let latest = person()
      const network = mockNetwork(
        routes(ALL_PERMISSIONS, {
          'GET /api/people/p-1': () => ({ body: latest }),
          'PATCH /api/people/p-1': () => {
            latest = person({ version: 4, workEmail: 'someone.else@example.test' })
            return problem(409, 'CONFLICT', { currentVersion: 4 })
          },
        }),
      )
      const { container } = renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))
      const email = screen.getByLabelText('Work email')
      await userEvent.clear(email)
      await userEvent.type(email, 'mine@example.test')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      const alert = await screen.findByRole('alert')
      expect(alert).toHaveTextContent('This record changed since you opened it')
      expect(alert).toHaveTextContent('Loading the latest version replaces this draft')
      await waitFor(() => expect(alert).toHaveFocus())
      expect(screen.getByLabelText('Work email')).toHaveValue('mine@example.test')
      expect(await axe(container)).toHaveNoViolations()

      await userEvent.click(screen.getByRole('button', { name: 'Load the latest version' }))
      await waitFor(() => expect(screen.getByLabelText('Work email')).toHaveValue('someone.else@example.test'))
      expect(screen.queryByText('This record changed since you opened it')).not.toBeInTheDocument()

      // The retry after reloading carries the new revision.
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      await waitFor(() => expect(network.callsTo('PATCH', '/api/people/p-1')).toHaveLength(2))
    })

    it('shows server validation next to the field and in a summary', async () => {
      mockNetwork(
        routes(ALL_PERMISSIONS, {
          'PATCH /api/people/p-1': problem(422, 'VALIDATION_ERROR', {
            errors: [{ field: 'workEmail', code: 'INVALID_EMAIL', message: 'Enter a valid email address.' }],
          }),
        }),
      )
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      expect(await screen.findByRole('alert')).toHaveTextContent('There is 1 problem with this form')
      expect(screen.getByLabelText('Work email')).toHaveAccessibleDescription('Enter a valid email address.')
    })

    it('reports a permission failure from the server without losing the form', async () => {
      mockNetwork(routes(ALL_PERMISSIONS, { 'PATCH /api/people/p-1': problem(403, 'FORBIDDEN') }))
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
      expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to make this change.')
      expect(screen.getByLabelText('Work email')).toBeInTheDocument()
    })
  })

  it('preserves unsaved identity values and their version through a background refetch', async () => {
    const network = mockNetwork(routes(ALL_PERMISSIONS))
    const { queryClient } = renderApp('/people/p-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit identity' }))
    await userEvent.clear(screen.getByLabelText('First name'))
    await userEvent.type(screen.getByLabelText('First name'), 'My draft')
    network.set('GET /api/people/p-1', { body: person({ firstName: 'Another editor', version: 9 }) })
    await act(async () => { await queryClient.refetchQueries({ queryKey: ['person', 'p-1'] }) })
    expect(screen.getByLabelText('First name')).toHaveValue('My draft')
    network.set('PATCH /api/people/p-1', problem(409, 'CONFLICT'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This record changed')
    expect(network.callsTo('PATCH', '/api/people/p-1')[0]?.body).toMatchObject({ expectedVersion: person().version })
  })

  describe('editing employment and assignment', () => {
    it('edits the assignment with its own revision', async () => {
      const network = mockNetwork(
        routes(ALL_PERMISSIONS, {
          'PATCH /api/assignments/a-1': { body: employment() },
          'GET /api/people': { body: page([]) },
    'GET /api/manager-candidates': { body: page([]) },
        }),
      )
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit assignment' }))
      const title = screen.getByLabelText('Job title')
      await userEvent.clear(title)
      await userEvent.type(title, 'Senior Support Analyst')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await screen.findByText('Changes saved.')
      const [patch] = network.callsTo('PATCH', '/api/assignments/a-1')
      expect(patch?.body).toMatchObject({ expectedVersion: 1, title: 'Senior Support Analyst' })
    })

    it('edits employment with its own revision, leaving the assignment alone', async () => {
      const network = mockNetwork(routes(ALL_PERMISSIONS, { 'PATCH /api/employments/e-1': { body: employment() } }))
      renderApp('/people/p-1')
      await userEvent.click(await screen.findByRole('button', { name: 'Edit employment' }))
      expect(screen.getByLabelText('Employment type')).toHaveAccessibleDescription(
        'Software classification used by PalCheck. Local legal interpretation may vary by country.',
      )
      await userEvent.selectOptions(screen.getByLabelText('Employment type'), 'PART_TIME')
      await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

      await screen.findByText('Changes saved.')
      const [patch] = network.callsTo('PATCH', '/api/employments/e-1')
      expect(patch?.body).toMatchObject({ expectedVersion: 2, employmentType: 'PART_TIME' })
      expect(network.callsTo('PATCH', '/api/assignments')).toHaveLength(0)
    })

    it('does not offer to edit an assignment that does not exist', async () => {
      mockNetwork(routes(ALL_PERMISSIONS, { 'GET /api/people/p-1/employments': { body: { items: [employment({ currentAssignment: null })] } } }))
      renderApp('/people/p-1')
      expect(await screen.findByText('No current assignment.')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Edit assignment' })).not.toBeInTheDocument()
    })
  })
})
