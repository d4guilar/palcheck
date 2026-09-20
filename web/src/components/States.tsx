import type { ReactNode } from 'react'
import { useT } from '../i18n/useT'

/** A loading placeholder announced politely to assistive technology. */
export function LoadingState({ message }: { message: string }) {
  return (
    <div className="state state--loading" role="status" aria-busy="true">
      <div className="loading-skeleton" aria-hidden="true"><span /><span /><span /></div>
      <p style={{ margin: 0 }}>{message}</p>
    </div>
  )
}

export function EmptyState({ title, body, action }: { title: string; body?: string; action?: ReactNode }) {
  return (
    <div className="state">
      <h2>{title}</h2>
      {body ? <p>{body}</p> : null}
      {action}
    </div>
  )
}

/** A failed load with a way to try again. Announced as an alert. */
export function ErrorState({ title, body, onRetry }: { title: string; body?: string; onRetry?: () => void }) {
  const t = useT()
  return (
    <div className="state" role="alert">
      <h2>{title}</h2>
      {body ? <p>{body}</p> : null}
      {onRetry ? (
        <button type="button" className="btn" onClick={onRetry}>
          {t('action.retry')}
        </button>
      ) : null}
    </div>
  )
}
