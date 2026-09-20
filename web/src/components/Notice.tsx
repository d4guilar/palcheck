import { forwardRef, type ReactNode } from 'react'

type Variant = 'info' | 'success' | 'warning' | 'error'

interface NoticeProps {
  variant: Variant
  title?: string
  children?: ReactNode
  /** "alert" interrupts assistive technology (use for errors); "status" is announced politely. */
  role?: 'alert' | 'status'
  actions?: ReactNode
}

/** An inline message. Color is never the only signal: every notice carries text, and errors are announced. */
export const Notice = forwardRef<HTMLDivElement, NoticeProps>(function Notice(
  { variant, title, children, role, actions },
  ref,
) {
  const resolvedRole = role ?? (variant === 'error' ? 'alert' : 'status')
  return (
    <div ref={ref} tabIndex={-1} className={`notice notice--${variant}`} role={resolvedRole}>
      {title ? <h2>{title}</h2> : null}
      {children}
      {actions ? <div className="cluster" style={{ marginTop: 'var(--space-3)' }}>{actions}</div> : null}
    </div>
  )
})
