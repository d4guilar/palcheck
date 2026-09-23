import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PageTitle } from '../../components/PageTitle'
import { ErrorState, LoadingState, EmptyState } from '../../components/States'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import { useSession } from '../../session/sessionContext'
import { integrations } from './api'
import { AccessDenied } from './SyncViews'

export function SystemsPage() {
  const t = useT(); const format = useFormat(); const session = useSession()
  const query = useQuery({ queryKey: ['systems'], queryFn: ({ signal }) => integrations.systems(signal), enabled: session.can('VIEW_SYSTEMS'), retry: false })
  if (!session.can('VIEW_SYSTEMS')) return <AccessDenied />
  return <div className="stack record-page">
    <PageTitle>{t('systems.title')}</PageTitle>
    {query.isPending ? <LoadingState message={t('systems.loading')} rows /> : query.isError ? <ErrorState title={t('systems.error')} onRetry={() => void query.refetch()} /> : query.data.items.length === 0 ? <EmptyState title={t('systems.empty')} /> :
      <ul className="integration-list">{query.data.items.map(system => <li key={system.id}>
        <div><h2><Link to={`/systems/${system.code}`}>{system.displayName}</Link></h2><p className="small secondary">{t('systems.mode')}</p></div>
        <div><span className="small secondary">{t('systems.lastSync')}</span><p>{system.lastSyncAt ? format.dateTime(system.lastSyncAt) : t('systems.never')}</p></div>
      </li>)}</ul>}
    <p className="small secondary">{t('systems.simulation')}</p>
  </div>
}
