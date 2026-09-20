import type { AuditEntry, DirectoryItem, Employment, LegalEntity, OrgUnit, Permission, Person, SessionInfo } from '../api/types'

/** Fictional people and organizations only. Nothing here resembles a real person, employer or identifier. */

export const ALL_PERMISSIONS: Permission[] = [
  'VIEW_PEOPLE',
  'VIEW_PERSON',
  'CREATE_PERSON',
  'EDIT_PERSON',
  'VIEW_EMPLOYMENT',
  'EDIT_EMPLOYMENT',
  'VIEW_AUDIT_HISTORY',
]

export const VIEWER_PERMISSIONS: Permission[] = ['VIEW_PEOPLE', 'VIEW_PERSON', 'VIEW_EMPLOYMENT', 'VIEW_AUDIT_HISTORY']

export const session = (permissions: Permission[] = ALL_PERMISSIONS): SessionInfo => ({
  user: { id: 'user-1', displayName: 'Ana Quesada' },
  tenant: { id: 'tenant-1', slug: 'demo-cr', name: 'Demo Tenant CR' },
  permissions,
})

export const legalEntity: LegalEntity = { id: 'le-1', name: 'Demo Servicios S.A. (fictional)', country: 'CR' }

export const orgUnits: OrgUnit[] = [
  { id: 'ou-1', parentId: null, name: 'Operations', path: 'Operations' },
  { id: 'ou-2', parentId: 'ou-1', name: 'Support', path: 'Operations / Support' },
]

export const person = (overrides: Partial<Person> = {}): Person => ({
  id: 'p-1',
  firstName: 'Mariana',
  middleName: null,
  lastName: 'Vargas Mora',
  preferredName: null,
  displayName: 'Mariana Vargas Mora',
  workEmail: 'mariana.vargas@example.test',
  locale: 'es-CR',
  version: 3,
  createdAt: '2026-05-01T09:00:00Z',
  updatedAt: '2026-06-01T09:00:00Z',
  ...overrides,
})

export const employment = (overrides: Partial<Employment> = {}): Employment => ({
  id: 'e-1',
  personId: 'p-1',
  legalEntity,
  status: 'ACTIVE',
  employmentType: 'FULL_TIME',
  country: 'CR',
  startDate: '2026-02-02',
  endDate: null,
  version: 2,
  createdAt: '2026-05-01T09:00:00Z',
  updatedAt: '2026-06-01T09:00:00Z',
  currentAssignment: {
    id: 'a-1',
    title: 'Support Analyst',
    orgUnit: { id: 'ou-2', name: 'Support' },
    manager: null,
    effectiveFrom: '2026-02-02',
    version: 1,
    updatedAt: '2026-05-01T09:00:00Z',
  },
  ...overrides,
})

export const directoryItem = (overrides: Partial<DirectoryItem> = {}): DirectoryItem => ({
  personId: 'p-1',
  displayName: 'Mariana Vargas Mora',
  workEmail: 'mariana.vargas@example.test',
  title: 'Support Analyst',
  orgUnitName: 'Support',
  legalEntityName: legalEntity.name,
  employmentStatus: 'ACTIVE',
  employmentId: 'e-1',
  ...overrides,
})

export const page = <T,>(
  items: T[],
  overrides: Partial<{ page: number; size: number; totalItems: number; totalPages: number }> = {},
) => ({
  items,
  page: 0,
  size: 25,
  totalItems: items.length,
  totalPages: items.length === 0 ? 0 : 1,
  ...overrides,
})

export const auditEntry = (overrides: Partial<AuditEntry> = {}): AuditEntry => ({
  id: 'audit-1',
  changeSetId: 'cs-1',
  occurredAt: '2026-06-01T09:00:00Z',
  actor: { type: 'USER', id: 'user-1', displayName: 'Ana Quesada' },
  command: 'EditPerson',
  operation: 'UPDATE',
  entityType: 'PERSON',
  entityId: 'p-1',
  entityVersion: 3,
  changes: [{ field: 'workEmail', before: 'old@example.test', after: 'mariana.vargas@example.test' }],
  reason: 'Corrected a typo',
  requestId: 'req-1',
  ...overrides,
})
