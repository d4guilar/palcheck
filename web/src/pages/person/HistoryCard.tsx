import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '../../api/endpoints'
import type { AuditChange, AuditEntry } from '../../api/types'
import type { MessageId } from '../../i18n/en'
import { useT, type Translate } from '../../i18n/useT'
import { useFormat } from '../../lib/format'
import { RecordSection } from '../../components/RecordSection'

const FIELD_LABELS: Record<string, MessageId> = {
  firstName: 'field.firstName',
  middleName: 'field.middleName',
  lastName: 'field.lastName',
  preferredName: 'field.preferredName',
  workEmail: 'field.workEmail',
  locale: 'field.locale',
  status: 'field.status',
  employmentType: 'field.employmentType',
  country: 'field.country',
  startDate: 'field.startDate',
  endDate: 'field.endDate',
  title: 'field.title',
  orgUnit: 'field.orgUnit',
  manager: 'field.manager',
}

const VALUE_LABELS: Record<string, Record<string, MessageId>> = {
  status: { PENDING: 'status.PENDING', ACTIVE: 'status.ACTIVE', ENDED: 'status.ENDED' },
  employmentType: { FULL_TIME: 'employmentType.FULL_TIME', PART_TIME: 'employmentType.PART_TIME' },
  locale: { en: 'locale.en', es: 'locale.es', 'es-CR': 'locale.es-CR' },
}

interface ChangeSet {
  id: string
  entries: AuditEntry[]
}

/** Groups consecutive entries written by one command (a create writes person, employment and assignment together). */
function groupByChangeSet(entries: AuditEntry[]): ChangeSet[] {
  const groups: ChangeSet[] = []
  for (const entry of entries) {
    const last = groups[groups.length - 1]
    if (last && last.id === entry.changeSetId) last.entries.push(entry)
    else groups.push({ id: entry.changeSetId, entries: [entry] })
  }
  return groups
}

/** The change history of a record: who changed what, when, from what to what, and why. */
export function HistoryCard({ personId }: { personId: string }) {
  const t = useT()
  const format = useFormat()
  const query = useInfiniteQuery({
    queryKey: ['history', personId],
    queryFn: ({ pageParam, signal }) => api.history(personId, pageParam, signal),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })

  const groups = groupByChangeSet(query.data?.pages.flatMap((p) => p.items) ?? [])

  return (
    <RecordSection id="history-heading" title={t('person.section.history')}>

      {query.isPending ? (
        <p role="status" className="muted">
          {t('history.loading')}
        </p>
      ) : null}
      {query.isError ? (
        <p role="alert" className="notice notice--error">
          {t('history.error')}
        </p>
      ) : null}
      {query.data && groups.length === 0 ? <p className="muted">{t('history.empty')}</p> : null}

      {groups.length > 0 ? (
        <ol className="history">
          {groups.map((group) => {
            const first = group.entries[0] as AuditEntry
            const created = first.operation === 'CREATE'
            const title = first.actor.type === 'SYSTEM' ? t(created ? 'history.imported' : 'history.synchronized') : created ? t('history.created') : t(`history.updated.${first.entityType}`)
            const changes = group.entries.flatMap((e) => e.changes)
            return (
              <li key={group.id} className="history__item">
                <time className="history__time" dateTime={first.occurredAt}>{format.dateTime(first.occurredAt)}</time>
                <h3 className="history__title">{title}</h3>
                <p className="history__meta">
                  {first.actor.type === 'SYSTEM' ? t('history.source', { source: first.actor.displayName }) : t('history.actor', { actor: first.actor.displayName })}
                </p>
                {first.reason ? <p className="small secondary">{t('history.reason', { reason: first.reason })}</p> : null}
                {created ? (
                  <details style={{ marginTop: 'var(--space-2)' }}>
                    <summary className="small">{t('person.section.identity')}, {t('person.section.employment').toLowerCase()}, {t('person.section.assignment').toLowerCase()}</summary>
                    <ChangeList changes={changes} />
                  </details>
                ) : (
                  <ChangeList changes={changes} />
                )}
              </li>
            )
          })}
        </ol>
      ) : null}

      {query.hasNextPage ? (
        <div style={{ marginTop: 'var(--space-4)' }}>
          <button type="button" className="btn" onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
            {t('history.more')}
          </button>
        </div>
      ) : null}
    </RecordSection>
  )
}

function ChangeList({ changes }: { changes: AuditChange[] }) {
  const t = useT()
  const format = useFormat()
  return (
    <ul className="history__changes">
      {changes.map((change, index) => {
        const label = fieldLabel(change.field, t)
        const before = displayValue(change.field, change.before, t, format.date)
        const after = displayValue(change.field, change.after, t, format.date)
        return (
          <li key={`${change.field}-${index}`}>
            {/* Screen readers get a sentence; sighted users get the compact arrow form. */}
            <span className="visually-hidden">{t('history.changeFrom', { field: label, before, after })}</span>
            <span className="history__change" aria-hidden="true">
              <span className="history__field">{label}</span>
              <span className="history__values"><span className="history__before">{before}</span><span className="history__arrow">→</span><span>{after}</span></span>
            </span>
          </li>
        )
      })}
    </ul>
  )
}

function fieldLabel(field: string, t: Translate): string {
  const id = FIELD_LABELS[field]
  return id ? t(id) : field
}

function displayValue(field: string, value: string | null, t: Translate, date: (iso: string) => string): string {
  if (value === null) return t('history.none')
  if (field === 'startDate' || field === 'endDate') return date(value)
  const id = VALUE_LABELS[field]?.[value]
  return id ? t(id) : value
}
