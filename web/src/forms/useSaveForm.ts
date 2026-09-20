import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { classify, errorsByField, type Failure } from './failure'
import { useSubmissionKey } from './useSubmissionKey'

interface Options<TPayload> {
  save: (payload: TPayload, idempotencyKey: string) => Promise<unknown>
  onSaved: () => void
}

/**
 * The shared behavior of every save form: one submission at a time (no accidental double submit), an idempotency key
 * that survives a retry of the same content, and the failure classified for display.
 */
export function useSaveForm<TPayload>({ save, onSaved }: Options<TPayload>) {
  const [failure, setFailure] = useState<Failure | null>(null)
  const submission = useSubmissionKey()
  const mutation = useMutation({
    mutationFn: (payload: TPayload) => save(payload, submission.keyFor(payload)),
    onSuccess: () => {
      submission.reset()
      setFailure(null)
      onSaved()
    },
    onError: (error) => setFailure(classify(error)),
  })

  return {
    failure,
    saving: mutation.isPending,
    fieldErrors: errorsByField(failure),
    submit: (payload: TPayload) => {
      if (mutation.isPending) return
      setFailure(null)
      mutation.mutate(payload)
    },
  }
}
