import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, onUnauthenticated } from '../api/client'
import { api, SIGN_IN_URL } from '../api/endpoints'
import { submitLogout } from './logout'
import { navigateTo } from '../lib/navigate'
import { SessionContext, type SessionState } from './sessionContext'

/**
 * Bootstraps the app from GET /api/session. 401 means "sign in" (the browser is sent to the identity provider through
 * the backend; no token ever reaches JavaScript), 403 TENANT_ACCESS_DENIED means the identity has no workspace, and a
 * 401 on any later call means the session ended.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [expired, setExpired] = useState(false)
  const loginFailed = useMemo(() => new URLSearchParams(window.location.search).get('login') === 'failed', [])

  const query = useQuery({
    queryKey: ['session'],
    queryFn: ({ signal }) => api.session(signal),
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  useEffect(
    () =>
      onUnauthenticated(() => {
        setExpired(true)
        queryClient.removeQueries({ predicate: (q) => q.queryKey[0] !== 'session' })
        void queryClient.invalidateQueries({ queryKey: ['session'] })
      }),
    [queryClient],
  )

  const state: SessionState = useMemo(() => {
    if (query.isPending) return { status: 'loading' }
    const error = query.error
    // Checked before the cached session: after a 401 on a refetch the old data is still there, but it is no longer valid.
    if (error instanceof ApiError && error.status === 401) {
      return { status: 'unauthenticated', expired, loginFailed }
    }
    if (query.data) return { status: 'ready', session: query.data }
    if (error instanceof ApiError && error.code === 'TENANT_ACCESS_DENIED') return { status: 'no-tenant' }
    return { status: 'error' }
  }, [query.isPending, query.data, query.error, expired, loginFailed])

  const retry = useCallback(() => void query.refetch(), [query])
  const signIn = useCallback(() => navigateTo(SIGN_IN_URL), [])
  const signOut = useCallback(async () => submitLogout(), [])

  const value = useMemo(() => ({ state, retry, signIn, signOut }), [state, retry, signIn, signOut])
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}
