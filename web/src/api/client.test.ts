import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, onUnauthenticated, request } from './client'
import { mockNetwork, problem } from '../test/harness'

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

describe('request', () => {
  it('sends the CSRF token and the idempotency key on writes, and never on reads', async () => {
    document.cookie = 'XSRF-TOKEN=csrf-value'
    const network = mockNetwork({ 'POST /api/x': { body: {} }, 'GET /api/x': { body: {} } })

    await request('/api/x', { method: 'POST', body: { a: 1 }, idempotencyKey: 'key-1' })
    await request('/api/x')

    const [write, read] = network.calls
    expect(write?.headers['x-xsrf-token']).toBe('csrf-value')
    expect(write?.headers['idempotency-key']).toBe('key-1')
    expect(write?.headers['content-type']).toBe('application/json')
    expect(read?.headers['x-xsrf-token']).toBeUndefined()
    expect(read?.headers['idempotency-key']).toBeUndefined()
  })

  it('never puts a tenant identifier on a request', async () => {
    const network = mockNetwork({ 'POST /api/people': { body: {} } })
    await request('/api/people', { method: 'POST', body: { person: { firstName: 'A' } }, idempotencyKey: 'k' })
    const call = network.calls[0]
    expect(JSON.stringify(call)).not.toMatch(/tenant/i)
  })

  it('exposes the problem document of a failed call, with its code', async () => {
    mockNetwork({
      'PATCH /api/people/p-1': problem(409, 'CONFLICT', { currentVersion: 4 }),
    })
    const error = await request('/api/people/p-1', { method: 'PATCH', body: {} }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(409)
    expect((error as ApiError).code).toBe('CONFLICT')
    expect((error as ApiError).problem?.currentVersion).toBe(4)
    expect((error as ApiError).ambiguous).toBe(false)
  })

  it('reports an ambiguous outcome when the connection is lost', async () => {
    mockNetwork({ 'POST /api/people': { networkError: true } })
    const error = await request('/api/people', { method: 'POST', body: {} }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).ambiguous).toBe(true)
    expect((error as ApiError).status).toBe(0)
  })

  it('flags a replayed idempotent response', async () => {
    mockNetwork({ 'POST /api/people': { status: 201, body: {}, headers: { 'Idempotency-Replayed': 'true' } } })
    const result = await request('/api/people', { method: 'POST', body: {} })
    expect(result.replayed).toBe(true)
  })

  it('tells listeners when the session is gone', async () => {
    mockNetwork({ 'GET /api/people': problem(401, 'UNAUTHENTICATED') })
    const listener = vi.fn()
    const unsubscribe = onUnauthenticated(listener)
    await request('/api/people').catch(() => undefined)
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
    await request('/api/people').catch(() => undefined)
    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('does not report the session check itself as a session that ended (that would loop)', async () => {
    mockNetwork({ 'GET /api/session': problem(401, 'UNAUTHENTICATED') })
    const listener = vi.fn()
    const unsubscribe = onUnauthenticated(listener)
    await request('/api/session', { sessionProbe: true }).catch(() => undefined)
    unsubscribe()
    expect(listener).not.toHaveBeenCalled()
  })

  it('does not treat an aborted request as a failure of the API', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new DOMException('aborted', 'AbortError')
      }),
    )
    const error = await request('/api/people').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(DOMException)
  })
})
