import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import { Initials } from '../../components/Initials'
import { Notice } from '../../components/Notice'
import { PageTitle } from '../../components/PageTitle'
import { ErrorState, LoadingState } from '../../components/States'
import { RecordSection } from '../../components/RecordSection'
import { useT } from '../../i18n/useT'
import { useSession } from '../../session/sessionContext'
import { EmploymentCard } from './EmploymentCard'
import { HistoryCard } from './HistoryCard'
import { IdentityCard } from './IdentityCard'

/** A cohesive workforce record: identity, employment, assignment and change history. */
export function PersonDetailPage() {
  const t = useT()
  const session = useSession()
  const queryClient = useQueryClient()
  const { personId = '' } = useParams()
  const location = useLocation()
  const [message, setMessage] = useState<string | null>(
    (location.state as { created?: boolean } | null)?.created ? t('notice.created') : null,
  )

  const person = useQuery({ queryKey: ['person', personId], queryFn: ({ signal }) => api.person(personId, signal), retry: false })
  const employments = useQuery({
    queryKey: ['employments', personId],
    queryFn: ({ signal }) => api.employments(personId, signal),
    enabled: session.can('VIEW_EMPLOYMENT'),
  })

  const saved = () => {
    setMessage(t('notice.saved'))
    void queryClient.invalidateQueries({ queryKey: ['person', personId] })
    void queryClient.invalidateQueries({ queryKey: ['employments', personId] })
    void queryClient.invalidateQueries({ queryKey: ['history', personId] })
    void queryClient.invalidateQueries({ queryKey: ['people'] })
  }

  const back = (
    <Link className="back-link" to="/people">
      ← {t('person.back')}
    </Link>
  )

  if (person.isPending) {
    return (
      <div>
        {back}
        <LoadingState message={t('app.loading')} />
      </div>
    )
  }
  if (person.isError) {
    const notFound = person.error instanceof ApiError && person.error.status === 404
    return (
      <div>
        {back}
        <PageTitle>{notFound ? t('person.notFound.title') : t('person.error.title')}</PageTitle>
        {notFound ? (
          <p className="secondary" style={{ marginTop: 'var(--space-3)' }}>
            {t('person.notFound.body')}
          </p>
        ) : (
          <ErrorState label={t('person.error.title')} body={t('person.error.body')} onRetry={() => void person.refetch()} />
        )}
      </div>
    )
  }

  const record = person.data
  const primary = employments.data?.[0]
  const assignment = primary?.currentAssignment

  return (
    <div className="stack record-page">
      <div>
        {back}
        <div className="page-header" style={{ marginBottom: 0 }}>
          <div className="profile-summary"><Initials name={record.displayName} profile /><div>
            <PageTitle documentTitle={record.displayName}>{record.displayName}</PageTitle>
            {assignment ? (
              <p className="page-header__sub">
                {assignment.title} · {assignment.orgUnit.name}
              </p>
            ) : null}
          </div></div>
        </div>
        <nav className="record-nav" aria-label={t('person.navigation')}>
          <a href="#identity-heading">{t('person.section.identity')}</a>
          {session.can('VIEW_EMPLOYMENT') ? <a href="#employment-heading">{t('person.section.employment')}</a> : null}
          {session.can('VIEW_AUDIT_HISTORY') ? <a href="#history-heading">{t('person.section.history')}</a> : null}
        </nav>
      </div>

      {message ? (
        <Notice variant="success" role="status">
          {message}
        </Notice>
      ) : null}

      <IdentityCard person={record} canEdit={session.can('EDIT_PERSON')} onSaved={saved} />

      {session.can('VIEW_EMPLOYMENT') ? (
        <RecordSection id="employment-heading" title={t('person.section.employment')}>
          {employments.isPending ? <LoadingState message={t('app.loading')} /> : null}
          {employments.isError ? <ErrorState title={t('person.error.title')} onRetry={() => void employments.refetch()} /> : null}
          {employments.data && employments.data.length === 0 ? <p className="muted">{t('person.employments.none')}</p> : null}
          {employments.data?.map((employment) => (
            <EmploymentCard
              key={employment.id}
              employment={employment}
              canEdit={session.can('EDIT_EMPLOYMENT')}
              onSaved={saved}
            />
          ))}
        </RecordSection>
      ) : null}

      {session.can('VIEW_AUDIT_HISTORY') ? <HistoryCard personId={personId} /> : null}
    </div>
  )
}
