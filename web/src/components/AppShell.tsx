import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation, useNavigationType } from 'react-router-dom'
import { useT } from '../i18n/useT'
import { useSession, useSessionState } from '../session/sessionContext'

/**
 * The signed-in frame: skip link, a banner with navigation and the current user, and the main landmark.
 * When the person navigates to another page, focus moves to its heading so keyboard and screen-reader users know where
 * they are. It does not move on the first load or on a redirect, so the skip link stays the first Tab stop.
 */
export function AppShell() {
  const t = useT()
  const session = useSession()
  const { signOut } = useSessionState()
  const { pathname } = useLocation()
  const navigationType = useNavigationType()
  const shownPath = useRef(pathname)

  useEffect(() => {
    // Only a change of page matters: the first load, and paging or searching within a page, must not pull focus.
    if (shownPath.current === pathname) return
    shownPath.current = pathname
    if (navigationType === 'REPLACE') return
    // A page often renders a loading state first, so the heading may not exist yet: focus the first one that appears,
    // unless the person has already moved focus into the page themselves.
    const main = document.getElementById('main')
    if (!main) return
    const focusHeading = () => {
      const active = document.activeElement
      if (active && active !== document.body && main.contains(active)) return true
      const heading = main.querySelector<HTMLElement>('h1')
      heading?.focus()
      return heading !== null
    }
    if (focusHeading()) return
    const observer = new MutationObserver(() => {
      if (focusHeading()) observer.disconnect()
    })
    observer.observe(main, { childList: true, subtree: true })
    return () => observer.disconnect()
  }, [pathname, navigationType])

  return (
    <>
      <a className="skip-link" href="#main">
        {t('app.skipToContent')}
      </a>
      <header className="app-rail">
        <div className="rail-brand"><span className="brand-mark" aria-hidden="true">P</span><span className="brand">{t('app.name')}</span></div>
        <div className="rail-workspace"><span className="eyebrow">{t('nav.workspace')}</span><strong>{session.tenant.name}</strong></div>
        <nav className="app-nav" aria-label={t('nav.main')}>
          <NavLink to="/people">{t('nav.people')}</NavLink>
          {session.can('VIEW_SYSTEMS') ? <NavLink to="/systems">{t('systems.title')}</NavLink> : null}
        </nav>
        <div className="rail-account">
          <span className="eyebrow">{t('nav.account')}</span>
          <span className="account-name">{session.user.displayName}</span>
          <span className="small muted">{t('app.synthetic')}</span>
          <button type="button" className="btn btn--quiet" onClick={() => void signOut()}>{t('session.signOut')}</button>
        </div>
      </header>
      <main id="main" className="container app-content">
        <Outlet />
      </main>
    </>
  )
}
