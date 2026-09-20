import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import {
  ALL_PERMISSIONS,
  VIEWER_PERMISSIONS,
  auditEntry,
  directoryItem,
  employment,
  legalEntity,
  orgUnits,
  page,
  person,
  session,
} from '../test/fixtures'
import { mockNetwork, problem, renderApp } from '../test/harness'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

const created = person({ id: 'p-9', firstName: 'Lucía', lastName: 'Mora Jiménez', displayName: 'Lucía Mora Jiménez', version: 1 })

function baseRoutes(permissions = ALL_PERMISSIONS) {
  return {
    'GET /api/session': { body: session(permissions) },
    'GET /api/legal-entities': { body: { items: [legalEntity] } },
    'GET /api/org-units': { body: { items: orgUnits } },
    'GET /api/people': { body: page([directoryItem()]) },
    'GET /api/manager-candidates': { body: page([]) },
    'GET /api/people/p-9': { body: created },
    'GET /api/people/p-9/employments': { body: { items: [employment({ id: 'e-9', personId: 'p-9' })] } },
    'GET /api/people/p-9/audit': { body: page([auditEntry({ operation: 'CREATE', command: 'RegisterWorker', changes: [] })]) },
  }
}

async function fillValidForm() {
  await userEvent.type(screen.getByLabelText('First name'), 'Lucía')
  await userEvent.type(screen.getByLabelText('Family name'), 'Mora Jiménez')
  await userEvent.type(screen.getByLabelText('Work email'), 'lucia.mora@example.test')
  await userEvent.selectOptions(screen.getByLabelText('Legal entity'), legalEntity.id)
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-03-02' } })
  await userEvent.type(screen.getByLabelText('Job title'), 'Support Analyst')
  await userEvent.selectOptions(screen.getByLabelText('Organizational unit'), 'ou-2')
}

describe('New person', () => {
  it('is an accessible form with labelled controls', async () => {
    mockNetwork(baseRoutes())
    const { container } = renderApp('/people/new')
    expect(await screen.findByRole('heading', { level: 1, name: 'New person' })).toBeInTheDocument()
    await screen.findByRole('option', { name: /Demo Servicios/ })
    expect(screen.getByLabelText('First name')).toBeRequired()
    expect(screen.getByRole('group', { name: 'Employment' })).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('suggests the country of the chosen legal entity but leaves it editable', async () => {
    mockNetwork(baseRoutes())
    renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await userEvent.selectOptions(screen.getByLabelText('Legal entity'), legalEntity.id)
    expect(screen.getByLabelText('Country of employment')).toHaveValue('CR')
  })

  it('creates the record, sends an idempotency key, and opens it', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value'
    const network = mockNetwork({ ...baseRoutes(), 'POST /api/people': { status: 201, body: created } })
    renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Lucía Mora Jiménez' })).toBeInTheDocument()
    expect(screen.getByText('Record created.')).toBeInTheDocument()

    const [post] = network.callsTo('POST', '/api/people')
    expect(post?.headers['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/)
    expect(post?.headers['x-xsrf-token']).toBe('csrf-value')
    expect(post?.body).toEqual({
      person: { firstName: 'Lucía', lastName: 'Mora Jiménez', workEmail: 'lucia.mora@example.test', locale: 'en' },
      employment: { legalEntityId: 'le-1', status: 'ACTIVE', employmentType: 'FULL_TIME', country: 'CR', startDate: '2026-03-02' },
      assignment: { title: 'Support Analyst', orgUnitId: 'ou-2' },
    })
    // The tenant is never chosen by the browser.
    expect(JSON.stringify(post?.body)).not.toMatch(/tenant/i)
  })

  it('lists every validation problem in a summary that takes focus and links to the controls', async () => {
    mockNetwork({
      ...baseRoutes(),
      'POST /api/people': problem(422, 'VALIDATION_ERROR', {
        errors: [
          { field: 'person.lastName', code: 'REQUIRED', message: 'Must not be blank.' },
          { field: 'employment.startDate', code: 'REQUIRED', message: 'A start date is required.' },
        ],
      }),
    })
    const { container } = renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveTextContent('There are 2 problems with this form')
    await waitFor(() => expect(summary).toHaveFocus())

    const familyName = screen.getByLabelText('Family name')
    expect(familyName).toHaveAttribute('aria-invalid', 'true')
    expect(familyName).toHaveAccessibleDescription(/Must not be blank\./)
    expect(await axe(container)).toHaveNoViolations()

    await userEvent.click(screen.getByRole('link', { name: 'Family name: Must not be blank.' }))
    expect(familyName).toHaveFocus()
  })

  it('keeps the same idempotency key when a lost response is retried, so the record cannot be created twice', async () => {
    let attempt = 0
    const network = mockNetwork({
      ...baseRoutes(),
      'POST /api/people': () => (++attempt === 1 ? { networkError: true } : { status: 201, body: created }),
    })
    renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await fillValidForm()

    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('it will not be applied twice')
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))
    await screen.findByRole('heading', { level: 1, name: 'Lucía Mora Jiménez' })

    const keys = network.callsTo('POST', '/api/people').map((c) => c.headers['idempotency-key'])
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
  })

  it('uses a new key once the content has changed', async () => {
    let attempt = 0
    const network = mockNetwork({
      ...baseRoutes(),
      'POST /api/people': () => (++attempt === 1 ? { networkError: true } : { status: 201, body: created }),
    })
    renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await fillValidForm()
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))
    await screen.findByRole('alert')

    await userEvent.type(screen.getByLabelText('Job title'), ' II')
    await userEvent.click(screen.getByRole('button', { name: 'Create record' }))
    await screen.findByRole('heading', { level: 1, name: 'Lucía Mora Jiménez' })

    const keys = network.callsTo('POST', '/api/people').map((c) => c.headers['idempotency-key'])
    expect(keys[0]).not.toBe(keys[1])
  })

  it('cannot be submitted twice while a save is in flight', async () => {
    let release: () => void = () => undefined
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    const network = mockNetwork({
      ...baseRoutes(),
      'POST /api/people': async () => {
        await gate
        return { status: 201, body: created }
      },
    })
    renderApp('/people/new')
    await screen.findByRole('option', { name: /Demo Servicios/ })
    await fillValidForm()

    const button = screen.getByRole('button', { name: 'Create record' })
    await userEvent.click(button)
    const busy = await screen.findByRole('button', { name: 'Creating…' })
    expect(busy).toBeDisabled()
    await userEvent.click(busy)
    release()
    await screen.findByRole('heading', { level: 1, name: 'Lucía Mora Jiménez' })
    expect(network.callsTo('POST', '/api/people')).toHaveLength(1)
  })

  it('tells someone without permission that they cannot create records, instead of showing a form that fails', async () => {
    mockNetwork(baseRoutes(VIEWER_PERMISSIONS))
    renderApp('/people/new')
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to make this change.')
    expect(screen.queryByRole('button', { name: 'Create record' })).not.toBeInTheDocument()
  })
})
