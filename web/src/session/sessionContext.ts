import { createContext, useContext } from 'react'
import type { Permission, SessionInfo } from '../api/types'

export type SessionState =
  | { status: 'loading' }
  | { status: 'ready'; session: SessionInfo }
  | { status: 'unauthenticated'; expired: boolean; loginFailed: boolean }
  | { status: 'no-tenant' }
  | { status: 'error' }

export interface SessionContextValue {
  state: SessionState
  retry: () => void
  signIn: () => void
  signOut: () => Promise<void>
}

export const SessionContext = createContext<SessionContextValue | null>(null)

export function useSessionState(): SessionContextValue {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useSessionState must be used inside SessionProvider')
  return value
}

/** The signed-in session. Only call below the session gate, where a session is guaranteed. */
export function useSession(): SessionInfo & { can: (permission: Permission) => boolean } {
  const { state } = useSessionState()
  if (state.status !== 'ready') throw new Error('useSession requires a signed-in session')
  const { session } = state
  return { ...session, can: (permission) => session.permissions.includes(permission) }
}
