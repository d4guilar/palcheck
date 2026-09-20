import { useIntl } from 'react-intl'

/** Parses "YYYY-MM-DD" as a calendar date with no time zone, so it never shifts a day. */
function parseLocalDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number) as [number, number, number]
  return new Date(Date.UTC(year, month - 1, day))
}

/** Locale-aware formatting for business dates and instants. The locale controls presentation only. */
export function useFormat() {
  const intl = useIntl()
  return {
    date: (iso: string) => intl.formatDate(parseLocalDate(iso), { dateStyle: 'medium', timeZone: 'UTC' }),
    dateTime: (iso: string) => intl.formatDate(new Date(iso), { dateStyle: 'medium', timeStyle: 'short' }),
  }
}
