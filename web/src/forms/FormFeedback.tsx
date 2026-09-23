import { useState } from 'react'
import { Notice } from '../components/Notice'
import { useT, type Translate } from '../i18n/useT'
import { fieldId, type Failure } from './failure'

interface FormFeedbackProps {
  failure: Failure | null
  /** Prefix that maps server field names to control ids. */
  idPrefix: string
  /** Human labels for server field names, used in the error summary. */
  labels: Record<string, string>
  onReload?: () => void | Promise<void>
}

/**
 * The summary of a failed save. Validation problems are listed with links to their controls; a concurrent change gets
 * the explanation and the action to load the latest version; other failures say what happened and what to do.
 * The element receives focus when it appears, so keyboard and screen-reader users land on it.
 */
export function FormFeedback({ failure, idPrefix, labels, onReload }: FormFeedbackProps) {
  const t = useT()
  const [reloadError, setReloadError] = useState(false)
  const [reloading, setReloading] = useState(false)
  if (!failure) return null

  if (failure.kind === 'conflict') {
    return (
      <Notice
        ref={focusOnMount}
        variant="warning"
        role="alert"
        title={t('conflict.title')}
        actions={
          onReload ? (
            <button type="button" className="btn" disabled={reloading} onClick={async () => { setReloading(true); setReloadError(false); try { await onReload() } catch { setReloadError(true) } finally { setReloading(false) } }}>
              {t('action.reload')}
            </button>
          ) : undefined
        }
      >
        <p>{t('conflict.body')}</p>
        {reloadError ? <p role="alert">{t('conflict.reloadError')}</p> : null}
      </Notice>
    )
  }

  if (failure.kind === 'validation') {
    return (
      <Notice ref={focusOnMount} variant="error" role="alert" title={t('form.errors.title', { count: failure.fieldErrors.length })}>
        <ul>
          {failure.fieldErrors.map((e) => (
            <li key={`${e.field}:${e.code}`}>
              <ErrorLink id={fieldId(idPrefix, e.field)} label={labels[e.field] ?? e.field} message={e.message} />
            </li>
          ))}
        </ul>
      </Notice>
    )
  }

  return (
    <Notice ref={focusOnMount} variant="error" role="alert">
      <p>{message(failure, t)}</p>
    </Notice>
  )
}

// A failed save mounts a fresh summary (submitting clears the previous failure), so focusing on mount lands keyboard and
// screen-reader users on each new failure.
function focusOnMount(node: HTMLDivElement | null) {
  node?.focus()
}

function message(failure: Exclude<Failure, { kind: 'validation' | 'conflict' }>, t: Translate): string {
  switch (failure.kind) {
    case 'externally-owned':
      return t('source.editRefused')
    case 'forbidden':
      return t('form.errors.forbidden')
    case 'network':
      return t('form.errors.network')
    default:
      return t('form.errors.generic')
  }
}

function ErrorLink({ id, label, message }: { id: string; label: string; message: string }) {
  return (
    <a
      href={`#${id}`}
      onClick={(event) => {
        const target = document.getElementById(id)
        if (target) {
          event.preventDefault()
          target.focus()
        }
      }}
    >
      {label}: {message}
    </a>
  )
}
