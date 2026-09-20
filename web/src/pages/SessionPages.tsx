import { Notice } from '../components/Notice'
import { PageTitle } from '../components/PageTitle'
import { useT } from '../i18n/useT'
import { useSessionState } from '../session/sessionContext'

/** Shown when there is no session. Sign-in is a browser navigation handled by the backend and the identity provider. */
export function SignInPage({ expired, loginFailed }: { expired: boolean; loginFailed: boolean }) {
  const t = useT()
  const { signIn } = useSessionState()
  return (
    <main id="main" className="center-card">
      <div className="sign-in-brand"><span className="brand-mark" aria-hidden="true">P</span><span className="brand">{t('app.name')}</span></div>
      <PageTitle>{t('session.signIn.title')}</PageTitle>
      {expired || loginFailed ? (
        <div style={{ marginBottom: 'var(--space-4)', textAlign: 'start' }}>
          <Notice variant={expired ? 'info' : 'error'} role="status">
            {expired ? t('session.expired') : t('session.loginFailed')}
          </Notice>
        </div>
      ) : null}
      <p>{t('session.signIn.body')}</p>
      <button type="button" className="btn btn--primary" onClick={signIn}>
        {t('session.signIn.button')}
      </button>
      <p className="session-note">{t('session.note')}</p>
    </main>
  )
}

/** A valid identity with no PalCheck tenant: explained clearly, with a way out. */
export function NoTenantPage() {
  const t = useT()
  const { signOut } = useSessionState()
  return (
    <main id="main" className="center-card">
      <PageTitle>{t('session.noTenant.title')}</PageTitle>
      <p>{t('session.noTenant.body')}</p>
      <button type="button" className="btn" onClick={() => void signOut()}>
        {t('session.signOut')}
      </button>
    </main>
  )
}

export function SessionErrorPage() {
  const t = useT()
  const { retry } = useSessionState()
  return (
    <main id="main" className="center-card">
      <PageTitle>{t('session.error.title')}</PageTitle>
      <p>{t('session.error.body')}</p>
      <button type="button" className="btn" onClick={retry}>
        {t('action.retry')}
      </button>
    </main>
  )
}

export function BootLoading() {
  const t = useT()
  return (
    <div className="center-card" role="status" aria-busy="true">
      <p>{t('app.loading')}</p>
    </div>
  )
}
