import { useState } from 'react'

/**
 * Idempotency key management for a form. The same content keeps the same key, so a retry after a lost response is
 * recognized by the server as the same request and applied once. Changing the content, or a successful save, starts a
 * new key. The key is a random UUID; it carries no personal data.
 */
export function useSubmissionKey() {
  const [current, setCurrent] = useState<{ payload: string; key: string } | null>(null)
  return {
    keyFor(payload: unknown): string {
      const serialized = JSON.stringify(payload)
      if (current && current.payload === serialized) return current.key
      const key = crypto.randomUUID()
      setCurrent({ payload: serialized, key })
      return key
    },
    reset() {
      setCurrent(null)
    },
  }
}
