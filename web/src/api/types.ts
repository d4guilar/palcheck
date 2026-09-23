/** Wire types of the PalCheck API (see docs/PHASE_1_IMPLEMENTATION_PLAN.md section 8). */

export type Permission =
  | 'VIEW_PEOPLE'
  | 'VIEW_PERSON'
  | 'CREATE_PERSON'
  | 'EDIT_PERSON'
  | 'VIEW_EMPLOYMENT'
  | 'EDIT_EMPLOYMENT'
  | 'VIEW_AUDIT_HISTORY'
  | 'VIEW_SYSTEMS'
  | 'VIEW_SYNCS'
  | 'RUN_SYNC'
  | 'VIEW_LINEAGE'

export interface SessionInfo {
  user: { id: string; displayName: string }
  tenant: { id: string; slug: string; name: string }
  permissions: Permission[]
}

/** Phase 1 software/domain states, not legal classifications, eligibility or compliance (Q3 accepted). */
export type EmploymentStatus = 'PENDING' | 'ACTIVE' | 'ENDED'
/** Software/domain classifications; country-specific legal vocabulary stays separate. No statutory calculations. */
export type EmploymentType = 'FULL_TIME' | 'PART_TIME'

export interface Person {
  id: string
  firstName: string
  middleName: string | null
  lastName: string
  preferredName: string | null
  displayName: string
  workEmail: string
  locale: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface DirectoryItem {
  personId: string
  displayName: string
  workEmail: string
  title: string | null
  orgUnitName: string | null
  legalEntityName: string | null
  employmentStatus: EmploymentStatus | null
  employmentId: string | null
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface LegalEntity {
  id: string
  name: string
  country: string
}

export interface OrgUnit {
  id: string
  parentId: string | null
  name: string
  path: string
}

export interface Manager {
  employmentId: string
  personId: string
  displayName: string
}

export interface Assignment {
  ownedBySystem?: string | null
  id: string
  title: string
  orgUnit: { id: string; name: string }
  manager: Manager | null
  effectiveFrom: string
  version: number
  updatedAt: string
}

export interface Employment {
  ownedBySystem?: string | null
  id: string
  personId: string
  legalEntity: LegalEntity
  status: EmploymentStatus
  employmentType: EmploymentType
  country: string
  startDate: string
  endDate: string | null
  version: number
  createdAt: string
  updatedAt: string
  currentAssignment: Assignment | null
}

export interface AuditChange {
  field: string
  before: string | null
  after: string | null
}

export interface AuditEntry {
  id: string
  changeSetId: string
  occurredAt: string
  actor: { type: 'USER' | 'SYSTEM'; id: string | null; displayName: string }
  command: string
  operation: 'CREATE' | 'UPDATE'
  entityType: 'PERSON' | 'EMPLOYMENT' | 'ASSIGNMENT'
  entityId: string
  entityVersion: number
  changes: AuditChange[]
  reason: string | null
  requestId: string | null
}

export interface FieldError {
  field: string
  code: string
  message: string
}

/** RFC 9457 problem details as returned by every API failure. */
export interface Problem {
  type: string
  title: string
  status: number
  detail?: string
  code:
    | 'VALIDATION_ERROR'
    | 'UNAUTHENTICATED'
    | 'FORBIDDEN'
    | 'TENANT_ACCESS_DENIED'
    | 'NOT_FOUND'
    | 'METHOD_NOT_ALLOWED'
    | 'EXTERNALLY_OWNED'
    | 'CONFLICT'
    | 'IDEMPOTENCY_CONFLICT'
    | 'INTERNAL_ERROR'
  requestId?: string
  correlationId?: string
  errors?: FieldError[]
  currentVersion?: number
}

export interface ManagerCandidate { employmentId: string; personId: string; displayName: string; title: string | null; legalEntityName: string; status: EmploymentStatus }
