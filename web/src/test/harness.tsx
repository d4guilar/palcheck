import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { IntlProvider } from 'react-intl'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'
import App from '../App'
import type { Problem } from '../api/types'
import { en } from '../i18n/en'
import { SessionProvider } from '../session/SessionProvider'

export interface Reply {
  /** Simulates a lost connection: the request fails before any response, so its outcome is unknown to the caller. */
  networkError?: boolean
  status?: number
  body?: unknown
  headers?: Record<string, string>
}

export interface RecordedCall {
  method: string
  path: string
  headers: Record<string, string>
  body: unknown
}

type Handler = Reply | ((call: RecordedCall) => Reply | Promise<Reply>)

/**
 * A fake network at the fetch boundary. Routes are "METHOD /path": the exact path with its query string wins, then the
 * path alone. An unrouted call throws, so a test fails loudly instead of silently getting an empty response.
 */
export function mockNetwork(routes: Record<string, Handler>) {
  const calls: RecordedCall[] = []
  const table = new Map(Object.entries(routes))

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const url = new URL(String(input), 'http://localhost')
    const headers: Record<string, string> = {}
    new Headers(init?.headers).forEach((value, key) => {
      headers[key.toLowerCase()] = value
    })
    const call: RecordedCall = {
      method,
      path: url.pathname + url.search,
      headers,
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    calls.push(call)
    const handler = table.get(`${method} ${call.path}`) ?? table.get(`${method} ${url.pathname}`)
    if (!handler) throw new Error(`Unrouted request in test: ${method} ${call.path}`)
    const reply = typeof handler === 'function' ? await handler(call) : handler
    if (reply.networkError) throw new TypeError('Failed to fetch')
    const status = reply.status ?? 200
    return new Response(status === 204 ? null : JSON.stringify(reply.body ?? {}), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json', ...reply.headers },
    })
  })
  vi.stubGlobal('fetch', fetchMock)

  return {
    calls,
    /** Requests that reached the network for a method and path prefix. */
    callsTo: (method: string, pathPrefix: string) =>
      calls.filter((c) => c.method === method && c.path.startsWith(pathPrefix)),
    set: (route: string, handler: Handler) => table.set(route, handler),
  }
}

export const problem = (status: number, code: Problem['code'], extra: Partial<Problem> = {}): Reply => ({
  status,
  body: {
    type: `https://palcheck.example/problems/${code.toLowerCase()}`,
    title: code,
    status,
    code,
    requestId: 'req-test',
    ...extra,
  },
})

/** Renders the whole application at a route, with the same providers as main.tsx and a fresh, non-retrying query cache. */
export function renderApp(path = '/people') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0 } } })
  const rendered = render(
    <IntlProvider locale="en" messages={en}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[path]}>
          <SessionProvider>
            <App />
          </SessionProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </IntlProvider>,
  )
  return { ...rendered, queryClient }
}
