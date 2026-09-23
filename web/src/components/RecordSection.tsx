import type { ReactNode, Ref } from 'react'

/** One record section: a quiet heading rail and aligned content, stacked on smaller screens. */
export function RecordSection({ id, title, action, children, regionRef }: {
  id: string; title: string; action?: ReactNode; children: ReactNode; regionRef?: Ref<HTMLElement>
}) {
  return (
    <section ref={regionRef} className="record-section" aria-labelledby={id}>
      <div className="record-section__heading"><h2 id={id}>{title}</h2>{action}</div>
      <div className="record-section__body">{children}</div>
    </section>
  )
}
