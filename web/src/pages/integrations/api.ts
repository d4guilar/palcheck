import { request } from '../../api/client'
import type { Page } from '../../api/types'

export interface ExternalSystem {
  id: string; code: string; displayName: string; type: string; mode: string
  lastSyncAt: string | null; simulationScenariosEnabled: boolean
}
export interface SyncRun {
  id: string; systemId: string; startedAt: string; completedAt: string | null
  status: 'RUNNING' | 'SUCCEEDED' | 'SUCCEEDED_WITH_FAILURES' | 'FAILED'
  recordsSeen: number; createdCount: number; updatedCount: number; unchangedCount: number; failedCount: number
}
export interface SyncItem {
  id: string; externalWorkerId: string; status: 'SUCCEEDED' | 'FAILED'
  action: 'CREATED' | 'UPDATED' | 'UNCHANGED' | 'MANAGER_LINKED' | null
  failureReason: 'UNKNOWN_LEGAL_ENTITY' | 'MISSING_REQUIRED_FIELD' | 'INVALID_MANAGER_REFERENCE' | 'DUPLICATE_EXTERNAL_RECORD' | 'TEMPORARY_PROVIDER_FAILURE' | 'IMPORT_FAILED' | null
  failureDetail: string | null; targetPersonId: string | null; targetEmploymentId: string | null; occurredAt: string
}
export interface Lineage {
  entityType: 'PERSON' | 'EMPLOYMENT' | 'ASSIGNMENT'; entityId: string; fieldPath: string
  externalWorkerId: string; sourceSystem: string; sourceField: string; mappingVersion: string; syncRunId: string; importedAt: string
}
const get = <T,>(url: string, signal?: AbortSignal) => request<T>(url, { signal }).then(r => r.data)
export const integrations = {
  systems: (signal?: AbortSignal) => get<Page<ExternalSystem>>('/api/systems', signal),
  system: (code: string, signal?: AbortSignal) => get<ExternalSystem>(`/api/systems/${encodeURIComponent(code)}`, signal),
  runs: (code: string, page: number, signal?: AbortSignal) => get<Page<SyncRun>>(`/api/systems/${encodeURIComponent(code)}/syncs?page=${page}&size=10`, signal),
  run: (id: string, signal?: AbortSignal) => get<SyncRun>(`/api/syncs/${id}/summary`, signal),
  items: (id: string, page: number, signal?: AbortSignal) => get<Page<SyncItem>>(`/api/syncs/${id}?page=${page}&size=25`, signal),
  start: (code: string, scenario: string) => request<SyncRun>(`/api/systems/${encodeURIComponent(code)}/sync?scenario=${encodeURIComponent(scenario)}`, { method: 'POST', body: {} }).then(r => r.data),
  lineage: (personId: string, employmentId: string, assignmentId: string | undefined, signal?: AbortSignal) => {
    const params = new URLSearchParams({ employmentId })
    if (assignmentId) params.set('assignmentId', assignmentId)
    return get<{ items: Lineage[] }>(`/api/people/${personId}/lineage?${params}`, signal)
  },
}
