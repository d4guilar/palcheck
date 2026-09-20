import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/endpoints'
import type { Person } from '../../api/types'
import { FormField } from '../../components/FormField'
import { FormFeedback } from '../../forms/FormFeedback'
import { fieldId } from '../../forms/failure'
import { useSaveForm } from '../../forms/useSaveForm'
import { useT } from '../../i18n/useT'

const LOCALES = ['en', 'es', 'es-CR'] as const

/** The identity fields of a person, viewable by everyone who can see the record and editable by those who may. */
export function IdentityCard({ person, canEdit, onSaved }: { person: Person; canEdit: boolean; onSaved: () => void }) {
  const t = useT()
  const [editing, setEditing] = useState<Person | null>(null)
  const queryClient = useQueryClient()
  const none = t('person.value.none')

  return (
    <section className="card" aria-labelledby="identity-heading">
      <div className="card__header">
        <h2 id="identity-heading">{t('person.section.identity')}</h2>
        {canEdit && !editing ? (
          <button type="button" className="btn btn--quiet" onClick={() => setEditing(person)} aria-label={t('person.edit.identity')}>
            {t('action.edit')}
          </button>
        ) : null}
      </div>

      {editing ? (
        // Only explicitly reviewing the latest version replaces the editing snapshot.
        <IdentityForm
          key={editing.version}
          person={editing}
          onReload={async () => { const latest = await api.person(person.id); queryClient.setQueryData(['person', person.id], latest); setEditing(latest) }}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            onSaved()
          }}
        />
      ) : (
        <>
          <dl className="dl">
            <div>
              <dt>{t('field.firstName')}</dt>
              <dd>{person.firstName}</dd>
            </div>
            <div>
              <dt>{t('field.middleName')}</dt>
              <dd>{person.middleName ?? <span className="muted">{none}</span>}</dd>
            </div>
            <div>
              <dt>{t('field.lastName')}</dt>
              <dd>{person.lastName}</dd>
            </div>
            <div>
              <dt>{t('field.preferredName')}</dt>
              <dd>{person.preferredName ?? <span className="muted">{none}</span>}</dd>
            </div>
            <div>
              <dt>{t('field.workEmail')}</dt>
              <dd>{person.workEmail}</dd>
            </div>
            <div>
              <dt>{t('field.locale')}</dt>
              <dd>{t(`locale.${person.locale as (typeof LOCALES)[number]}`)}</dd>
            </div>
          </dl>
          <p className="small muted" style={{ marginTop: 'var(--space-4)' }}>
            {t('person.revision', { version: person.version })}
          </p>
        </>
      )}
    </section>
  )
}

function IdentityForm({ person, onSaved, onCancel, onReload }: { person: Person; onSaved: () => void; onCancel: () => void; onReload: () => Promise<void> }) {
  const t = useT()
  const [values, setValues] = useState({
    firstName: person.firstName,
    middleName: person.middleName ?? '',
    lastName: person.lastName,
    preferredName: person.preferredName ?? '',
    workEmail: person.workEmail,
    locale: person.locale,
    reason: '',
  })
  const form = useSaveForm({ save: (payload, key) => api.updatePerson(person.id, payload, key), onSaved })
  const set = (name: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [name]: event.target.value }))
  const id = (name: string) => fieldId('identity', name)
  const err = form.fieldErrors

  const labels = {
    firstName: t('field.firstName'),
    middleName: t('field.middleName'),
    lastName: t('field.lastName'),
    preferredName: t('field.preferredName'),
    workEmail: t('field.workEmail'),
    locale: t('field.locale'),
    reason: t('field.reason'),
    expectedVersion: t('person.revision', { version: person.version }),
  }

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault()
        form.submit({
          expectedVersion: person.version,
          firstName: values.firstName,
          middleName: values.middleName.trim() ? values.middleName : null,
          lastName: values.lastName,
          preferredName: values.preferredName.trim() ? values.preferredName : null,
          workEmail: values.workEmail,
          locale: values.locale,
          ...(values.reason.trim() ? { reason: values.reason } : {}),
        })
      }}
    >
      <div className="stack stack--tight">
        <FormFeedback
          failure={form.failure}
          idPrefix="identity"
          labels={labels}
          onReload={onReload}
        />
        <div className="form-grid">
          <FormField id={id('firstName')} label={labels.firstName} error={err.firstName}>
            {(a) => <input {...a} className="input" required autoComplete="given-name" value={values.firstName} onChange={set('firstName')} />}
          </FormField>
          <FormField id={id('middleName')} label={labels.middleName} optional error={err.middleName}>
            {(a) => <input {...a} className="input" value={values.middleName} onChange={set('middleName')} />}
          </FormField>
          <FormField id={id('lastName')} label={labels.lastName} hint={t('field.lastName.hint')} error={err.lastName}>
            {(a) => <input {...a} className="input" required autoComplete="family-name" value={values.lastName} onChange={set('lastName')} />}
          </FormField>
          <FormField id={id('preferredName')} label={labels.preferredName} optional error={err.preferredName}>
            {(a) => <input {...a} className="input" value={values.preferredName} onChange={set('preferredName')} />}
          </FormField>
          <FormField id={id('workEmail')} label={labels.workEmail} error={err.workEmail}>
            {(a) => <input {...a} className="input" type="email" required autoComplete="email" value={values.workEmail} onChange={set('workEmail')} />}
          </FormField>
          <FormField id={id('locale')} label={labels.locale} error={err.locale}>
            {(a) => (
              <select {...a} className="select" required value={values.locale} onChange={set('locale')}>
                {LOCALES.map((locale) => (
                  <option key={locale} value={locale}>
                    {t(`locale.${locale}`)}
                  </option>
                ))}
              </select>
            )}
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
