import { useEffect, type ReactNode } from 'react'

/** The page heading. Focusable (not tabbable) so navigation can move focus to it, and mirrored into the document title. */
export function PageTitle({ children, documentTitle }: { children: ReactNode; documentTitle?: string }) {
  const text = documentTitle ?? (typeof children === 'string' ? children : undefined)
  useEffect(() => {
    document.title = text ? `${text} · PalCheck` : 'PalCheck'
  }, [text])
  return <h1 tabIndex={-1}>{children}</h1>
}
