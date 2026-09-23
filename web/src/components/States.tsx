import type { ReactNode } from 'react'
import { useT } from '../i18n/useT'

/** A loading placeholder announced politely to assistive technology. */
export function LoadingState({ message, rows = false }: { message: string; rows?: boolean }) {
  return (
    <div className={rows ? 'state state--loading state--rows' : 'state state--loading'} role="status" aria-busy="true">
      <div className="loading-skeleton" aria-hidden="true"><span /><span /><span />{rows ? <><span /><span /></> : null}</div>
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
export function ErrorState({ title, label, body, onRetry }: { title?: string; label?: string; body?: string; onRetry?: () => void }) {
  const t = useT()
  return (
    <div className="state" role="alert" aria-label={label}>
      {title ? <h2>{title}</h2> : null}
      {body ? <p>{body}</p> : null}
      {onRetry ? (
        <button type="button" className="btn" onClick={onRetry}>
          {t('action.retry')}
        </button>
      ) : null}
    </div>
  )
}
