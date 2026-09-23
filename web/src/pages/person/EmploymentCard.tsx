import { SourceDetails } from '../integrations/SourceDetails'
import { ManagerPicker } from '../../components/ManagerPicker'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../api/endpoints'
import type { Employment } from '../../api/types'
import { FormField } from '../../components/FormField'
import { StatusTag } from '../../components/StatusTag'
import { FormFeedback } from '../../forms/FormFeedback'
import { fieldId } from '../../forms/failure'
import { useSaveForm } from '../../forms/useSaveForm'
import { useEditorFocus } from '../../forms/useEditorFocus'
import { useT } from '../../i18n/useT'
import { useFormat } from '../../lib/format'

interface Props {
  employment: Employment
  canEdit: boolean
  onSaved: () => void
}

/** One employment with its current assignment. Each part is edited on its own and has its own revision. */
export function EmploymentCard({ employment, canEdit, onSaved }: Props) {
  const t = useT()
  const format = useFormat()
  const [editing, setEditing] = useState<{ kind: 'employment' | 'assignment'; record: Employment } | null>(null)
  const editorRegion = useEditorFocus(editing?.kind ?? null, editing ? fieldId(editing.kind, editing.kind === 'employment' ? 'status' : 'title') : null)
  const queryClient = useQueryClient()
  const reload = async () => {
    const latest = await api.employments(employment.personId)
    queryClient.setQueryData(['employments', employment.personId], latest)
    const record = latest.find((e) => e.id === employment.id)
    if (record) setEditing((current) => current ? { ...current, record } : null)
  }
  const none = t('person.value.none')
  const assignment = employment.currentAssignment
  const headingId = `employment-${employment.id}`

  const done = () => {
    setEditing(null)
    onSaved()
  }

  return (
    <article ref={editorRegion} className="employment-record" aria-labelledby={headingId}>
      <div className="card__header">
        <h3 id={headingId}>
          {t('person.employment.legalEntity', {
            name: employment.legalEntity.name,
            country: employment.legalEntity.country,
          })}
        </h3>
        <div className="cluster employment-actions">
          <StatusTag status={employment.status} />
          {canEdit && !employment.ownedBySystem && !editing ? (
            <button type="button" data-edit="employment" className="btn btn--quiet" onClick={() => setEditing({ kind: 'employment', record: employment })} aria-label={t('person.edit.employment')}>
              {t('action.edit')}
            </button>
          ) : null}
        </div>
      </div>

      <div role="group" aria-label={t('person.section.employment')}>
        <SourceDetails employment={employment} />
        {editing?.kind === 'employment' ? (
          <EmploymentForm key={editing.record.version} employment={editing.record} onReload={reload} onCancel={() => setEditing(null)} onSaved={done} />
        ) : (
          <>
            <dl className="dl">
              <div>
                <dt>{t('field.employmentType')}</dt>
                <dd>{t(`employmentType.${employment.employmentType}`)}</dd>
              </div>
              <div>
                <dt>{t('field.country')}</dt>
                <dd>{employment.country}</dd>
              </div>
              <div>
                <dt>{t('field.startDate')}</dt>
                <dd>{format.date(employment.startDate)}</dd>
              </div>
              <div>
                <dt>{t('field.endDate')}</dt>
                <dd>{employment.endDate ? format.date(employment.endDate) : <span className="muted">{none}</span>}</dd>
              </div>
            </dl>
            <details className="record-metadata">
              <summary>{t('person.recordDetails')}</summary>
              <p>{t('person.revision', { version: employment.version })}</p>
            </details>
          </>
        )}
      </div>

      <hr className="divider" />

      <div role="group" aria-labelledby={`${headingId}-assignment`}>
        <div className="card__header" style={{ marginBottom: 'var(--space-3)' }}>
          <h4 id={`${headingId}-assignment`} style={{ margin: 0 }}>
            {t('person.section.assignment')}
          </h4>
          {canEdit && assignment && !assignment.ownedBySystem && !editing ? (
            <button type="button" data-edit="assignment" className="btn btn--quiet" onClick={() => setEditing({ kind: 'assignment', record: employment })} aria-label={t('person.edit.assignment')}>
              {t('action.edit')}
            </button>
          ) : null}
        </div>
        <SourceDetails employment={employment} assignment />
        {!assignment ? (
          <p className="muted">{t('person.assignment.none')}</p>
        ) : editing?.kind === 'assignment' ? (
          <AssignmentForm
            key={editing.record.currentAssignment?.version}
            employment={editing.record}
            onReload={reload}
            onCancel={() => setEditing(null)}
            onSaved={done}
          />
        ) : (
          <>
            <dl className="dl">
              <div>
                <dt>{t('field.title')}</dt>
                <dd>{assignment.title}</dd>
              </div>
              <div>
                <dt>{t('field.orgUnit')}</dt>
                <dd>{assignment.orgUnit.name}</dd>
              </div>
              <div>
                <dt>{t('field.manager')}</dt>
                <dd>
                  {assignment.manager ? (
                    <Link to={`/people/${assignment.manager.personId}`}>{assignment.manager.displayName}</Link>
                  ) : (
                    <span className="muted">{t('field.manager.none')}</span>
                  )}
                </dd>
              </div>
            </dl>
            <p className="small muted record-effective-date">
              {t('person.assignment.effectiveFrom', { date: format.date(assignment.effectiveFrom) })}
            </p>
            <details className="record-metadata">
              <summary>{t('person.recordDetails')}</summary>
              <p>{t('person.revision', { version: assignment.version })}</p>
            </details>
          </>
        )}
      </div>
    </article>
  )
}

