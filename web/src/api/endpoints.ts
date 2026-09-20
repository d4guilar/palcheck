import { request } from './client'
import type {
  AuditEntry,
  ManagerCandidate,
  DirectoryItem,
  Employment,
  LegalEntity,
  OrgUnit,
  Page,
  Person,
  SessionInfo,
} from './types'

/** Typed calls to the PalCheck API. Nothing here builds a tenant id: the server derives it from the session. */
export const api = {
  session: (signal?: AbortSignal) => request<SessionInfo>('/api/session', { signal, sessionProbe: true }).then((r) => r.data),

  people: (params: { q?: string; page?: number; size?: number }, signal?: AbortSignal) => {
    const query = new URLSearchParams()
    if (params.q) query.set('q', params.q)
    query.set('page', String(params.page ?? 0))
    query.set('size', String(params.size ?? 25))
    return request<Page<DirectoryItem>>(`/api/people?${query}`, { signal }).then((r) => r.data)
  },

  managers: (params: { q: string; page: number; excludingEmploymentId?: string }, signal?: AbortSignal) => {
    const query = new URLSearchParams({ q: params.q, page: String(params.page), size: '20' })
    if (params.excludingEmploymentId) query.set('excludingEmploymentId', params.excludingEmploymentId)
    return request<Page<ManagerCandidate>>(`/api/manager-candidates?${query}`, { signal }).then((r) => r.data)
  },

  person: (id: string, signal?: AbortSignal) =>
    request<Person>(`/api/people/${id}`, { signal }).then((r) => r.data),

  employments: (personId: string, signal?: AbortSignal) =>
    request<{ items: Employment[] }>(`/api/people/${personId}/employments`, { signal }).then((r) => r.data.items),

  history: (personId: string, page: number, signal?: AbortSignal) =>
    request<Page<AuditEntry>>(`/api/people/${personId}/audit?page=${page}&size=25`, { signal }).then((r) => r.data),

  legalEntities: (signal?: AbortSignal) =>
    request<{ items: LegalEntity[] }>('/api/legal-entities', { signal }).then((r) => r.data.items),

  orgUnits: (signal?: AbortSignal) =>
    request<{ items: OrgUnit[] }>('/api/org-units', { signal }).then((r) => r.data.items),

  createPerson: (body: unknown, idempotencyKey: string) =>
    request<Person>('/api/people', { method: 'POST', body, idempotencyKey }).then((r) => r.data),

  updatePerson: (id: string, body: unknown, idempotencyKey: string) =>
    request<Person>(`/api/people/${id}`, { method: 'PATCH', body, idempotencyKey }).then((r) => r.data),

  updateEmployment: (id: string, body: unknown, idempotencyKey: string) =>
    request<Employment>(`/api/employments/${id}`, { method: 'PATCH', body, idempotencyKey }).then((r) => r.data),

  updateAssignment: (id: string, body: unknown, idempotencyKey: string) =>
    request<Employment>(`/api/assignments/${id}`, { method: 'PATCH', body, idempotencyKey }).then((r) => r.data),


}

/** Where the browser goes to start the OpenID Connect login (a navigation, not a fetch: the identity provider redirects). */
export const SIGN_IN_URL = '/oauth2/authorization/oidc'
