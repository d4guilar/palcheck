import { ManagerPicker } from '../components/ManagerPicker'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/endpoints'
import type { Person } from '../api/types'
import { FormField } from '../components/FormField'
import { PageTitle } from '../components/PageTitle'
import { FormFeedback } from '../forms/FormFeedback'
import { fieldId } from '../forms/failure'
import { useSaveForm } from '../forms/useSaveForm'
import { useT } from '../i18n/useT'
import { useSession } from '../session/sessionContext'

const LOCALES = ['en', 'es', 'es-CR'] as const
const STATUSES = ['PENDING', 'ACTIVE'] as const
const TYPES = ['FULL_TIME', 'PART_TIME'] as const

/** Creates a person together with their first employment and current assignment in one atomic save. */
export function NewPersonPage() {
  const t = useT()
  const session = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const entities = useQuery({ queryKey: ['legal-entities'], queryFn: ({ signal }) => api.legalEntities(signal), staleTime: 60_000 })
  const orgUnits = useQuery({ queryKey: ['org-units'], queryFn: ({ signal }) => api.orgUnits(signal), staleTime: 60_000 })


  const [values, setValues] = useState({
    firstName: '',
    middleName: '',
    lastName: '',
    preferredName: '',
    workEmail: '',
    locale: 'en',
    legalEntityId: '',
    status: 'ACTIVE',
    employmentType: 'FULL_TIME',
    country: '',
    startDate: '',
    endDate: '',
    title: '',
    orgUnitId: '',
    managerEmploymentId: '',
    reason: '',
  })
  const set = (name: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [name]: event.target.value }))

  // A ref, not a local: the save and success callbacks may belong to different renders.
  const createdId = useRef<string | null>(null)
  const form = useSaveForm<unknown>({
    save: async (payload, key) => {
      const person: Person = await api.createPerson(payload, key)
      createdId.current = person.id
    },
    onSaved: () => {
      void queryClient.invalidateQueries({ queryKey: ['people'] })
      if (createdId.current) navigate(`/people/${createdId.current}`, { state: { created: true } })
    },
  })
  const err = form.fieldErrors
  const id = (name: string) => fieldId('new', name)

  const labels: Record<string, string> = {
    'person.firstName': t('field.firstName'),
    'person.middleName': t('field.middleName'),
    'person.lastName': t('field.lastName'),
    'person.preferredName': t('field.preferredName'),
    'person.workEmail': t('field.workEmail'),
    'person.locale': t('field.locale'),
    'employment.legalEntityId': t('field.legalEntity'),
    'employment.status': t('field.status'),
    'employment.employmentType': t('field.employmentType'),
    'employment.country': t('field.country'),
    'employment.startDate': t('field.startDate'),
    'employment.endDate': t('field.endDate'),
    'assignment.title': t('field.title'),
    'assignment.orgUnitId': t('field.orgUnit'),
    'assignment.managerEmploymentId': t('field.manager'),
    reason: t('field.reason'),
  }

  const chooseEntity = (event: { target: { value: string } }) => {
    const entity = entities.data?.find((e) => e.id === event.target.value)
    setValues((current) => ({
      ...current,
      legalEntityId: event.target.value,
      // Suggest the entity's country until the person has entered one; it stays editable.
      country: current.country || entity?.country || '',
    }))
  }


  return (
    <div className="container--narrow" style={{ margin: '0 auto' }}>
      <Link className="back-link" to="/people">
        ← {t('person.back')}
      </Link>
      <div className="page-header">
        <div>
          <PageTitle>{t('newPerson.title')}</PageTitle>
          <p className="page-header__sub">{t('newPerson.subtitle')}</p>
        </div>
      </div>

      {!session.can('CREATE_PERSON') || !session.can('EDIT_EMPLOYMENT') ? (
        <p role="alert" className="notice notice--error">
          {t('form.errors.forbidden')}
        </p>
      ) : (
        <form
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            form.submit({
              person: {
                firstName: values.firstName,
                ...(values.middleName.trim() ? { middleName: values.middleName } : {}),
                lastName: values.lastName,
                ...(values.preferredName.trim() ? { preferredName: values.preferredName } : {}),
                workEmail: values.workEmail,
                locale: values.locale,
              },
              employment: {
                legalEntityId: values.legalEntityId || null,
                status: values.status,
                employmentType: values.employmentType,
                country: values.country.trim().toUpperCase(),
                startDate: values.startDate || null,
                ...(values.endDate ? { endDate: values.endDate } : {}),
              },
              assignment: {
                title: values.title,
                orgUnitId: values.orgUnitId || null,
                ...(values.managerEmploymentId ? { managerEmploymentId: values.managerEmploymentId } : {}),
              },
              ...(values.reason.trim() ? { reason: values.reason } : {}),
            })
          }}
        >
          <div className="stack">
            <FormFeedback failure={form.failure} idPrefix="new" labels={labels} />

            <fieldset className="form-section">
              <legend>{t('newPerson.section.identity')}</legend>
              <div className="form-grid">
                <FormField id={id('person.firstName')} label={t('field.firstName')} error={err['person.firstName']}>
                  {(a) => <input {...a} className="input" required autoComplete="given-name" value={values.firstName} onChange={set('firstName')} />}
                </FormField>
                <FormField id={id('person.middleName')} label={t('field.middleName')} optional error={err['person.middleName']}>
                  {(a) => <input {...a} className="input" value={values.middleName} onChange={set('middleName')} />}
                </FormField>
                <FormField id={id('person.lastName')} label={t('field.lastName')} hint={t('field.lastName.hint')} error={err['person.lastName']}>
                  {(a) => <input {...a} className="input" required autoComplete="family-name" value={values.lastName} onChange={set('lastName')} />}
                </FormField>
                <FormField id={id('person.preferredName')} label={t('field.preferredName')} optional error={err['person.preferredName']}>
                  {(a) => <input {...a} className="input" value={values.preferredName} onChange={set('preferredName')} />}
                </FormField>
                <FormField id={id('person.workEmail')} label={t('field.workEmail')} error={err['person.workEmail']}>
                  {(a) => <input {...a} className="input" type="email" required autoComplete="off" value={values.workEmail} onChange={set('workEmail')} />}
                </FormField>
                <FormField id={id('person.locale')} label={t('field.locale')} error={err['person.locale']}>
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
              </div>
            </fieldset>

            <fieldset className="form-section">
              <legend>{t('newPerson.section.employment')}</legend>
              <div className="form-grid">
                <FormField id={id('employment.legalEntityId')} label={t('field.legalEntity')} error={err['employment.legalEntityId']}>
                  {(a) => (
                    <select {...a} className="select" required value={values.legalEntityId} onChange={chooseEntity}>
                      <option value="">{t('field.select')}</option>
                      {(entities.data ?? []).map((entity) => (
                        <option key={entity.id} value={entity.id}>
                          {t('person.employment.legalEntity', { name: entity.name, country: entity.country })}
                        </option>
                      ))}
                    </select>
                  )}
                </FormField>
                <FormField id={id('employment.status')} label={t('field.status')} error={err['employment.status']}>
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
                <FormField id={id('employment.employmentType')} label={t('field.employmentType')} hint={t('field.employmentType.hint')} error={err['employment.employmentType']}>
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
                <FormField id={id('employment.country')} label={t('field.country')} hint={t('field.country.hint')} error={err['employment.country']}>
                  {(a) => <input {...a} className="input" required maxLength={2} value={values.country} onChange={set('country')} />}
                </FormField>
                <FormField id={id('employment.startDate')} label={t('field.startDate')} error={err['employment.startDate']}>
                  {(a) => <input {...a} className="input" type="date" required value={values.startDate} onChange={set('startDate')} />}
                </FormField>
                <FormField id={id('employment.endDate')} label={t('field.endDate')} optional error={err['employment.endDate']}>
                  {(a) => <input {...a} className="input" type="date" value={values.endDate} onChange={set('endDate')} />}
                </FormField>
              </div>
            </fieldset>

            <fieldset className="form-section">
              <legend>{t('newPerson.section.assignment')}</legend>
              <div className="form-grid">
                <FormField id={id('assignment.title')} label={t('field.title')} wide error={err['assignment.title']}>
                  {(a) => <input {...a} className="input" required value={values.title} onChange={set('title')} />}
                </FormField>
                <FormField id={id('assignment.orgUnitId')} label={t('field.orgUnit')} error={err['assignment.orgUnitId']}>
                  {(a) => (
                    <select {...a} className="select" required value={values.orgUnitId} onChange={set('orgUnitId')}>
                      <option value="">{t('field.select')}</option>
                      {(orgUnits.data ?? []).map((unit) => (
                        <option key={unit.id} value={unit.id}>
                          {unit.path}
                        </option>
                      ))}
                    </select>
                  )}
                </FormField>
                <FormField id={id('assignment.managerEmploymentId')} label={t('field.manager')} optional error={err['assignment.managerEmploymentId']}>
                  {(a) => <ManagerPicker control={a} value={values.managerEmploymentId}
                    onChange={(managerEmploymentId) => setValues((current) => ({ ...current, managerEmploymentId }))}  />}
                </FormField>
              </div>
            </fieldset>

            <div className="field">
              <label className="field__label" htmlFor={id('reason')}>
                {t('field.reason')} <span className="field__optional">({t('field.optional')})</span>
              </label>
              <span className="field__hint" id={id('reason-hint')}>
                {t('field.reason.hint')}
              </span>
              <textarea
                id={id('reason')}
                className="textarea"
                aria-describedby={id('reason-hint')}
                value={values.reason}
                onChange={set('reason')}
              />
            </div>

            <div className="form-actions" style={{ marginTop: 0 }}>
              <button type="submit" className="btn btn--primary" disabled={form.saving} aria-busy={form.saving}>
                {form.saving ? t('action.creating') : t('action.create')}
              </button>
              <Link className="btn" to="/people">
                {t('action.cancel')}
              </Link>
            </div>
          </div>
        </form>
      )}
    </div>
  )
}
