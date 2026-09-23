import { SystemsPage } from './pages/integrations/SystemsPage'
import { SystemPage } from './pages/integrations/SystemPage'
import { SyncPage } from './pages/integrations/SyncPage'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { PageTitle } from './components/PageTitle'
import { NewPersonPage } from './pages/NewPersonPage'
import { PeoplePage } from './pages/PeoplePage'
import { PersonDetailPage } from './pages/person/PersonDetailPage'
import { BootLoading, NoTenantPage, SessionErrorPage, SignInPage } from './pages/SessionPages'
import { useT } from './i18n/useT'
import { useSessionState } from './session/sessionContext'
import { Link } from 'react-router-dom'

/** Chooses what to show from the session: sign in, no workspace, an error, or the application itself. */
export default function App() {
  const { state } = useSessionState()
  switch (state.status) {
    case 'loading':
      return <BootLoading />
    case 'unauthenticated':
      return <SignInPage expired={state.expired} loginFailed={state.loginFailed} />
    case 'no-tenant':
      return <NoTenantPage />
    case 'error':
      return <SessionErrorPage />
    case 'ready':
      return <SignedInRoutes />
  }
}

function SignedInRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/people" replace />} />
        <Route path="systems" element={<SystemsPage />} />
        <Route path="systems/:code" element={<SystemPage />} />
        <Route path="syncs/:runId" element={<SyncPage />} />
        <Route path="people" element={<PeoplePage />} />
        <Route path="people/new" element={<NewPersonPage />} />
        <Route path="people/:personId" element={<PersonDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

function NotFoundPage() {
  const t = useT()
  return (
    <div className="stack stack--tight">
      <PageTitle>{t('person.notFound.title')}</PageTitle>
      <p className="secondary">{t('person.notFound.body')}</p>
      <Link to="/people">{t('person.back')}</Link>
    </div>
  )
}