const STATUSES = ['PENDING', 'ACTIVE', 'ENDED'] as const
const TYPES = ['FULL_TIME', 'PART_TIME'] as const

function EmploymentForm({ employment, onSaved, onCancel, onReload }: { employment: Employment; onSaved: () => void; onCancel: () => void; onReload: () => Promise<void> }) {
  const t = useT()
  const [values, setValues] = useState({
    status: employment.status as string,
    employmentType: employment.employmentType as string,
    country: employment.country,
    startDate: employment.startDate,
    endDate: employment.endDate ?? '',
    reason: '',
  })
  const form = useSaveForm({ save: (payload, key) => api.updateEmployment(employment.id, payload, key), onSaved })
  const set = (name: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [name]: event.target.value }))
  const id = (name: string) => fieldId('employment', name)
  const err = form.fieldErrors
  const labels = {
    status: t('field.status'),
    employmentType: t('field.employmentType'),
    country: t('field.country'),
    startDate: t('field.startDate'),
    endDate: t('field.endDate'),
    reason: t('field.reason'),
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        form.submit({
          expectedVersion: employment.version,
          status: values.status,
          employmentType: values.employmentType,
          country: values.country.trim().toUpperCase(),
          startDate: values.startDate,
          endDate: values.endDate || null,
          ...(values.reason.trim() ? { reason: values.reason } : {}),
        })
      }}
    >
      <div className="stack stack--tight">
        <FormFeedback
          failure={form.failure}
          idPrefix="employment"
          labels={labels}
          onReload={onReload}
        />
        <div className="form-grid">
          <FormField id={id('status')} label={labels.status} error={err.status}>
            {(a) => (
              <select {...a} className="select" required value={values.status} onChange={set('status')}>
                {STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {t(`status.${s}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField id={id('employmentType')} label={labels.employmentType} hint={t('field.employmentType.hint')} error={err.employmentType}>
            {(a) => (
              <select {...a} className="select" required value={values.employmentType} onChange={set('employmentType')}>
                {TYPES.map((s) => (
                  <option key={s} value={s}>
                    {t(`employmentType.${s}`)}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField id={id('country')} label={labels.country} hint={t('field.country.hint')} error={err.country}>
            {(a) => <input {...a} className="input" required maxLength={2} value={values.country} onChange={set('country')} />}
          </FormField>
          <FormField id={id('startDate')} label={labels.startDate} error={err.startDate}>
            {(a) => <input {...a} className="input" type="date" required value={values.startDate} onChange={set('startDate')} />}
          </FormField>
          <FormField id={id('endDate')} label={labels.endDate} optional error={err.endDate}>
            {(a) => <input {...a} className="input" type="date" value={values.endDate} onChange={set('endDate')} />}
          </FormField>
          <FormField id={id('reason')} label={labels.reason} hint={t('field.reason.hint')} optional wide error={err.reason}>
            {(a) => <textarea {...a} className="textarea" value={values.reason} onChange={set('reason')} />}
          </FormField>
        </div>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn--primary" disabled={form.saving} aria-busy={form.saving}>
          {form.saving ? t('action.saving') : t('action.save')}
        </button>
        <button type="button" className="btn" onClick={onCancel} disabled={form.saving}>
          {t('action.cancel')}
        </button>
      </div>
    </form>
  )
}

function AssignmentForm({ employment, onSaved, onCancel, onReload }: { employment: Employment; onSaved: () => void; onCancel: () => void; onReload: () => Promise<void> }) {
  const t = useT()
  const assignment = employment.currentAssignment
  const orgUnits = useQuery({ queryKey: ['org-units'], queryFn: ({ signal }) => api.orgUnits(signal), staleTime: 60_000 })

  const [values, setValues] = useState({
    title: assignment?.title ?? '',
    orgUnitId: assignment?.orgUnit.id ?? '',
    managerEmploymentId: assignment?.manager?.employmentId ?? '',
    reason: '',
  })
  const form = useSaveForm({
    save: (payload, key) => api.updateAssignment(assignment?.id ?? '', payload, key),
    onSaved,
  })
  const set = (name: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [name]: event.target.value }))
  const id = (name: string) => fieldId('assignment', name)
  const err = form.fieldErrors
  const labels = {
    title: t('field.title'),
    orgUnitId: t('field.orgUnit'),
    managerEmploymentId: t('field.manager'),
    reason: t('field.reason'),
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        if (!assignment) return
        form.submit({
          expectedVersion: assignment.version,
          title: values.title,
          orgUnitId: values.orgUnitId || null,
          managerEmploymentId: values.managerEmploymentId || null,
          ...(values.reason.trim() ? { reason: values.reason } : {}),
        })
      }}
    >
      <div className="stack stack--tight">
        <FormFeedback
          failure={form.failure}
          idPrefix="assignment"
          labels={labels}
          onReload={onReload}
        />
        <div className="form-grid">
          <FormField id={id('title')} label={labels.title} wide error={err.title}>
            {(a) => <input {...a} className="input" required value={values.title} onChange={set('title')} />}
          </FormField>
          <FormField id={id('orgUnitId')} label={labels.orgUnitId} error={err.orgUnitId}>
            {(a) => (
              <select {...a} className="select" required value={values.orgUnitId} onChange={set('orgUnitId')}>
                {!values.orgUnitId ? <option value="">{t('field.select')}</option> : null}
                {(orgUnits.data ?? []).map((unit) => (
                  <option key={unit.id} value={unit.id}>
                    {unit.path}
                  </option>
                ))}
              </select>
            )}
          </FormField>
          <FormField id={id('managerEmploymentId')} label={labels.managerEmploymentId} optional error={err.managerEmploymentId}>
                  {(a) => <ManagerPicker control={a} value={values.managerEmploymentId}
                    onChange={(managerEmploymentId) => setValues((current) => ({ ...current, managerEmploymentId }))} selectedName={assignment?.manager?.displayName} excludingEmploymentId={employment.id} />}
                </FormField>
          <FormField id={id('reason')} label={labels.reason} hint={t('field.reason.hint')} optional wide error={err.reason}>
            {(a) => <textarea {...a} className="textarea" value={values.reason} onChange={set('reason')} />}
          </FormField>
        </div>
      </div>
      <div className="form-actions">
        <button type="submit" className="btn btn--primary" disabled={form.saving} aria-busy={form.saving}>
          {form.saving ? t('action.saving') : t('action.save')}
        </button>
        <button type="button" className="btn" onClick={onCancel} disabled={form.saving}>
          {t('action.cancel')}
        </button>
      </div>
    </form>
  )
}
