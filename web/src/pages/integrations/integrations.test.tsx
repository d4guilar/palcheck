import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ALL_PERMISSIONS, session, page, person, employment, auditEntry } from '../../test/fixtures'
import { mockNetwork, problem, renderApp } from '../../test/harness'
import type { Permission } from '../../api/types'
import type { ExternalSystem, SyncRun, SyncItem } from './api'

const permissions: Permission[] = [...ALL_PERMISSIONS, 'VIEW_SYSTEMS', 'VIEW_SYNCS', 'RUN_SYNC', 'VIEW_LINEAGE']
const system: ExternalSystem = { id: 's-1', code: 'workday-simulation', displayName: 'Workday Simulation', type: 'HRIS', mode: 'SIMULATION', lastSyncAt: null, simulationScenariosEnabled: true }
const run: SyncRun = { id: 'r-1', systemId: 's-1', startedAt: '2026-09-22T12:00:00Z', completedAt: '2026-09-22T12:00:01Z', status: 'SUCCEEDED_WITH_FAILURES', recordsSeen: 6, createdCount: 4, updatedCount: 0, unchangedCount: 0, failedCount: 3 }
const item: SyncItem = { id: 'i-1', externalWorkerId: 'W-1002', status: 'SUCCEEDED', action: 'CREATED', failureReason: null, failureDetail: null, targetPersonId: 'p-1', targetEmploymentId: 'e-1', occurredAt: run.startedAt }
const base = (grants = permissions) => ({
  'GET /api/session': { body: session(grants) }, 'GET /api/systems': { body: page([system]) },
  'GET /api/systems/workday-simulation': { body: system }, 'GET /api/systems/workday-simulation/syncs': { body: page([run]) },
  'GET /api/syncs/r-1/summary': { body: run }, 'GET /api/syncs/r-1': { body: page([item]) },
})
afterEach(() => { vi.unstubAllGlobals(); document.cookie = 'XSRF-TOKEN=; Max-Age=0' })

it('shows an accessible systems list with an honest never-synced state', async () => {
  mockNetwork(base()); const { container } = renderApp('/systems')
  expect(await screen.findByRole('link', { name: 'Workday Simulation' })).toHaveAttribute('href', '/systems/workday-simulation')
  expect(screen.getByText('Not synchronized yet')).toBeInTheDocument()
  expect(screen.queryByText('Healthy')).not.toBeInTheDocument()
  expect(await axe(container)).toHaveNoViolations()
})

it('denies direct system routes without requesting protected resources', async () => {
  const network = mockNetwork(base(ALL_PERMISSIONS)); renderApp('/systems/workday-simulation')
  expect(await screen.findByText('You do not have permission to view this area.')).toBeInTheDocument()
  expect(network.callsTo('GET', '/api/systems')).toHaveLength(0)
  expect(screen.queryByRole('link', { name: 'Enterprise Systems' })).not.toBeInTheDocument()
})

