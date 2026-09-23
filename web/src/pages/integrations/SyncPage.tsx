import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { PageTitle } from '../../components/PageTitle'
import { RecordSection } from '../../components/RecordSection'
import { ErrorState, LoadingState } from '../../components/States'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import { useSession } from '../../session/sessionContext'
import { integrations } from './api'
import { AccessDenied, Pagination, RunSummary } from './SyncViews'

export function SyncPage() {
  const { runId = '' } = useParams(); const t = useT(); const format = useFormat(); const session = useSession(); const location = useLocation()
  const [page, setPage] = useState(0)
  const run = useQuery({ queryKey: ['sync', runId], queryFn: ({ signal }) => integrations.run(runId, signal), enabled: session.can('VIEW_SYNCS'), retry: false })
  const items = useQuery({ queryKey: ['sync-items', runId, page], queryFn: ({ signal }) => integrations.items(runId, page, signal), enabled: session.can('VIEW_SYNCS'), retry: false })
  const systems = useQuery({ queryKey: ['systems'], queryFn: ({ signal }) => integrations.systems(signal), enabled: session.can('VIEW_SYSTEMS') && session.can('VIEW_SYNCS') })
  const source = systems.data?.items.find(system => system.id === run.data?.systemId)
  if (!session.can('VIEW_SYNCS')) return <AccessDenied />
  return <div className="stack record-page">
    {session.can('VIEW_SYSTEMS') ? <Link className="back-link" to="/systems">← {t('systems.back')}</Link> : null}
    <PageTitle>{t('sync.title')}</PageTitle>
    {source ? <Link to={`/systems/${source.code}`}>{source.displayName}</Link> : null}
    {run.isPending ? <LoadingState message={t('app.loading')} /> : run.isError ? <ErrorState title={t('integrations.error')} onRetry={() => void run.refetch()} /> : <>
      <p className="secondary">{format.dateTime(run.data.startedAt)}</p>
      {(location.state as { completed?: boolean } | null)?.completed ? <p role="status">{t('sync.completed', { count: run.data.failedCount })}</p> : null}
      <RunSummary run={run.data} />
    </>}
    <RecordSection id="sync-records" title={t('sync.records')}>
      {items.isPending ? <LoadingState message={t('app.loading')} /> : items.isError ? <ErrorState title={t('integrations.error')} onRetry={() => void items.refetch()} /> : <>
        <ul className="integration-list sync-items">{items.data.items.map(item => <li key={item.id}>
          <div className="stack stack--tight"><h3>{item.externalWorkerId}</h3>
            {item.targetPersonId && session.can('VIEW_PERSON') ? <Link to={`/people/${item.targetPersonId}`}>{t('sync.person')}</Link> : null}
            {item.failureReason ? <details><summary>{t('sync.details')}</summary><p>{t(`sync.${item.failureReason}`)}</p><p className="small secondary">{item.failureDetail}</p><p className="small secondary">{t(item.failureReason === 'INVALID_MANAGER_REFERENCE' ? 'sync.managerFailure' : 'sync.failureUnchanged')}</p></details> : null}
          </div>
          <span className={item.status === 'FAILED' ? 'sync-status sync-status--succeeded_with_failures' : 'small secondary'}>{item.action ? t(`sync.${item.action}`) : t('sync.failure')}</span>
        </li>)}</ul>
        <Pagination page={items.data} change={setPage} busy={items.isFetching} />
      </>}
    </RecordSection>
  </div>
}
