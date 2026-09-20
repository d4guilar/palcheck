import { useIntl } from 'react-intl'
import type { MessageId } from './en'

export type Translate = (id: MessageId, values?: Record<string, string | number | Date>) => string

/** Typed message lookup: an unknown message id is a compile error, not a runtime surprise. */
export function useT(): Translate {
  const intl = useIntl()
  return (id, values) => intl.formatMessage({ id }, values)
}
