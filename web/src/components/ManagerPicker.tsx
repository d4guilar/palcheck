import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../api/endpoints'
import { useT } from '../i18n/useT'
import type { ControlProps } from './FormField'
import { SearchField } from './SearchField'

/** Paged employment search with native controls: no load-all list or custom keyboard model. */
export function ManagerPicker({ control, value, onChange, selectedName, excludingEmploymentId }: {
  control: ControlProps; value: string; onChange: (value: string) => void
  selectedName?: string; excludingEmploymentId?: string
}) {
  const t = useT()
  const [draft, setDraft] = useState('')
  const [search, setSearch] = useState({ q: '', page: 0 })
  const [chosen, setChosen] = useState({ id: value, label: selectedName ?? t('manager.current') })
  useEffect(() => {
    const timer = window.setTimeout(() => setSearch((s) => s.q === draft ? s : { q: draft, page: 0 }), 300)
    return () => window.clearTimeout(timer)
  }, [draft])
  const query = useQuery({
    queryKey: ['manager-candidates', search.q, search.page, excludingEmploymentId],
    queryFn: ({ signal }) => api.managers({ ...search, excludingEmploymentId }, signal),
  })
  const items = query.data?.items ?? []
  const waiting = query.isFetching || draft !== search.q
  return (
    <div className="manager-picker stack stack--tight">
      <select {...control} className="select" value={value} onChange={(event) => {
        const next = event.target.value
        const candidate = items.find((item) => item.employmentId === next)
        setChosen({ id: next, label: candidate?.displayName ?? '' })
        onChange(next)
      }}>
        <option value="">{t('field.manager.none')}</option>
        {value && !items.some((item) => item.employmentId === value)
          ? <option value={value}>{chosen.id === value ? chosen.label : selectedName ?? t('manager.current')}</option> : null}
        {items.map((item) => <option key={item.employmentId} value={item.employmentId}>
          {t('manager.option', { name: item.displayName, title: item.title ?? t('person.assignment.none'), entity: item.legalEntityName, status: t(`status.${item.status}`) })}
        </option>)}
      </select>
      <SearchField id={`${control.id}-search`} label={t('manager.search')} placeholder={t('people.search.placeholder')} value={draft} maxLength={100}
        autoComplete="off" onChange={(event) => setDraft(event.target.value)} />
      <div className="cluster cluster--between">
        <span className="small muted" role="status">{query.isError ? t('manager.error') : waiting ? t('app.loading') : t('manager.results', { count: query.data?.totalItems ?? 0 })}</span>
        {query.isError ? <button type="button" className="btn btn--quiet" onClick={() => void query.refetch()}>{t('action.retry')}</button> : null}
        {(query.data?.totalPages ?? 0) > 1 || search.page > 0 ? <div className="cluster">
          <button type="button" className="btn" disabled={waiting || search.page === 0}
            onClick={() => setSearch((s) => ({ ...s, page: s.page - 1 }))}>{t('people.pagination.previous')}</button>
          <button type="button" className="btn" disabled={waiting || !query.data || search.page + 1 >= query.data.totalPages}
            onClick={() => setSearch((s) => ({ ...s, page: s.page + 1 }))}>{t('people.pagination.next')}</button>
        </div> : null}
      </div>
    </div>
  )
}
