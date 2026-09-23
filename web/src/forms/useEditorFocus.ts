import { useEffect, useRef } from 'react'

/** Keep keyboard position when an inline editor replaces its Edit button, and restore it on close. */
export function useEditorFocus(editor: string | null, firstControlId: string | null) {
  const region = useRef<HTMLElement>(null)
  const previous = useRef<string | null>(null)
  useEffect(() => {
    if (editor && previous.current !== editor && firstControlId) {
      region.current?.querySelector<HTMLElement>(`[id="${firstControlId}"]`)?.focus()
    } else if (!editor && previous.current) {
      region.current?.querySelector<HTMLButtonElement>(`[data-edit="${previous.current}"]`)?.focus()
    }
    previous.current = editor
  }, [editor, firstControlId])
  return region
}
