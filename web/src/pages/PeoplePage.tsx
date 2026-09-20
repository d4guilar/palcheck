import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/endpoints'
import { EmptyState, ErrorState, LoadingState } from '../components/States'
import { Initials } from '../components/Initials'
import { PageTitle } from '../components/PageTitle'
import { StatusTag } from '../components/StatusTag'
import { useT } from '../i18n/useT'
import { useSession } from '../session/sessionContext'

const PAGE_SIZE = 25

/** The people directory: search, a table of records, and paging. Loading, empty and error states are all designed. */
export function PeoplePage() {
  const t = useT()
  const session = useSession()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0)
  const [draft, setDraft] = useState(q)
  const [observedQuery, setObservedQuery] = useState(q)
  const [edited, setEdited] = useState(false)
  if (observedQuery !== q) {
    setObservedQuery(q)
    setDraft(q)
    setEdited(false)
  }

  // Search as you type, without a request per keystroke.
  useEffect(() => {
    if (!edited || draft === q) return
    const handle = window.setTimeout(() => {
      setParams(draft ? { q: draft } : {}, { replace: true })
    }, 300)
    return () => window.clearTimeout(handle)
  }, [draft, q, setParams, edited])

  const query = useQuery({
    queryKey: ['people', q, page],
    queryFn: ({ signal }) => api.people({ q, page, size: PAGE_SIZE }, signal),
    placeholderData: keepPreviousData,
  })

  const total = query.data?.totalItems ?? 0
  const from = total === 0 ? 0 : page * PAGE_SIZE + 1
  const to = Math.min(total, (page + 1) * PAGE_SIZE)

  const goToPage = (next: number) => {
    const nextParams = new URLSearchParams(params)
    if (next === 0) nextParams.delete('page')
    else nextParams.set('page', String(next))
    setParams(nextParams)
  }

  return (
    <div className="stack directory-page">
      <div className="page-header">
        <div>
          <PageTitle>{t('people.title')}</PageTitle>
          {query.data ? (
            <p className="page-header__sub">{t(q ? 'people.matches' : 'people.count', { count: total, tenant: session.tenant.name })}</p>
          ) : null}
        </div>
        {session.can('CREATE_PERSON') ? (
          <Link className="btn btn--primary" to="/people/new">
            {t('people.new')}
          </Link>
        ) : null}
      </div>

      <form className="directory-toolbar" role="search" onSubmit={(event) => event.preventDefault()}>
        <div className="field directory-search">
          <label className="field__label" htmlFor="people-search">
            {t('people.search.label')}
          </label>
          <input
            id="people-search"
            className="input"
            type="search"
            value={draft}
            placeholder={t('people.search.placeholder')}
            onChange={(event) => { setEdited(true); setDraft(event.target.value) }}
            maxLength={100}
            aria-describedby="search-hint"
            autoComplete="off"
          />
        </div>
        <p id="search-hint" className="small muted">{t('people.search.hint')}</p>
      </form>

      {query.isPending ? <LoadingState message={t('people.loading')} /> : null}
      {query.isError ? (
        <ErrorState title={t('people.error.title')} body={t('people.error.body')} onRetry={() => void query.refetch()} />
      ) : null}

      {query.data && total === 0 ? (
        q ? (
          <EmptyState title={t('people.emptySearch', { query: q })} action={<button type="button" className="btn" onClick={() => { setEdited(true); setDraft('') }}>{t('people.clearSearch')}</button>} />
        ) : (
          <EmptyState
            title={t('people.empty.title')}
            body={t('people.empty.body')}
            action={
              session.can('CREATE_PERSON') ? (
                <Link className="btn btn--primary" to="/people/new">
                  {t('people.new')}
                </Link>
              ) : undefined
            }
          />
        )
      ) : null}

      {query.data && total > 0 ? (
        <>
          <div className="table-wrap">
            <table className="table" aria-busy={query.isPlaceholderData}>
              <caption className="visually-hidden">{t('people.table.caption', { tenant: session.tenant.name })}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('people.col.name')}</th>
                  <th scope="col" className="col-title">{t('people.col.title')}</th>
                  <th scope="col" className="col-unit">{t('people.col.orgUnit')}</th>
                  <th scope="col" className="col-entity">{t('people.col.legalEntity')}</th>
                  <th scope="col">{t('people.col.status')}</th>
                </tr>
              </thead>
              <tbody>
                {query.data.items.map((person) => (
                  <tr key={person.personId}>
                    <td>
                      <div className="person-cell"><Initials name={person.displayName} /><div>
                        <Link to={`/people/${person.personId}`}>{person.displayName}</Link>
                        <div className="small muted">{person.workEmail}</div>
                      </div></div>
                    </td>
                    <td className="col-title">{person.title ?? <span className="muted">—</span>}</td>
                    <td className="col-unit">{person.orgUnitName ?? <span className="muted">—</span>}</td>
                    <td className="col-entity">{person.legalEntityName ?? <span className="muted">—</span>}</td>
                    <td>
                      <StatusTag status={person.employmentStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav className="cluster cluster--between" aria-label={t('people.pagination.label')}>
            <p className="secondary small" style={{ margin: 0 }}>
              {t('people.pagination.range', { from, to, total })}
            </p>
            <div className="cluster">
              <button type="button" className="btn" disabled={page === 0 || query.isPlaceholderData} onClick={() => goToPage(page - 1)}>
                {t('people.pagination.previous')}
              </button>
              <button
                type="button"
                className="btn"
                disabled={page + 1 >= query.data.totalPages || query.isPlaceholderData}
                onClick={() => goToPage(page + 1)}
              >
                {t('people.pagination.next')}
              </button>
            </div>
          </nav>
        </>
      ) : null}
    </div>
  )
}
