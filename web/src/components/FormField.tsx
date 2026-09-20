import type { ReactNode } from 'react'
import { useT } from '../i18n/useT'

export interface ControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
}

interface FormFieldProps {
  id: string
  label: string
  hint?: string
  error?: string
  optional?: boolean
  wide?: boolean
  children: (control: ControlProps) => ReactNode
}

/**
 * A labelled control with an optional hint and an inline error. The label is a real <label>, the hint and error are
 * tied to the control with aria-describedby, and an invalid control is marked with aria-invalid.
 */
export function FormField({ id, label, hint, error, optional, wide, children }: FormFieldProps) {
  const t = useT()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className={wide ? 'field field--wide' : 'field'}>
      <label className="field__label" htmlFor={id}>
        {label}
        {optional ? <span className="field__optional"> ({t('field.optional')})</span> : null}
      </label>
      {children({ id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })}
      {hint ? (
        <span className="field__hint" id={hintId}>
          {hint}
        </span>
      ) : null}
      {error ? (
        <span className="field__error" id={errorId}>
          {error}
        </span>
      ) : null}
    </div>
  )
}
