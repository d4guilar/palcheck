/** A neutral visual anchor for scanning names; the adjacent name provides the accessible text. */
export function Initials({ name, profile = false }: { name: string; profile?: boolean }) {
  const parts = name.trim().split(/\s+/u)
  const initials = [parts[0], parts.length > 1 ? parts[1] : undefined].filter(Boolean).map((p) => Array.from(p!)[0]).join('')
  return <span className={`avatar${profile ? ' avatar--profile' : ''}`} aria-hidden="true">{initials}</span>
}
