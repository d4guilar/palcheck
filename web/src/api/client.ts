import type { Problem } from './types'

/** A failed API call. `problem` is present when the server returned a problem document. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | null
  /** True when the request may or may not have reached the server (network failure, timeout). */
  readonly ambiguous: boolean

  constructor(status: number, problem: Problem | null, ambiguous = false, message?: string) {
    super(message ?? problem?.detail ?? problem?.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
    this.ambiguous = ambiguous
  }

  get code(): Problem['code'] | null {
    return this.problem?.code ?? null
  }
}

export interface ApiResult<T> {
  data: T
  status: number
  /** True when the server answered an idempotent retry with the original result. */
  replayed: boolean
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH'
  body?: unknown
  idempotencyKey?: string
  signal?: AbortSignal
  /**
   * Marks the call that decides whether there is a session at all. A 401 is its normal answer, not "the session ended",
   * so it does not notify listeners (which would re-check the session, get a 401, and loop).
   */
  sessionProbe?: boolean
}

type UnauthenticatedListener = () => void
const unauthenticatedListeners = new Set<UnauthenticatedListener>()

/** Registers a callback for "the session is gone" (a 401 on any call). Returns an unsubscribe function. */
export function onUnauthenticated(listener: UnauthenticatedListener): () => void {
  unauthenticatedListeners.add(listener)
  return () => unauthenticatedListeners.delete(listener)
}

/** The CSRF token the backend issues in a readable cookie; echoed in a header on every mutating request. */
export function csrfToken(): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.substring('XSRF-TOKEN='.length)) : null
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResult<T>> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey
  }
  if (method !== 'GET') {
    const token = csrfToken()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause
    }
    // The request may or may not have been processed; the caller decides whether a retry is safe.
    throw new ApiError(0, null, true, 'Network error')
  }

  if (response.ok) {
    const data = response.status === 204 ? (undefined as T) : ((await response.json()) as T)
    return { data, status: response.status, replayed: response.headers.get('Idempotency-Replayed') === 'true' }
  }

  let problem: Problem | null = null
  try {
    const parsed: unknown = await response.json()
    if (parsed && typeof parsed === 'object' && 'code' in parsed) {
      problem = parsed as Problem
    }
  } catch {
    // Not JSON: fall through with no problem document.
  }
  if (response.status === 401 && !options.sessionProbe) {
    unauthenticatedListeners.forEach((listener) => listener())
  }
  throw new ApiError(response.status, problem)
}
