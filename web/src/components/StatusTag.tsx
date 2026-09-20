import type { EmploymentStatus } from '../api/types'
import { useT } from '../i18n/useT'

const VARIANT: Record<EmploymentStatus, string> = {
  ACTIVE: 'tag tag--success',
  PENDING: 'tag tag--warning',
  ENDED: 'tag',
}

/** Employment status as text; the color is a redundant cue, never the only one. */
export function StatusTag({ status }: { status: EmploymentStatus | null }) {
  const t = useT()
  if (!status) return <span className="muted">{t('status.none')}</span>
  return <span className={VARIANT[status]}>{t(`status.${status}`)}</span>
}