it('keeps run, history and lineage permissions independent', async () => {
  const network = mockNetwork(base(['VIEW_SYSTEMS'])); renderApp('/systems/workday-simulation')
  await screen.findByRole('heading', { name: 'Workday Simulation' })
  expect(screen.queryByRole('button', { name: 'Run sync' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Recent synchronizations' })).not.toBeInTheDocument()
  expect(network.callsTo('GET', '/api/systems/workday-simulation/syncs')).toHaveLength(0)
})

it('runs a named simulation once, announces partial success and opens its durable result', async () => {
  document.cookie = 'XSRF-TOKEN=test-csrf'
  const network = mockNetwork({ ...base(), 'POST /api/systems/workday-simulation/sync': { body: run } })
  renderApp('/systems/workday-simulation'); const user = userEvent.setup()
  await user.selectOptions(await screen.findByLabelText('Simulation scenario'), 'TITLE_CHANGE')
  await user.click(screen.getByRole('button', { name: 'Run sync' }))
  await screen.findByRole('heading', { name: 'Synchronization run' })
  expect(await screen.findByText('Synchronization completed. 3 outcomes need attention.')).toBeInTheDocument()
  const calls = network.callsTo('POST', '/api/systems/workday-simulation/sync')
  expect(calls).toHaveLength(1); expect(calls[0]?.path).toContain('scenario=TITLE_CHANGE'); expect(calls[0]?.headers['x-xsrf-token']).toBe('test-csrf')
})

it('does not auto-retry a sync whose response is lost', async () => {
  const network = mockNetwork({ ...base(), 'POST /api/systems/workday-simulation/sync': { networkError: true } })
  renderApp('/systems/workday-simulation'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Run sync' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Review recent synchronizations')
  expect(network.callsTo('POST', '/api/systems/workday-simulation/sync')).toHaveLength(1)
})

it('distinguishes manager-link failure from a worker not imported, with accessible disclosure', async () => {
  mockNetwork({ ...base(), 'GET /api/syncs/r-1': { body: page([item, { ...item, id: 'i-2', externalWorkerId: 'W-1005', action: null, status: 'FAILED', targetPersonId: null, failureReason: 'INVALID_MANAGER_REFERENCE', failureDetail: 'The source manager was not found.' }]) } })
  const { container } = renderApp('/syncs/r-1'); const user = userEvent.setup()
  await user.click(await screen.findByText('View reason'))
  expect(screen.getByText(/The workforce record was imported/)).toBeVisible()
  expect(screen.getByText(/outcome counts may exceed processed records/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open workforce record' })).toHaveAttribute('href', '/people/p-1')
  expect(await axe(container)).toHaveNoViolations()
})

it('recovers systems load errors and represents an empty catalog', async () => {
  const network = mockNetwork({ ...base(), 'GET /api/systems': problem(500, 'INTERNAL_ERROR') })
  renderApp('/systems'); const user = userEvent.setup()
  await screen.findByRole('alert'); network.set('GET /api/systems', { body: page([]) })
  await user.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByRole('heading', { name: 'No systems configured' })).toBeInTheDocument()
})

function recordRoutes(grants = permissions) {
  const record = employment({ ownedBySystem: system.code })
  if (record.currentAssignment) record.currentAssignment.ownedBySystem = system.code
  return { ...base(grants), 'GET /api/people/p-1': { body: person() }, 'GET /api/people/p-1/employments': { body: { items: [record] } },
    'GET /api/people/p-1/audit': { body: page([auditEntry({ actor: { type: 'SYSTEM', id: null, displayName: system.displayName }, changes: [{ field: 'title', before: 'Support Analyst', after: 'Senior Support Analyst' }] })]) },
    'GET /api/people/p-1/lineage': { body: { items: [{ entityType: 'ASSIGNMENT', entityId: 'a-1', fieldPath: 'title', sourceSystem: system.code, sourceField: 'Business_Title', externalWorkerId: 'W-1002', importedAt: run.startedAt, mappingVersion: 'mock-v1', syncRunId: run.id }] } },
  }
}

it('makes externally owned areas read-only, discloses lineage lazily and names the audit source', async () => {
  const network = mockNetwork(recordRoutes()); const { container } = renderApp('/people/p-1'); const user = userEvent.setup()
  await screen.findAllByText('Managed by Workday Simulation')
  expect(screen.queryByRole('button', { name: 'Edit employment' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Edit assignment' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Edit identity' })).toBeInTheDocument()
  expect(network.callsTo('GET', '/api/people/p-1/lineage')).toHaveLength(0)
  await user.click(within(screen.getByRole('group', { name: 'Assignment' })).getByText('View source'))
  expect(await screen.findByText('Business_Title')).toBeVisible(); expect(screen.getByText('assignment.title')).toBeVisible()
  expect(screen.getByText('W-1002')).toBeVisible()
  expect(await screen.findByText('Source: Workday Simulation')).toBeInTheDocument()
  expect(await axe(container)).toHaveNoViolations()
})

it('shows ownership without leaking lineage to a caller missing VIEW_LINEAGE', async () => {
  const network = mockNetwork(recordRoutes(ALL_PERMISSIONS)); renderApp('/people/p-1')
  await screen.findAllByText('Managed by Workday Simulation')
  expect(screen.queryByText('View source')).not.toBeInTheDocument()
  expect(network.callsTo('GET', '/api/people/p-1/lineage')).toHaveLength(0)
})

it('handles authoritative EXTERNALLY_OWNED separately from stale-version conflict', async () => {
  const record = employment()
  mockNetwork({ ...recordRoutes(), 'GET /api/people/p-1/employments': { body: { items: [record] } }, 'PATCH /api/employments/e-1': problem(409, 'EXTERNALLY_OWNED') })
  renderApp('/people/p-1'); const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Edit employment' }))
  await user.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('managed by an external system'))
  expect(screen.queryByRole('button', { name: 'Review latest version' })).not.toBeInTheDocument()
})
