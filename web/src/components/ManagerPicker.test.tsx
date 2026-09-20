import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { mockNetwork, renderApp } from '../test/harness'
import { session, legalEntity, orgUnits } from '../test/fixtures'

afterEach(() => vi.unstubAllGlobals())

it('searches and pages on the server while retaining a selected manager outside the result page', async () => {
  const candidate = (id: string, name: string) => ({ employmentId: id, personId: id, displayName: name, title: 'Engineer', legalEntityName: 'Demo entity', status: 'ACTIVE' })
  const network = mockNetwork({
    'GET /api/session': { body: session() },
    'GET /api/legal-entities': { body: { items: [legalEntity] } },
    'GET /api/org-units': { body: { items: orgUnits } },
    'GET /api/manager-candidates': (call) => {
      const url = new URL(call.path, 'http://localhost')
      const page = Number(url.searchParams.get('page'))
      const q = url.searchParams.get('q')
      return { body: { items: [candidate(q ? 'e-z' : page ? 'e-b' : 'e-a', q ? 'Zoe Solis' : page ? 'Bea Mora' : 'Ana Vargas')], page, size: 20, totalItems: q ? 1 : 101, totalPages: q ? 1 : 6 } }
    },
  })
  renderApp('/people/new')
  await screen.findByRole('option', { name: /Ana Vargas/ })
  await userEvent.selectOptions(screen.getByRole('combobox', { name: /^Manager/ }), 'e-a')
  await userEvent.click(screen.getByRole('button', { name: 'Next' }))
  await screen.findByRole('option', { name: /Bea Mora/ })
  expect(screen.getByRole('combobox', { name: /^Manager/ })).toHaveValue('e-a')
  await userEvent.type(screen.getByLabelText('Find a manager by name or email'), 'Solis')
  await screen.findByRole('option', { name: /Zoe Solis/ })
  await waitFor(() => expect(network.callsTo('GET', '/api/manager-candidates').at(-1)?.path).toContain('q=Solis&page=0&size=20'))
  expect(screen.getByRole('option', { name: 'Ana Vargas' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: /^Manager/ })).toHaveValue('e-a')
})
