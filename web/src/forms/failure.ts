import { ApiError } from '../api/client'
import type { FieldError } from '../api/types'

/** Why a save did not succeed, in the terms the form needs to explain it to the person using it. */
export type Failure =
  | { kind: 'validation'; fieldErrors: FieldError[] }
  | { kind: 'conflict' }
  | { kind: 'externally-owned' }
  | { kind: 'forbidden' }
  | { kind: 'network' }
  | { kind: 'generic'; requestId?: string }

export function classify(error: unknown): Failure {
  if (error instanceof ApiError) {
    if (error.ambiguous) return { kind: 'network' }
    switch (error.code) {
      case 'VALIDATION_ERROR':
        return { kind: 'validation', fieldErrors: error.problem?.errors ?? [] }
      case 'EXTERNALLY_OWNED':
        return { kind: 'externally-owned' }
      case 'CONFLICT':
        return { kind: 'conflict' }
      case 'FORBIDDEN':
      case 'TENANT_ACCESS_DENIED':
        return { kind: 'forbidden' }
      default:
        return { kind: 'generic', requestId: error.problem?.requestId }
    }
  }
  return { kind: 'generic' }
}

/** The DOM id of the control for a server field name (create uses "person.firstName", edit uses "firstName"). */
export function fieldId(prefix: string, serverField: string): string {
  return `${prefix}-${serverField.replace(/\./g, '-')}`
}

/** Field errors keyed by server field name, first message wins. */
export function errorsByField(failure: Failure | null): Record<string, string> {
  const out: Record<string, string> = {}
  if (failure?.kind === 'validation') {
    for (const e of failure.fieldErrors) {
      if (!(e.field in out)) out[e.field] = e.message
    }
  }
  return out
}
