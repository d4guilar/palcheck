import { Link } from 'react-router-dom'
import type { Page } from '../../api/types'
import { PageTitle } from '../../components/PageTitle'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import type { SyncRun } from './api'

export function AccessDenied() {
  const t = useT()
  return <div className="stack"><PageTitle>{t('systems.title')}</PageTitle><p>{t('integrations.denied')}</p><Link to="/people">{t('nav.people')}</Link></div>
}
export function RunStatus({ status }: { status: SyncRun['status'] }) {
  const t = useT()
  return <span className={`sync-status sync-status--${status.toLowerCase()}`}>{t(`sync.${status}`)}</span>
}
export function RunSummary({ run }: { run: SyncRun }) {
  const t = useT()
  return <div className="stack stack--tight">
    <RunStatus status={run.status} />
    <dl className="sync-summary">{(['recordsSeen', 'createdCount', 'updatedCount', 'unchangedCount', 'failedCount'] as const).map(key => <div key={key}><dt>{t(`sync.${key}`)}</dt><dd>{run[key].toLocaleString()}</dd></div>)}</dl>
    <p className="small secondary">{t('sync.countNote')}</p>
    {run.status === 'FAILED' || run.status === 'RUNNING' ? <p className="small secondary">{t('sync.incomplete')}</p> : null}
  </div>
}
export function RunList({ runs }: { runs: SyncRun[] }) {
  const t = useT(); const format = useFormat()
  return <ul className="integration-list">{runs.map(run => <li key={run.id}>
    <div><Link to={`/syncs/${run.id}`}>{format.dateTime(run.startedAt)}</Link><p className="small secondary">{t('sync.recordsSeen')}: {run.recordsSeen} · {t('sync.failedCount')}: {run.failedCount}</p></div>
    <RunStatus status={run.status} />
  </li>)}</ul>
}
export function Pagination({ page, change, busy }: { page: Page<unknown>; change: (page: number) => void; busy: boolean }) {
  const t = useT()
  if (page.totalPages <= 1) return null
  return <div className="pagination"><p className="small secondary">{t('sync.page', { page: page.page + 1, total: page.totalPages })}</p><div className="cluster">
    <button className="btn" disabled={busy || page.page === 0} onClick={() => change(page.page - 1)}>{t('people.pagination.previous')}</button>
    <button className="btn" disabled={busy || page.page + 1 >= page.totalPages} onClick={() => change(page.page + 1)}>{t('people.pagination.next')}</button>
  </div></div>
}
