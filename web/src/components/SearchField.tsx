import type { InputHTMLAttributes } from 'react'

/** Native search semantics and a real label; the small icon is only a visual cue. */
export function SearchField({ id, label, hint, ...input }: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  id: string; label: string; hint?: string
}) {
  return (
    <div className="search-field">
      <label className="visually-hidden" htmlFor={id}>{label}</label>
      <svg className="search-field__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
        <circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4 4" />
      </svg>
      <input {...input} id={id} className="input" type="search" aria-describedby={hint ? `${id}-hint` : input['aria-describedby']} />
      {hint ? <span className="visually-hidden" id={`${id}-hint`}>{hint}</span> : null}
    </div>
  )
}
