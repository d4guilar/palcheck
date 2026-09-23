import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PageTitle } from '../../components/PageTitle'
import { RecordSection } from '../../components/RecordSection'
import { ErrorState, LoadingState } from '../../components/States'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import { useSession } from '../../session/sessionContext'
import { integrations } from './api'
import { AccessDenied, Pagination, RunList } from './SyncViews'

export function SystemPage() {
  const { code = '' } = useParams(); const t = useT(); const format = useFormat(); const session = useSession()
  const navigate = useNavigate(); const client = useQueryClient()
  const [page, setPage] = useState(0); const [scenario, setScenario] = useState('BASELINE')
  const system = useQuery({ queryKey: ['system', code], queryFn: ({ signal }) => integrations.system(code, signal), enabled: session.can('VIEW_SYSTEMS'), retry: false })
  const runs = useQuery({ queryKey: ['syncs', code, page], queryFn: ({ signal }) => integrations.runs(code, page, signal), enabled: session.can('VIEW_SYSTEMS') && session.can('VIEW_SYNCS'), retry: false })
  const sync = useMutation({ mutationFn: () => integrations.start(code, scenario), retry: false, onSuccess: async run => {
    await client.invalidateQueries()
    if (session.can('VIEW_SYNCS')) navigate(`/syncs/${run.id}`, { state: { completed: true } })
  } })
  if (!session.can('VIEW_SYSTEMS')) return <AccessDenied />
  return <div className="stack record-page">
    <Link className="back-link" to="/systems">← {t('systems.back')}</Link>
    {system.data ? null : <PageTitle>{t('systems.title')}</PageTitle>}
    {system.isPending ? <LoadingState message={t('systems.loading')} /> : system.isError ? <ErrorState title={t('systems.error')} onRetry={() => void system.refetch()} /> : <>
      <div><PageTitle>{system.data.displayName}</PageTitle><p className="secondary">{t('systems.mode')}</p></div>
      <p className="small secondary">{t('systems.simulation')}</p>
      <RecordSection id="sync-heading" title={t('sync.section')}>
        <div className="stack stack--tight">
          <dl className="dl"><div><dt>{t('systems.lastSync')}</dt><dd>{system.data.lastSyncAt ? format.dateTime(system.data.lastSyncAt) : t('systems.never')}</dd></div></dl>
          {session.can('RUN_SYNC') ? <form className="stack stack--tight" onSubmit={event => { event.preventDefault(); if (!sync.isPending) sync.mutate() }}>
            {system.data.simulationScenariosEnabled ? <div><label htmlFor="sync-scenario">{t('sync.scenario')}</label><select id="sync-scenario" className="select" aria-describedby="scenario-help" disabled={sync.isPending} value={scenario} onChange={event => setScenario(event.target.value)}><option value="BASELINE">{t('sync.baseline')}</option><option value="TITLE_CHANGE">{t('sync.changed')}</option></select><p id="scenario-help" className="small secondary">{t('sync.scenarioHint')}</p></div> : null}
            <div><button className="btn btn--primary" type="submit" disabled={sync.isPending} aria-busy={sync.isPending}>{t(sync.isPending ? 'sync.running' : 'sync.run')}</button></div>
          </form> : null}
          <div role="status">{sync.isPending ? t('sync.running') : sync.data ? t('sync.completed', { count: sync.data.failedCount }) : ''}</div>
          {sync.isError ? <p role="alert" className="notice notice--warning">{t('sync.error')}</p> : null}
        </div>
      </RecordSection>
      {session.can('VIEW_SYNCS') ? <RecordSection id="runs-heading" title={t('sync.history')}>
        {runs.isPending ? <LoadingState message={t('app.loading')} /> : runs.isError ? <ErrorState title={t('integrations.error')} onRetry={() => void runs.refetch()} /> : <><RunList runs={runs.data.items} />{runs.data.items.length === 0 ? <p className="secondary">{t('sync.empty')}</p> : null}<Pagination page={runs.data} change={setPage} busy={runs.isFetching} /></>}
      </RecordSection> : null}
    </>}
  </div>
}
