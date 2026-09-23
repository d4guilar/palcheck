import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Employment } from '../../api/types'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import { useSession } from '../../session/sessionContext'
import { integrations } from './api'

/** Metadata is fetched only when an authorized reader opens the native disclosure. */
export function SourceDetails({ employment, assignment = false }: { employment: Employment; assignment?: boolean }) {
  const t = useT(); const format = useFormat(); const session = useSession(); const [open, setOpen] = useState(false)
  const owner = assignment ? employment.currentAssignment?.ownedBySystem : employment.ownedBySystem
  const name = owner === 'workday-simulation' ? t('simulation.name') : t('source.external')
  const query = useQuery({ queryKey: ['lineage', employment.personId, employment.id, employment.currentAssignment?.id], queryFn: ({ signal }) => integrations.lineage(employment.personId, employment.id, employment.currentAssignment?.id, signal), enabled: Boolean(owner) && open && session.can('VIEW_LINEAGE'), retry: false })
  if (!owner) return null
  const fields = query.data?.items.filter(row => row.entityType === (assignment ? 'ASSIGNMENT' : 'EMPLOYMENT') && row.entityId === (assignment ? employment.currentAssignment?.id : employment.id))
  return <div className="source-details">
    <p className="small secondary">{t('source.managed', { system: name })}</p>
    {session.can('VIEW_LINEAGE') ? <details onToggle={event => setOpen(event.currentTarget.open)}>
      <summary>{t('source.view')}</summary>
      <p className="small secondary">{t('source.readonly')}</p>
      {query.isPending ? <p role="status">{t('source.loading')}</p> : query.isError ? <div role="alert"><p>{t('integrations.error')}</p><button className="btn" onClick={() => void query.refetch()}>{t('action.retry')}</button></div> : fields?.length === 0 ? <p>{t('source.empty')}</p> :
        <ul className="source-fields">{fields?.map(field => <li key={field.fieldPath}>
          <dl className="dl"><div><dt>{t('source.system')}</dt><dd>{name}</dd></div><div><dt>{t('sync.worker')}</dt><dd>{field.externalWorkerId}</dd></div><div><dt>{t('source.field')}</dt><dd>{field.sourceField}</dd></div><div><dt>{t('source.destination')}</dt><dd>{field.entityType.toLowerCase()}.{field.fieldPath}</dd></div><div><dt>{t('source.observed')}</dt><dd>{format.dateTime(field.importedAt)}</dd></div><div><dt>{t('source.mapping')}</dt><dd>{field.mappingVersion}</dd></div><div><dt>{t('source.ownership')}</dt><dd>{t('source.externalOwnership')}</dd></div></dl>
          {session.can('VIEW_SYNCS') ? <Link to={`/syncs/${field.syncRunId}`}>{t('sync.open')}</Link> : null}
        </li>)}</ul>}
    </details> : null}
  </div>
}
